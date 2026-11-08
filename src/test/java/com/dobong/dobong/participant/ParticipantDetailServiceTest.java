package com.dobong.dobong.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse.BookDetail;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse.LogDetail;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.dto.ProgressResponse.GoalInfo;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자별 기록 조회(F-0802)를 실제 DB에서 확인한다. 책별 값의 합이 참가자 누적 집계와 같은지 본다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class ParticipantDetailServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2027, 3, 1);
	private static final String REVIEW_500 = "가".repeat(500);

	@Autowired
	private ParticipantService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long enrollment;
	private long owned;     // 소장도서 300쪽: 1–150 승인 서평, 151–300 → 완독, 서평 1편
	private long partial;   // 소장도서 200쪽: 1–120, 100–150 (겹침) + 회차 기간 밖 151–200
	private long notOwned;  // 소장도서 아님: 집계에 안 들어감
	private long empty;     // 기록 없음

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도
		long roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2994, '2027-01-05', '2027-11-30') RETURNING id");
		long courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 1000, 2, 5) RETURNING id", roundId);
		long userId = insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version, loan_member_no) VALUES ('홍길동', '01070000201', 'x', now(), 1, 'LM-0001') RETURNING id");
		enrollment = insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id", userId, roundId, courseId);

		owned = addBook("첫째 책", 300, true);
		addLog(owned, 1, 150, IN_PERIOD, REVIEW_500, "APPROVED");
		addLog(owned, 151, 300, IN_PERIOD.plusDays(1), null, "SUBMITTED");

		partial = addBook("둘째 책", 200, true);
		addLog(partial, 1, 120, IN_PERIOD, null, "DRAFT");
		addLog(partial, 100, 150, IN_PERIOD, null, "DRAFT");
		addLog(partial, 151, 200, LocalDate.of(2026, 12, 31), null, "DRAFT"); // 회차 기간 밖

		notOwned = addBook("셋째 책", 100, false);
		addLog(notOwned, 1, 100, IN_PERIOD, REVIEW_500, "APPROVED");

		empty = addBook("넷째 책", 50, true);
	}

	@Test
	@DisplayName("회원 정보, 누적 집계, 책별 값과 기록을 돌려준다")
	void detail() {
		EnrollmentDetailResponse detail = service.getDetail(enrollment);

		assertThat(detail.enrollmentId()).isEqualTo(enrollment);
		assertThat(detail.round().year()).isEqualTo(2994);
		assertThat(detail.user().name()).isEqualTo("홍길동");
		assertThat(detail.user().phone()).isEqualTo("01070000201");
		assertThat(detail.user().loanMemberNo()).isEqualTo("LM-0001");
		assertThat(detail.course().name()).isEqualTo("시험코스");
		assertThat(detail.status()).isEqualTo(CompletionStatus.IN_PROGRESS);
		assertThat(detail.progress().pages()).isEqualTo(new GoalInfo(450, 1000, 550, 45));
		assertThat(detail.progress().reviews()).isEqualTo(new GoalInfo(1, 2, 1, 50));
		assertThat(detail.progress().books()).isEqualTo(new GoalInfo(3, 5, 2, 60)); // 소장도서 3권
		assertThat(detail.progress().completedBooks()).isEqualTo(1);

		assertThat(detail.books()).extracting(BookDetail::enrollmentBookId).containsExactly(owned, partial, notOwned, empty);

		BookDetail first = detail.books().get(0);
		assertThat(first.title()).isEqualTo("첫째 책");
		assertThat(first.readPages()).isEqualTo(300);
		assertThat(first.completed()).isTrue();
		assertThat(first.reviewCounted()).isTrue();
		assertThat(first.logs()).extracting(LogDetail::startPage).containsExactly(1, 151);
		assertThat(first.logs().get(0).review()).isEqualTo(REVIEW_500);
		assertThat(first.logs().get(0).reviewLength()).isEqualTo(500);
		assertThat(first.logs().get(1).reviewLength()).isZero();

		BookDetail second = detail.books().get(1);
		assertThat(second.readPages()).isEqualTo(150); // 1–120, 100–150 겹침은 한 번만
		assertThat(second.completed()).isFalse();
		// 읽은 날짜 순서: 기간 밖(2026-12-31) 기록이 먼저
		assertThat(second.logs()).extracting(LogDetail::counted).containsExactly(false, true, true);

		BookDetail third = detail.books().get(2);
		assertThat(third.libraryOwned()).isFalse();
		assertThat(third.readPages()).isZero();
		assertThat(third.reviewCounted()).isFalse();
		assertThat(third.logs()).extracting(LogDetail::counted).containsExactly(false);

		BookDetail fourth = detail.books().get(3);
		assertThat(fourth.readPages()).isZero();
		assertThat(fourth.logs()).isEmpty();
	}

	@Test
	@DisplayName("책별 값의 합은 참가자 누적 집계와 같다")
	void booksSumToProgress() {
		EnrollmentDetailResponse detail = service.getDetail(enrollment);

		assertThat(detail.books().stream().mapToInt(BookDetail::readPages).sum())
				.isEqualTo(detail.progress().pages().current());
		assertThat(detail.books().stream().filter(BookDetail::completed).count())
				.isEqualTo(detail.progress().completedBooks());
		assertThat(detail.books().stream().filter(BookDetail::reviewCounted).count())
				.isEqualTo(detail.progress().reviews().current());
	}

	@Test
	@DisplayName("없는 참가 신청은 404")
	void notFound() {
		assertThatThrownBy(() -> service.getDetail(Long.MAX_VALUE))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
				});
	}

	private long addBook(String title, int totalPages, boolean libraryOwned) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES (?, '저자', ?) RETURNING id", title, totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				enrollment, bookId, libraryOwned);
	}

	private void addLog(long enrollmentBookId, int start, int end, LocalDate readDate, String review, String status) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status) VALUES (?, ?, ?, ?, ?, ?)",
				enrollmentBookId, start, end, readDate, review, status);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
