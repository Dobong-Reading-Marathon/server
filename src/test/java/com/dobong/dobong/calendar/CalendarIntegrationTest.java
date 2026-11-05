package com.dobong.dobong.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 서버를 띄우고 HTTP로 독서달력을 조회한다. 로컬 DB 필요 (docker compose up -d).
 * 테스트가 만든 회원, 회차, 참가 신청, 책, 기록은 끝나고 지움.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private String phone;
	private String otherPhone;
	private long roundId;

	@BeforeEach
	void setUp() {
		phone = randomPhone();
		otherPhone = randomPhone();
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도와 회차 번호
		roundId = jdbc.queryForObject("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (?, 2996, '2027-01-05', '2027-11-30') RETURNING id",
				Long.class, ThreadLocalRandom.current().nextInt(1, 1_000_000));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM reading_logs WHERE enrollment_book_id IN (SELECT eb.id FROM enrollment_books eb JOIN enrollments e ON e.id = eb.enrollment_id WHERE e.round_id = ?)", roundId);
		jdbc.update("DELETE FROM enrollment_books WHERE enrollment_id IN (SELECT id FROM enrollments WHERE round_id = ?)", roundId);
		jdbc.update("DELETE FROM books b WHERE b.title = '달력시험책' AND NOT EXISTS (SELECT 1 FROM enrollment_books eb WHERE eb.book_id = b.id)");
		jdbc.update("DELETE FROM enrollments WHERE round_id = ?", roundId);
		jdbc.update("DELETE FROM courses WHERE round_id = ?", roundId);
		jdbc.update("DELETE FROM rounds WHERE id = ?", roundId);
		jdbc.update("DELETE FROM users WHERE phone IN (?, ?)", phone, otherPhone);
	}

	@Test
	@DisplayName("본인 달력은 200, 참가 신청하지 않은 회차는 404, 월이 잘못되면 400")
	void myCalendar() throws Exception {
		String token = signup(phone);
		String otherToken = signup(otherPhone);
		long courseId = jdbc.queryForObject("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '하프코스', 17500, 10, 50) RETURNING id",
				Long.class, roundId);
		long enrollment = jdbc.queryForObject("INSERT INTO enrollments (user_id, round_id, course_id) SELECT id, ?, ? FROM users WHERE phone = ? RETURNING id",
				Long.class, roundId, courseId, phone);
		long bookId = jdbc.queryForObject("INSERT INTO books (title, author, total_pages) VALUES ('달력시험책', '저자', 300) RETURNING id", Long.class);
		long enrollmentBook = jdbc.queryForObject("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, true) RETURNING id",
				Long.class, enrollment, bookId);
		jdbc.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, status) VALUES (?, 1, 120, '2027-03-02', 'DRAFT')", enrollmentBook);

		String base = "/api/me/calendar?roundId=" + roundId;
		HttpResponse<String> mine = get(base + "&year=2027&month=3", token);
		assertThat(mine.statusCode()).isEqualTo(200);
		assertThat(mine.body())
				.contains("\"enrollmentId\":" + enrollment)
				.contains("\"summary\":{\"readDays\":1,\"books\":1,\"pages\":120}")
				.contains("\"date\":\"2027-03-02\"")
				.contains("\"bookTitle\":\"달력시험책\"")
				.contains("\"counted\":true");

		HttpResponse<String> others = get(base + "&year=2027&month=3", otherToken);
		assertThat(others.statusCode()).isEqualTo(404);
		assertThat(others.body()).contains("ENROLLMENT_NOT_FOUND");

		assertThat(get(base + "&year=2027&month=13", token).statusCode()).isEqualTo(400);
		assertThat(get(base + "&year=2027", token).statusCode()).isEqualTo(400);
		assertThat(get(base + "&year=abc&month=3", token).statusCode()).isEqualTo(400);
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void unauthorized() throws Exception {
		assertThat(get("/api/me/calendar", null).statusCode()).isEqualTo(401);
	}

	// ------------------------------------------------------------ helpers

	private static String randomPhone() {
		return "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
	}

	private String signup(String phone) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("""
						{"name":"홍길동","phone":"%s","password":"reading2027","privacyAgreed":true,"privacyPolicyVersion":1}""".formatted(phone)))
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		Matcher m = TOKEN.matcher(response.body());
		assertThat(m.find()).as("응답에 token이 있어야 함").isTrue();
		return m.group(1);
	}

	private HttpResponse<String> get(String path, String token) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
		if (token != null) {
			b.header("Authorization", "Bearer " + token);
		}
		return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}
}
