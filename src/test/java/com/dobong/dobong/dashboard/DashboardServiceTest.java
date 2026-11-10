package com.dobong.dobong.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.dashboard.dto.RecentLogsResponse;
import com.dobong.dobong.dashboard.dto.RecentLogsResponse.RecentLog;
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
 * 최근 기록의 순서, 개수, 본인 확인이 실제 DB에서 맞게 동작하는지 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class DashboardServiceTest {

	@Autowired
	private DashboardService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long roundId;
	private long courseId;

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도. 운영 기간은 2027-01-05 ~ 2027-11-30
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2994, '2027-01-05', '2027-11-30') RETURNING id");
		courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 2, 3) RETURNING id", roundId);
	}

	@Test
	@DisplayName("읽은 날짜가 늦은 순서로, 같은 날이면 나중에 쓴 기록부터 돌려준다")
	void newestFirst() {
		long userId = user("01000000301");
		long enrollment = enroll(userId);
		long book = addBook(enrollment, "어린 왕자", 120, true);
		long first = addLog(book, 1, 30, "2027-03-01", null, null);
		long second = addLog(book, 31, 60, "2027-03-05", null, null);
		long third = addLog(book, 61, 90, "2027-03-05", null, null);

		RecentLogsResponse response = service.getRecentLogs(userId, enrollment, 5);

		assertThat(response.enrollmentId()).isEqualTo(enrollment);
		assertThat(response.logs()).extracting(RecentLog::readingLogId).containsExactly(third, second, first);
	}

	@Test
	@DisplayName("size만큼만 돌려준다")
	void limitsSize() {
		long userId = user("01000000302");
		long enrollment = enroll(userId);
		long book = addBook(enrollment, "데미안", 248, true);
		for (int day = 1; day <= 7; day++) {
			addLog(book, day * 10, day * 10 + 5, "2027-04-0" + day, null, null);
		}

		assertThat(service.getRecentLogs(userId, enrollment, 3).logs()).hasSize(3);
		assertThat(service.getRecentLogs(userId, enrollment, 20).logs()).hasSize(7);
	}

	@Test
	@DisplayName("도서명, 글귀, 서평 글자 수, 집계 포함 여부를 함께 돌려준다")
	void fields() {
		long userId = user("01000000303");
		long enrollment = enroll(userId);
		long owned = addBook(enrollment, "코스모스", 719, true);
		long notOwned = addBook(enrollment, "아몬드", 264, false);
		addLog(owned, 1, 50, "2027-05-01", "별은 우리 안에 있다", "가".repeat(512));
		addLog(notOwned, 1, 30, "2027-05-02", null, null);

		RecentLogsResponse response = service.getRecentLogs(userId, enrollment, 5);

		RecentLog latest = response.logs().get(0);
		assertThat(latest.bookTitle()).isEqualTo("아몬드");
		assertThat(latest.counted()).isFalse();
		assertThat(latest.quote()).isNull();
		assertThat(latest.reviewLength()).isZero();

		RecentLog older = response.logs().get(1);
		assertThat(older.bookTitle()).isEqualTo("코스모스");
		assertThat(older.readDate()).isEqualTo(LocalDate.of(2027, 5, 1));
		assertThat(older.startPage()).isEqualTo(1);
		assertThat(older.endPage()).isEqualTo(50);
		assertThat(older.status()).isEqualTo("DRAFT");
		assertThat(older.quote()).isEqualTo("별은 우리 안에 있다");
		assertThat(older.reviewLength()).isEqualTo(512);
		assertThat(older.counted()).isTrue();
	}

	@Test
	@DisplayName("기록이 없으면 빈 목록이다")
	void noLogs() {
		long userId = user("01000000304");
		long enrollment = enroll(userId);

		assertThat(service.getRecentLogs(userId, enrollment, 5).logs()).isEmpty();
	}

	@Test
	@DisplayName("남의 참가 신청이나 없는 참가 신청은 404")
	void notMine() {
		long owner = user("01000000305");
		long enrollment = enroll(owner);
		long other = user("01000000306");

		for (long[] userAndEnrollment : new long[][] {{other, enrollment}, {owner, Long.MAX_VALUE}}) {
			assertThatThrownBy(() -> service.getRecentLogs(userAndEnrollment[0], userAndEnrollment[1], 5))
					.isInstanceOfSatisfying(ApiException.class, e -> {
						assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
						assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
					});
		}
	}

	@Test
	@DisplayName("size가 1~20 밖이면 400")
	void invalidSize() {
		long userId = user("01000000307");
		long enrollment = enroll(userId);

		for (int size : new int[] {0, -1, 21}) {
			assertThatThrownBy(() -> service.getRecentLogs(userId, enrollment, size))
					.isInstanceOfSatisfying(ApiException.class, e -> {
						assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(e.getCode()).isEqualTo("INVALID_INPUT");
					});
		}
	}

	private long user(String phone) {
		return insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES ('테스트', ?, 'x', now(), 1) RETURNING id", phone);
	}

	private long enroll(long userId) {
		return insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id",
				userId, roundId, courseId);
	}

	private long addBook(long enrollmentId, String title, int totalPages, boolean libraryOwned) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES (?, '저자', ?) RETURNING id", title, totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				enrollmentId, bookId, libraryOwned);
	}

	private long addLog(long enrollmentBookId, int start, int end, String readDate, String quote, String review) {
		return insert("""
				INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, quote, review)
				VALUES (?, ?, ?, CAST(? AS date), ?, ?) RETURNING id""",
				enrollmentBookId, start, end, readDate, quote, review);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
