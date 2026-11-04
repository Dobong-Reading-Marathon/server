package com.dobong.dobong.admin;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 실제 서버를 띄우고 HTTP로 관리자 로그인과 참가자·관리자 토큰 분리를 확인. 로컬 DB 필요 (docker compose up -d).
 * 테스트용 관리자와 참가자를 직접 만들고 끝나면 지움 (세션은 ON DELETE CASCADE로 함께 지워짐).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminLoginIntegrationTest {

	private static final String PASSWORD = "admin-pass-2027";
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private String loginId;
	private String phone;

	@BeforeEach
	void setUp() {
		loginId = "test-admin-" + ThreadLocalRandom.current().nextInt(1_000_000);
		phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		jdbc.update("INSERT INTO admins (login_id, password_hash, name) VALUES (?, ?, ?)",
				loginId, new BCryptPasswordEncoder().encode(PASSWORD), "테스트관리자");
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM admins WHERE login_id = ?", loginId);
		jdbc.update("DELETE FROM users WHERE phone = ?", phone);
	}

	@Test
	@DisplayName("로그인 200 → 내 정보 200 → 로그아웃 204 → 같은 토큰 401")
	void loginMeLogout() throws Exception {
		HttpResponse<String> login = adminLogin(loginId, PASSWORD);
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(login.body()).contains("\"name\":\"테스트관리자\"").doesNotContain(loginId).doesNotContain(PASSWORD);
		String token = token(login.body());

		// DB에는 원본 토큰이 없음 (해시만)
		Integer rawRows = jdbc.queryForObject(
				"SELECT count(*) FROM admin_sessions WHERE token_hash = ?", Integer.class, token);
		assertThat(rawRows).isZero();

		assertThat(get("/api/admin/me", token).statusCode()).isEqualTo(200);
		assertThat(post("/api/admin/auth/logout", token, "").statusCode()).isEqualTo(204);
		assertThat(get("/api/admin/me", token).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("관리자 토큰으로 참가자 API는 401, 참가자 토큰으로 관리자 API도 401")
	void tokensAreSeparated() throws Exception {
		String adminToken = token(adminLogin(loginId, PASSWORD).body());
		HttpResponse<String> signup = post("/api/auth/signup", null, """
				{"name":"홍길동","phone":"%s","password":"reading2027","privacyAgreed":true}""".formatted(phone));
		assertThat(signup.statusCode()).isEqualTo(201);
		String userToken = token(signup.body());

		assertThat(get("/api/users/me", adminToken).statusCode()).isEqualTo(401);
		assertThat(get("/api/admin/me", userToken).statusCode()).isEqualTo(401);
		assertThat(post("/api/admin/auth/logout", userToken, "").statusCode()).isEqualTo(401);

		// 각자 자기 API는 정상
		assertThat(get("/api/users/me", userToken).statusCode()).isEqualTo(200);
		assertThat(get("/api/admin/me", adminToken).statusCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("토큰 없이 관리자 API는 401, 로그인 주소만 열려 있다")
	void onlyLoginIsPublic() throws Exception {
		assertThat(get("/api/admin/me", null).statusCode()).isEqualTo(401);
		assertThat(post("/api/admin/auth/logout", null, "").statusCode()).isEqualTo(401);
		assertThat(adminLogin("", "").statusCode()).isEqualTo(400);
	}

	@Test
	@DisplayName("없는 아이디와 틀린 비밀번호는 같은 응답, 5번 틀리면 맞는 비밀번호로도 429")
	void wrongPasswordAndLockout() throws Exception {
		HttpResponse<String> wrong = adminLogin(loginId, "wrongpass1");
		HttpResponse<String> unknown = adminLogin(loginId + "-x", PASSWORD);
		assertThat(wrong.statusCode()).isEqualTo(401);
		assertThat(unknown.statusCode()).isEqualTo(401);
		assertThat(wrong.body()).isEqualTo(unknown.body());

		for (int i = 0; i < 4; i++) { // 앞의 1번 + 4번 = 5번
			assertThat(adminLogin(loginId, "wrongpass1").statusCode()).isEqualTo(401);
		}
		HttpResponse<String> locked = adminLogin(loginId, PASSWORD);
		assertThat(locked.statusCode()).isEqualTo(429);
		assertThat(locked.body()).contains("ACCOUNT_LOCKED");
	}

	// ------------------------------------------------------------ helpers

	private HttpResponse<String> adminLogin(String id, String password) throws Exception {
		return post("/api/admin/auth/login", null, """
				{"loginId":"%s","password":"%s"}""".formatted(id, password));
	}

	private static String token(String body) {
		Matcher m = TOKEN.matcher(body);
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
