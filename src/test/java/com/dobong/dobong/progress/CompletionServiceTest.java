package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.progress.dto.ProgressResponse;
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
 * 참가 신청, 코스 기준, 집계를 실제 DB에서 모아 판정하는지 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class CompletionServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2027, 3, 1);
	private static final String REVIEW_500 = "가".repeat(500);

	@Autowired
	private CompletionService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long roundId;
	private long courseId;

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2999, '2027-01-05', '2027-11-30') RETURNING id");
		courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 2, 3) RETURNING id", roundId);
	}

	@Test
	@DisplayName("본인 참가 신청의 코스 기준과 집계 값으로 진행 현황을 만든다")
	void myProgress() {
		long userId = user("01000000101");
		long enrollment = enroll(userId, "ACTIVE");
		addLog(addBook(enrollment, 300, true), 1, 300, REVIEW_500, "APPROVED");
		addLog(addBook(enrollment, 300, true), 1, 120, null, "SUBMITTED");
		addBook(enrollment, 100, false); // 소장도서 아님: 권수에 안 셈

		ProgressResponse response = service.getMyProgress(userId, enrollment);

		assertThat(response.enrollmentId()).isEqualTo(enrollment);
		assertThat(response.round().year()).isEqualTo(2999);
		assertThat(response.course().name()).isEqualTo("시험코스");
		assertThat(response.status()).isEqualTo(CompletionStatus.IN_PROGRESS);
		assertThat(response.pages()).isEqualTo(new GoalInfo(420, 500, 80, 84));
		assertThat(response.reviews()).isEqualTo(new GoalInfo(1, 2, 1, 50));
		assertThat(response.books()).isEqualTo(new GoalInfo(2, 3, 1, 66));
		assertThat(response.readKm()).isEqualTo(0.42);
		assertThat(response.completedBooks()).isEqualTo(1);
	}

	@Test
	@DisplayName("쪽수와 서평 편수를 채우면 완주 승인 대기")
	void pendingApproval() {
		long userId = user("01000000102");
		long enrollment = enroll(userId, "ACTIVE");
		addLog(addBook(enrollment, 300, true), 1, 300, REVIEW_500, "APPROVED");
		addLog(addBook(enrollment, 200, true), 1, 200, REVIEW_500, "APPROVED");

		assertThat(service.getMyProgress(userId, enrollment).status()).isEqualTo(CompletionStatus.PENDING_APPROVAL);
	}

	@Test
	@DisplayName("사서가 완주를 승인한 참가 신청은 완주")
	void completed() {
		long userId = user("01000000103");
		long enrollment = enroll(userId, "COMPLETED");

		assertThat(service.getMyProgress(userId, enrollment).status()).isEqualTo(CompletionStatus.COMPLETED);
	}

	@Test
	@DisplayName("남의 참가 신청이나 없는 참가 신청은 404")
	void notMine() {
		long owner = user("01000000104");
		long enrollment = enroll(owner, "ACTIVE");
		long other = user("01000000105");

		assertThatThrownBy(() -> service.getMyProgress(other, enrollment))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
				});
		assertThatThrownBy(() -> service.getMyProgress(owner, Long.MAX_VALUE))
				.isInstanceOf(ApiException.class);
	}

	private long user(String phone) {
		return insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES ('테스트', ?, 'x', now(), 1) RETURNING id", phone);
	}

	private long enroll(long userId, String status) {
		return insert("INSERT INTO enrollments (user_id, round_id, course_id, status) VALUES (?, ?, ?, ?) RETURNING id",
				userId, roundId, courseId, status);
	}

	private long addBook(long enrollmentId, int totalPages, boolean libraryOwned) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('책', '저자', ?) RETURNING id", totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				enrollmentId, bookId, libraryOwned);
	}

	private void addLog(long enrollmentBookId, int start, int end, String review, String status) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status) VALUES (?, ?, ?, ?, ?, ?)",
				enrollmentBookId, start, end, IN_PERIOD, review, status);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
