package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회 조건이 실제 DB에서 맞게 걸리는지 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class ReadingProgressServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2027, 3, 1);
	private static final String REVIEW_500 = "가".repeat(500);

	@Autowired
	private ReadingProgressService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long roundId;
	private long courseId;

	@BeforeEach
	void setUp() {
		// 회차 번호 901: V6(2027년 1회차)과 시연용 회차(2026년 99회차)가 이미 DB에 있어서 겹치지 않는 번호 사용
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (901, 2027, '2027-01-05', '2027-11-30') RETURNING id");
		courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '풀코스', 42195, 15, 120) RETURNING id", roundId);
	}

	@Test
	@DisplayName("쪽수는 승인 여부와 상관없이 세고, 서평은 승인된 것만 센다")
	void pagesFromAllLogsReviewsOnlyApproved() {
		long enrollment = enroll("01000000001");
		long book = addBook(enrollment, 300, true);
		addLog(book, 1, 100, IN_PERIOD, null, "DRAFT");
		addLog(book, 101, 200, IN_PERIOD, REVIEW_500, "SUBMITTED");
		addLog(book, 201, 300, IN_PERIOD, null, "APPROVED");

		assertThat(service.getProgress(enrollment)).isEqualTo(new ReadingProgress(enrollment, 300, 1, 0));
	}

	@Test
	@DisplayName("소장도서 아님, 회차 기간 밖, 끝 쪽이 전체 쪽수보다 큰 기록은 집계하지 않는다")
	void excludesNonTargetLogs() {
		long enrollment = enroll("01000000002");
		long notOwned = addBook(enrollment, 300, false);
		addLog(notOwned, 1, 300, IN_PERIOD, REVIEW_500, "APPROVED");

		long owned = addBook(enrollment, 300, true);
		addLog(owned, 1, 50, LocalDate.of(2026, 12, 31), null, "APPROVED");
		addLog(owned, 51, 350, IN_PERIOD, null, "APPROVED");
		addLog(owned, 1, 10, IN_PERIOD, null, "APPROVED");

		assertThat(service.getProgress(enrollment)).isEqualTo(new ReadingProgress(enrollment, 10, 0, 0));
	}

	@Test
	@DisplayName("승인된 서평은 500자 이상부터 1편으로 센다")
	void reviewNeeds500Characters() {
		long enrollment = enroll("01000000003");
		long short499 = addBook(enrollment, 100, true);
		addLog(short499, 1, 10, IN_PERIOD, "가".repeat(499), "APPROVED");
		long exactly500 = addBook(enrollment, 100, true);
		addLog(exactly500, 1, 10, IN_PERIOD, REVIEW_500, "APPROVED");

		assertThat(service.getProgress(enrollment).reviewCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("기록이 없는 참가자는 모두 0이다")
	void noLogs() {
		long enrollment = enroll("01000000004");

		assertThat(service.getProgress(enrollment)).isEqualTo(ReadingProgress.empty(enrollment));
	}

	@Test
	@DisplayName("회차 전체 조회는 참가자별 결과를 돌려준다")
	void byRound() {
		long first = enroll("01000000005");
		addLog(addBook(first, 300, true), 1, 300, IN_PERIOD, REVIEW_500, "APPROVED");
		long second = enroll("01000000006");
		addLog(addBook(second, 200, true), 1, 50, IN_PERIOD, null, "SUBMITTED");

		Map<Long, ReadingProgress> result = service.getProgressByRound(roundId);

		assertThat(result).containsOnlyKeys(first, second);
		assertThat(result.get(first)).isEqualTo(new ReadingProgress(first, 300, 1, 1));
		assertThat(result.get(second)).isEqualTo(new ReadingProgress(second, 50, 0, 0));
	}

	private long enroll(String phone) {
		long userId = insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at) VALUES ('테스트', ?, 'x', now()) RETURNING id", phone);
		return insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id", userId, roundId, courseId);
	}

	private long addBook(long enrollmentId, int totalPages, boolean libraryOwned) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('책', '저자', ?) RETURNING id", totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				enrollmentId, bookId, libraryOwned);
	}

	private void addLog(long enrollmentBookId, int start, int end, LocalDate readDate, String review, String status) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status) VALUES (?, ?, ?, ?, ?, ?)",
				enrollmentBookId, start, end, readDate, review, status);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
