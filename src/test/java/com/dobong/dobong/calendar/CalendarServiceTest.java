package com.dobong.dobong.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.calendar.dto.CalendarResponse;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.progress.ReadingProgressService;
import com.dobong.dobong.round.RoundService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참가 신청 찾기, 월 범위, 집계 포함 조건이 실제 DB에서 맞게 걸리는지 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class CalendarServiceTest {

	@Autowired
	private CalendarService service;

	@Autowired
	private ReadingProgressService readingProgressService;

	@Autowired
	private RoundService roundService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	private long roundId;
	private long courseId;

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도. 운영 기간은 2027-01-05 ~ 2027-11-30
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2997, '2027-01-05', '2027-11-30') RETURNING id");
		courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 2, 3) RETURNING id", roundId);
	}

	@Test
	@DisplayName("그 달의 기록만 날짜별로 돌려주고, 월 통계를 계산한다")
	void monthOnly() {
		long userId = user("01000000201");
		long enrollment = enroll(userId, roundId);
		long book = addBook(enrollment, 300, true);
		addLog(book, 1, 100, LocalDate.of(2027, 2, 28));
		addLog(book, 101, 200, LocalDate.of(2027, 3, 1));
		addLog(book, 150, 250, LocalDate.of(2027, 3, 31));
		addLog(book, 251, 300, LocalDate.of(2027, 4, 1));

		CalendarResponse response = service.getMyCalendar(userId, 2027, 3, roundId);

		assertThat(response.enrollmentId()).isEqualTo(enrollment);
		assertThat(response.roundId()).isEqualTo(roundId);
		assertThat(response.year()).isEqualTo(2027);
		assertThat(response.month()).isEqualTo(3);
		assertThat(response.summary().readDays()).isEqualTo(2);
		assertThat(response.summary().books()).isEqualTo(1);
		assertThat(response.summary().pages()).isEqualTo(150); // 101–250
		assertThat(response.days()).extracting(CalendarResponse.Day::date)
				.containsExactly(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31));
	}

	@Test
	@DisplayName("소장도서 아님, 회차 기간 밖, 끝 쪽 초과 기록은 목록에 나오지만 counted가 false다")
	void countedFlag() {
		long userId = user("01000000202");
		long enrollment = enroll(userId, roundId);
		long notOwned = addBook(enrollment, 300, false);
		long owned = addBook(enrollment, 300, true);
		addLog(notOwned, 1, 100, LocalDate.of(2027, 1, 10));
		addLog(owned, 1, 50, LocalDate.of(2027, 1, 4));    // 회차 시작(1/5) 전
		addLog(owned, 51, 350, LocalDate.of(2027, 1, 10)); // 전체 쪽수(300) 초과
		addLog(owned, 1, 10, LocalDate.of(2027, 1, 10));

		CalendarResponse response = service.getMyCalendar(userId, 2027, 1, roundId);

		assertThat(response.days()).flatExtracting(CalendarResponse.Day::logs)
				.extracting(CalendarResponse.Log::counted)
				.containsExactly(false, false, false, true);
		assertThat(response.summary().pages()).isEqualTo(10);
		assertThat(response.summary().readDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("한 달 안에 모든 기록이 있으면 월 쪽수는 누적 집계(F-0502)의 누적 쪽수와 같다")
	void sameRuleAsProgress() {
		long userId = user("01000000203");
		long enrollment = enroll(userId, roundId);
		long first = addBook(enrollment, 300, true);
		long second = addBook(enrollment, 100, false);
		long third = addBook(enrollment, 200, true);
		addLog(first, 1, 120, LocalDate.of(2027, 5, 1));
		addLog(first, 100, 250, LocalDate.of(2027, 5, 3));
		addLog(second, 1, 100, LocalDate.of(2027, 5, 3));
		addLog(third, 1, 250, LocalDate.of(2027, 5, 9));
		addLog(third, 10, 30, LocalDate.of(2027, 5, 20));

		int monthPages = service.getMyCalendar(userId, 2027, 5, roundId).summary().pages();

		assertThat(monthPages).isEqualTo(readingProgressService.getProgress(enrollment).readPages());
	}

	@Test
	@DisplayName("연도와 월을 안 보내면 이번 달, 회차를 안 보내면 현재 회차의 참가 신청을 쓴다")
	void defaults() {
		long currentRoundId = roundService.findCurrentRound().orElseThrow().getId();
		long currentCourseId = insert("SELECT id FROM courses WHERE round_id = ? ORDER BY id LIMIT 1", currentRoundId);
		long userId = user("01000000204");
		long enrollment = insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id",
				userId, currentRoundId, currentCourseId);

		CalendarResponse response = service.getMyCalendar(userId, null, null, null);

		YearMonth now = YearMonth.now(clock);
		assertThat(response.enrollmentId()).isEqualTo(enrollment);
		assertThat(response.year()).isEqualTo(now.getYear());
		assertThat(response.month()).isEqualTo(now.getMonthValue());
	}

	@Test
	@DisplayName("그 회차에 참가 신청하지 않았으면 404, 남의 참가 신청은 보이지 않는다")
	void notEnrolled() {
		long owner = user("01000000205");
		enroll(owner, roundId);
		long other = user("01000000206");

		assertThatThrownBy(() -> service.getMyCalendar(other, 2027, 3, roundId))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
				});
	}

	@Test
	@DisplayName("연도와 월 중 하나만 보내거나, 월이 1~12가 아니면 400")
	void invalidMonth() {
		long userId = user("01000000207");
		enroll(userId, roundId);

		for (Integer[] yearMonth : new Integer[][] {{2027, null}, {null, 3}, {2027, 0}, {2027, 13}, {1999, 3}}) {
			assertThatThrownBy(() -> service.getMyCalendar(userId, yearMonth[0], yearMonth[1], roundId))
					.isInstanceOfSatisfying(ApiException.class, e -> {
						assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
						assertThat(e.getCode()).isEqualTo("INVALID_INPUT");
					});
		}
	}

	private long user(String phone) {
		return insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES ('테스트', ?, 'x', now(), 1) RETURNING id", phone);
	}

	private long enroll(long userId, long round) {
		return insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id",
				userId, round, courseId);
	}

	private long addBook(long enrollmentId, int totalPages, boolean libraryOwned) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('책', '저자', ?) RETURNING id", totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				enrollmentId, bookId, libraryOwned);
	}

	private void addLog(long enrollmentBookId, int start, int end, LocalDate readDate) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, status) VALUES (?, ?, ?, ?, 'SUBMITTED')",
				enrollmentBookId, start, end, readDate);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
