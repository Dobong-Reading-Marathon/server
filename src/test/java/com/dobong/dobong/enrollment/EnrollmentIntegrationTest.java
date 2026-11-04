package com.dobong.dobong.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 서버를 띄우고 HTTP로 참가 신청을 확인. 로컬 DB 필요 (docker compose up -d).
 * local 설정이라 시연용 회차(db/seed, 항상 신청 가능)가 현재 회차로 잡힘.
 * enrollments는 users 삭제 시 함께 지워지지 않으므로 참가부터 지우고 회원을 지움.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentIntegrationTest {

	private static final String PASSWORD = "reading2027";
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern FIRST_COURSE_ID = Pattern.compile("\"courses\"\\s*:\\s*\\[\\s*\\{\\s*\"id\"\\s*:\\s*(\\d+)");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	private final HttpClient http = HttpClient.newHttpClient();
	private final List<String> phones = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (String phone : phones) {
			jdbc.update("DELETE FROM enrollments WHERE user_id IN (SELECT id FROM users WHERE phone = ?)", phone);
			jdbc.update("DELETE FROM users WHERE phone = ?", phone);
		}
	}

	@Test
	@DisplayName("신청 전 204 → 신청 201 → 내 참가 200 → 같은 회차 다시 신청 409")
	void enrollFlow() throws Exception {
		String token = signup();
		long courseId = currentCourseId();

		assertThat(get("/api/enrollments/current", token).statusCode()).isEqualTo(204);

		HttpResponse<String> enroll = enroll(token, courseId);
		assertThat(enroll.statusCode()).isEqualTo(201);
		assertThat(enroll.body()).contains("\"status\":\"ACTIVE\"").contains("\"id\":" + courseId)
				.doesNotContain("userId").doesNotContain("phone");

		HttpResponse<String> current = get("/api/enrollments/current", token);
		assertThat(current.statusCode()).isEqualTo(200);
		assertThat(current.body()).contains("\"status\":\"ACTIVE\"");

		HttpResponse<String> again = enroll(token, courseId);
		assertThat(again.statusCode()).isEqualTo(409);
		assertThat(again.body()).contains("ALREADY_ENROLLED");
	}

	@Test
	@DisplayName("로그인 없이는 401, 코스 없으면 400, 없는 코스면 404")
	void invalidRequests() throws Exception {
		assertThat(post("/api/enrollments", null, "{\"courseId\":1}").statusCode()).isEqualTo(401);
		assertThat(get("/api/enrollments/current", null).statusCode()).isEqualTo(401);

		String token = signup();
		HttpResponse<String> noCourse = post("/api/enrollments", token, "{}");
		assertThat(noCourse.statusCode()).isEqualTo(400);
		assertThat(noCourse.body()).contains("INVALID_INPUT").contains("courseId");

		HttpResponse<String> unknown = enroll(token, 999_999_999L);
		assertThat(unknown.statusCode()).isEqualTo(404);
		assertThat(unknown.body()).contains("COURSE_NOT_FOUND");
	}

	@Test
	@DisplayName("신청 기간이 아닌 회차(2027, 2/2~6/30)의 코스는 400")
	void applyClosed() throws Exception {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		assumeFalse(!today.isBefore(LocalDate.of(2027, 2, 2)) && !today.isAfter(LocalDate.of(2027, 6, 30)),
				"오늘이 2027 신청 기간이라 건너뜀");

		Long course2027 = jdbc.queryForObject("""
				SELECT c.id FROM courses c JOIN rounds r ON r.id = c.round_id
				WHERE r.year = 2027 AND r.round_no = 1 ORDER BY c.sort_order LIMIT 1""", Long.class);

		HttpResponse<String> res = enroll(signup(), course2027);
		assertThat(res.statusCode()).isEqualTo(400);
		assertThat(res.body()).contains("APPLY_CLOSED");
	}

	@Test
	@DisplayName("다른 회원의 참가 id는 본인 것으로 확인되지 않는다 (IDOR 방지용 조회)")
	void ownershipCheck() throws Exception {
		String token = signup();
		String ownerPhone = phones.get(phones.size() - 1);
		assertThat(enroll(token, currentCourseId()).statusCode()).isEqualTo(201);
		signup();
		String otherPhone = phones.get(phones.size() - 1);

		Long enrollmentId = jdbc.queryForObject(
				"SELECT e.id FROM enrollments e JOIN users u ON u.id = e.user_id WHERE u.phone = ?", Long.class, ownerPhone);
		Long ownerId = jdbc.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, ownerPhone);
		Long otherId = jdbc.queryForObject("SELECT id FROM users WHERE phone = ?", Long.class, otherPhone);

		assertThat(enrollmentRepository.existsByIdAndUserId(enrollmentId, ownerId)).isTrue();
		assertThat(enrollmentRepository.existsByIdAndUserId(enrollmentId, otherId)).isFalse();
	}

	// ------------------------------------------------------------ helpers

	// 무작위 번호로 가입하고 토큰을 돌려줌
	private String signup() throws Exception {
		String phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		phones.add(phone);
		HttpResponse<String> res = post("/api/auth/signup", null, """
				{"name":"홍길동","phone":"%s","password":"%s","privacyAgreed":true}""".formatted(phone, PASSWORD));
		assertThat(res.statusCode()).isEqualTo(201);
		Matcher m = TOKEN.matcher(res.body());
		assertThat(m.find()).as("응답에 token이 있어야 함").isTrue();
		return m.group(1);
	}

	// 현재 회차(로컬은 시연용 회차)의 첫 코스 id
	private long currentCourseId() throws Exception {
		HttpResponse<String> res = get("/api/rounds/current", null);
		assertThat(res.statusCode()).isEqualTo(200);
		assertThat(res.body()).as("로컬에서는 시연용 회차로 신청 가능해야 함").contains("\"applyOpen\":true");
		Matcher m = FIRST_COURSE_ID.matcher(res.body());
		assertThat(m.find()).as("현재 회차에 코스가 있어야 함").isTrue();
		return Long.parseLong(m.group(1));
	}

	private HttpResponse<String> enroll(String token, long courseId) throws Exception {
		return post("/api/enrollments", token, "{\"courseId\":" + courseId + "}");
	}

	private HttpResponse<String> get(String path, String token) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
		if (token != null) {
			b.header("Authorization", "Bearer " + token);
		}
		return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String token, String json) throws Exception {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json));
		if (token != null) {
			b.header("Authorization", "Bearer " + token);
		}
		return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}
}
