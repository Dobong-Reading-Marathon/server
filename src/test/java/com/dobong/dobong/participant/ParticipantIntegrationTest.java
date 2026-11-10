package com.dobong.dobong.participant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 실제 서버를 띄우고 HTTP로 참가자 목록을 조회한다. 로컬 DB 필요 (docker compose up -d).
 * 테스트가 만든 관리자, 회원, 회차, 참가 신청은 끝나고 지움.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParticipantIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final String ADMIN_PASSWORD = "admin-pass-2027";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private String adminLoginId;
	private String phone;
	private long roundId;

	@BeforeEach
	void setUp() {
		adminLoginId = "test-admin-" + ThreadLocalRandom.current().nextInt(1_000_000);
		jdbc.update("INSERT INTO admins (login_id, password_hash, name) VALUES (?, ?, '테스트관리자')",
				adminLoginId, new BCryptPasswordEncoder().encode(ADMIN_PASSWORD));
		phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도와 회차 번호
		roundId = jdbc.queryForObject("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (?, 2995, '2027-01-05', '2027-11-30') RETURNING id",
				Long.class, ThreadLocalRandom.current().nextInt(1, 1_000_000));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM enrollments WHERE round_id = ?", roundId);
		jdbc.update("DELETE FROM courses WHERE round_id = ?", roundId);
		jdbc.update("DELETE FROM rounds WHERE id = ?", roundId);
		jdbc.update("DELETE FROM users WHERE phone = ?", phone);
		jdbc.update("DELETE FROM admins WHERE login_id = ?", adminLoginId);
	}

	@Test
	@DisplayName("관리자는 200, 잘못된 상태 값은 400, 없는 회차는 404, 참가자 토큰은 401")
	void participants() throws Exception {
		String userToken = signup(phone);
		String adminToken = adminLogin();
		long courseId = jdbc.queryForObject("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '하프코스', 17500, 10, 50) RETURNING id",
				Long.class, roundId);
		long enrollment = jdbc.queryForObject("INSERT INTO enrollments (user_id, round_id, course_id) SELECT id, ?, ? FROM users WHERE phone = ? RETURNING id",
				Long.class, roundId, courseId, phone);

		String path = "/api/admin/rounds/" + roundId + "/enrollments";
		HttpResponse<String> list = get(path + "?keyword=" + phone.substring(7), adminToken);
		assertThat(list.statusCode()).isEqualTo(200);
		assertThat(list.body())
				.contains("\"totalElements\":1")
				.contains("\"enrollmentId\":" + enrollment)
				.contains("\"name\":\"홍길동\"")
				.contains("\"phone\":\"" + phone + "\"")
				.contains("\"status\":\"IN_PROGRESS\"")
				.contains("\"pages\":{\"current\":0,\"target\":17500,\"remaining\":17500,\"percent\":0}")
				.doesNotContain("password");

		assertThat(get(path + "?status=WRONG", adminToken).statusCode()).isEqualTo(400);
		assertThat(get(path + "?size=101", adminToken).statusCode()).isEqualTo(400);

		HttpResponse<String> noRound = get("/api/admin/rounds/" + Long.MAX_VALUE + "/enrollments", adminToken);
		assertThat(noRound.statusCode()).isEqualTo(404);
		assertThat(noRound.body()).contains("ROUND_NOT_FOUND");

		// 참가자 토큰은 관리자 주소에서 로그인 안 한 것과 같음
		assertThat(get(path, userToken).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("사용자별 기록 조회: 관리자는 200, 없는 참가 신청은 404, 숫자가 아니면 400, 참가자 토큰은 401")
	void detail() throws Exception {
		String userToken = signup(phone);
		String adminToken = adminLogin();
		long courseId = jdbc.queryForObject("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '하프코스', 17500, 10, 50) RETURNING id",
				Long.class, roundId);
		long enrollment = jdbc.queryForObject("INSERT INTO enrollments (user_id, round_id, course_id) SELECT id, ?, ? FROM users WHERE phone = ? RETURNING id",
				Long.class, roundId, courseId, phone);

		String path = "/api/admin/enrollments/" + enrollment;
		HttpResponse<String> detail = get(path, adminToken);
		assertThat(detail.statusCode()).isEqualTo(200);
		assertThat(detail.body())
				.contains("\"enrollmentId\":" + enrollment)
				.contains("\"user\":{")
				.contains("\"phone\":\"" + phone + "\"")
				.contains("\"progress\":{\"pages\":{\"current\":0,\"target\":17500,\"remaining\":17500,\"percent\":0}")
				.contains("\"books\":[]")
				.doesNotContain("password");

		HttpResponse<String> notFound = get("/api/admin/enrollments/" + Long.MAX_VALUE, adminToken);
		assertThat(notFound.statusCode()).isEqualTo(404);
		assertThat(notFound.body()).contains("ENROLLMENT_NOT_FOUND");
		assertThat(get("/api/admin/enrollments/abc", adminToken).statusCode()).isEqualTo(400);
		assertThat(get(path, userToken).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("명단 다운로드: 관리자는 xlsx 파일, 없는 회차는 JSON 404, 참가자 토큰은 401")
	void export() throws Exception {
		String userToken = signup(phone);
		String adminToken = adminLogin();
		long courseId = jdbc.queryForObject("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '하프코스', 17500, 10, 50) RETURNING id",
				Long.class, roundId);
		jdbc.update("INSERT INTO enrollments (user_id, round_id, course_id) SELECT id, ?, ? FROM users WHERE phone = ?", roundId, courseId, phone);
		int roundNo = jdbc.queryForObject("SELECT round_no FROM rounds WHERE id = ?", Integer.class, roundId);

		String path = "/api/admin/rounds/" + roundId + "/enrollments/export";
		HttpResponse<byte[]> file = getBytes(path, adminToken);
		assertThat(file.statusCode()).isEqualTo(200);
		assertThat(file.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith(ParticipantController.XLSX.toString()));
		assertThat(file.headers().firstValue("Content-Disposition"))
				.hasValue("attachment; filename=\"participants-2995-" + roundNo + ".xlsx\"");
		assertThat(file.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.body()))) {
			Sheet sheet = workbook.getSheetAt(0);
			assertThat(sheet.getLastRowNum()).isEqualTo(1);
			assertThat(sheet.getRow(1).getCell(2).getStringCellValue())
					.isEqualTo(phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7));
		}

		// 필터는 참가자 목록과 같다
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(getBytes(path + "?status=COMPLETED", adminToken).body()))) {
			assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
		}

		HttpResponse<String> noRound = get("/api/admin/rounds/" + Long.MAX_VALUE + "/enrollments/export", adminToken);
		assertThat(noRound.statusCode()).isEqualTo(404);
		assertThat(noRound.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("application/json"));
		assertThat(noRound.body()).contains("ROUND_NOT_FOUND");
		assertThat(get(path + "?status=WRONG", adminToken).statusCode()).isEqualTo(400);
		assertThat(get(path, userToken).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("완주 승인: 사유를 적으면 200과 인증서 번호, 다시 승인하면 409, 대출 권수가 없으면 400, 참가자 토큰은 401")
	void approveCompletion() throws Exception {
		String userToken = signup(phone);
		String adminToken = adminLogin();
		long courseId = jdbc.queryForObject("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '하프코스', 17500, 10, 50) RETURNING id",
				Long.class, roundId);
		long enrollment = jdbc.queryForObject("INSERT INTO enrollments (user_id, round_id, course_id) SELECT id, ?, ? FROM users WHERE phone = ? RETURNING id",
				Long.class, roundId, courseId, phone);
		String path = "/api/admin/enrollments/" + enrollment + "/completion-approval";

		assertThat(post(path, adminToken, "{}").statusCode()).isEqualTo(400);
		assertThat(post(path, userToken, "{\"verifiedLoanCount\":50}").statusCode()).isEqualTo(401);
		HttpResponse<String> notMet = post(path, adminToken, "{\"verifiedLoanCount\":50}");
		assertThat(notMet.statusCode()).isEqualTo(409);
		assertThat(notMet.body()).contains("COMPLETION_NOT_MET");

		HttpResponse<String> approved = post(path, adminToken, "{\"verifiedLoanCount\":50,\"note\":\"오프라인 수첩 합산\"}");
		assertThat(approved.statusCode()).isEqualTo(200);
		assertThat(approved.body())
				.contains("\"status\":\"COMPLETED\"")
				.contains("\"certificateNo\":\"DB-2995-")
				.contains("\"approvalNote\":\"오프라인 수첩 합산\"");

		HttpResponse<String> again = post(path, adminToken, "{\"verifiedLoanCount\":50}");
		assertThat(again.statusCode()).isEqualTo(409);
		assertThat(again.body()).contains("ALREADY_COMPLETED");
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void unauthorized() throws Exception {
		assertThat(get("/api/admin/rounds/" + roundId + "/enrollments", null).statusCode()).isEqualTo(401);
		assertThat(get("/api/admin/enrollments/1", null).statusCode()).isEqualTo(401);
	}

	// ------------------------------------------------------------ helpers

	private String adminLogin() throws Exception {
		return token(post("/api/admin/auth/login", """
				{"loginId":"%s","password":"%s"}""".formatted(adminLoginId, ADMIN_PASSWORD)));
	}

	private String signup(String phone) throws Exception {
		return token(post("/api/auth/signup", """
				{"name":"홍길동","phone":"%s","password":"reading2027","privacyAgreed":true,"privacyPolicyVersion":1}""".formatted(phone)));
	}

	private static String token(HttpResponse<String> response) {
		assertThat(response.statusCode()).isBetween(200, 201);
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

	private HttpResponse<byte[]> getBytes(String path, String token) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Authorization", "Bearer " + token)
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
	}

	private HttpResponse<String> post(String path, String token, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + token)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
