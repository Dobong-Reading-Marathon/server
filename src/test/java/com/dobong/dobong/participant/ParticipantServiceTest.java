package com.dobong.dobong.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.participant.dto.ParticipantPageResponse;
import com.dobong.dobong.participant.dto.ParticipantResponse;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.dto.ProgressResponse.GoalInfo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참가자 목록의 검색, 필터, 페이지 나누기와 집계·완주 판정을 실제 DB에서 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class ParticipantServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2027, 3, 1);
	private static final String REVIEW_500 = "가".repeat(500);
	private static final ParticipantFilter ALL = new ParticipantFilter(null, null, null);

	@Autowired
	private ParticipantService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long roundId;
	private long courseA;
	private long courseB;
	private long pending;    // 홍길동, 코스 A, 기준을 채움
	private long inProgress; // 김철수, 코스 A
	private long completed;  // 이영희, 코스 B, 사서 승인

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2996, '2027-01-05', '2027-11-30') RETURNING id");
		courseA = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스A', 500, 1, 3) RETURNING id", roundId);
		courseB = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스B', 1000, 2, 5) RETURNING id", roundId);

		pending = enroll(user("홍길동", "01070000001"), roundId, courseA, "ACTIVE", null);
		addLog(addBook(pending, 500), 1, 500, REVIEW_500, "APPROVED");

		inProgress = enroll(user("김철수", "01070000002"), roundId, courseA, "ACTIVE", null);
		addLog(addBook(inProgress, 300), 1, 120, null, "SUBMITTED");

		completed = enroll(user("이영희", "01070000003"), roundId, courseB, "COMPLETED", "TEST-0001");

		// 다른 회차 참가자는 나오지 않아야 함
		long otherRound = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (2, 2996, '2027-01-05', '2027-11-30') RETURNING id");
		long otherCourse = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '다른코스', 500, 1, 3) RETURNING id", otherRound);
		enroll(user("홍길순", "01070000004"), otherRound, otherCourse, "ACTIVE", null);
	}

	@Test
	@DisplayName("회차 참가자를 참가 신청 순서로, 집계와 완주 상태를 붙여 돌려준다")
	void listsWithProgress() {
		ParticipantPageResponse page = service.getPage(roundId, ALL, 0, 20);

		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(1);
		assertThat(ids(page)).containsExactly(pending, inProgress, completed);

		ParticipantResponse first = page.content().get(0);
		assertThat(first.name()).isEqualTo("홍길동");
		assertThat(first.phone()).isEqualTo("01070000001");
		assertThat(first.course().name()).isEqualTo("시험코스A");
		assertThat(first.status()).isEqualTo(CompletionStatus.PENDING_APPROVAL);
		assertThat(first.pages()).isEqualTo(new GoalInfo(500, 500, 0, 100));
		assertThat(first.reviews()).isEqualTo(new GoalInfo(1, 1, 0, 100));
		assertThat(first.books()).isEqualTo(new GoalInfo(1, 3, 2, 33));
		assertThat(first.completedBooks()).isEqualTo(1);
		assertThat(first.certificateNo()).isNull();

		ParticipantResponse second = page.content().get(1);
		assertThat(second.status()).isEqualTo(CompletionStatus.IN_PROGRESS);
		assertThat(second.pages()).isEqualTo(new GoalInfo(120, 500, 380, 24));

		ParticipantResponse third = page.content().get(2);
		assertThat(third.status()).isEqualTo(CompletionStatus.COMPLETED);
		assertThat(third.pages().current()).isZero(); // 기록이 없어도 목록에 나옴
		assertThat(third.certificateNo()).isEqualTo("TEST-0001");
	}

	@Test
	@DisplayName("검색어는 이름 일부, 또는 하이픈을 뺀 전화번호 일부와 맞춘다")
	void keyword() {
		assertThat(ids(search("길동"))).containsExactly(pending);
		assertThat(ids(search("  길동  "))).containsExactly(pending);
		assertThat(ids(search("010-7000-0002"))).containsExactly(inProgress);
		assertThat(ids(search("0003"))).containsExactly(completed);
		// 글자가 섞이면 전화번호로 찾지 않음 ("1"이 모든 번호에 맞지 않게)
		assertThat(ids(search("홍1"))).isEmpty();
	}

	@Test
	@DisplayName("검색어의 %, _ 는 글자 그대로 찾는다")
	void keywordEscapesWildcards() {
		assertThat(ids(search("%"))).isEmpty();
		assertThat(ids(search("_"))).isEmpty();
	}

	@Test
	@DisplayName("코스와 완주 상태로 거른다")
	void filters() {
		assertThat(ids(service.getPage(roundId, new ParticipantFilter(null, courseB, null), 0, 20))).containsExactly(completed);
		assertThat(ids(service.getPage(roundId, new ParticipantFilter(null, null, CompletionStatus.PENDING_APPROVAL), 0, 20)))
				.containsExactly(pending);
		assertThat(ids(service.getPage(roundId, new ParticipantFilter(null, courseA, CompletionStatus.COMPLETED), 0, 20))).isEmpty();
	}

	@Test
	@DisplayName("거른 뒤에 페이지를 나눈다")
	void paging() {
		ParticipantPageResponse first = service.getPage(roundId, ALL, 0, 2);
		assertThat(ids(first)).containsExactly(pending, inProgress);
		assertThat(first.totalElements()).isEqualTo(3);
		assertThat(first.totalPages()).isEqualTo(2);

		assertThat(ids(service.getPage(roundId, ALL, 1, 2))).containsExactly(completed);
		assertThat(service.getPage(roundId, ALL, 5, 2).content()).isEmpty();
	}

	@Test
	@DisplayName("없는 회차는 404, page·size·검색어가 범위 밖이면 400")
	void errors() {
		assertError(() -> service.getPage(Long.MAX_VALUE, ALL, 0, 20), HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND");
		assertError(() -> service.getPage(roundId, ALL, -1, 20), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
		assertError(() -> service.getPage(roundId, ALL, 0, 0), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
		assertError(() -> service.getPage(roundId, ALL, 0, ParticipantService.MAX_SIZE + 1), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
		String longKeyword = "가".repeat(ParticipantService.MAX_KEYWORD_LENGTH + 1);
		assertError(() -> search(longKeyword), HttpStatus.BAD_REQUEST, "INVALID_INPUT");
	}

	private ParticipantPageResponse search(String keyword) {
		return service.getPage(roundId, new ParticipantFilter(keyword, null, null), 0, 20);
	}

	private static List<Long> ids(ParticipantPageResponse page) {
		return page.content().stream().map(ParticipantResponse::enrollmentId).toList();
	}

	private static void assertError(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	private long user(String name, String phone) {
		return insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES (?, ?, 'x', now(), 1) RETURNING id",
				name, phone);
	}

	private long enroll(long userId, long round, long course, String status, String certificateNo) {
		return insert("INSERT INTO enrollments (user_id, round_id, course_id, status, certificate_no) VALUES (?, ?, ?, ?, ?) RETURNING id",
				userId, round, course, status, certificateNo);
	}

	private long addBook(long enrollmentId, int totalPages) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('책', '저자', ?) RETURNING id", totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, true) RETURNING id",
				enrollmentId, bookId);
	}

	private void addLog(long enrollmentBookId, int start, int end, String review, String status) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status) VALUES (?, ?, ?, ?, ?, ?)",
				enrollmentBookId, start, end, IN_PERIOD, review, status);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
