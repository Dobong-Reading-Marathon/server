package com.dobong.dobong.auth;

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
 * 실제 서버를 띄우고 HTTP로 가입 → 내 정보 → 로그인 → 로그아웃 → 잠금을 확인. 로컬 DB 필요 (docker compose up -d).
 * 테스트가 만든 회원은 끝나고 지움 (세션은 ON DELETE CASCADE로 함께 지워짐).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginIntegrationTest {

	private static final String PASSWORD = "reading2027";
	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private String phone;

	@BeforeEach
	void setUp() {
		phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM users WHERE phone = ?", phone);
	}

	@Test
	@DisplayName("가입 토큰으로 내 정보 200 → 로그인 → 로그아웃하면 같은 토큰은 401")
	void signupLoginLogout() throws Exception {
		HttpResponse<String> signup = post("/api/auth/signup", null, """
				{"name":"홍길동","phone":"%s","password":"%s","privacyAgreed":true,"reminderAgreed":true}"""
				.formatted(phone, PASSWORD));
		assertThat(signup.statusCode()).isEqualTo(201);
		String signupToken = token(signup.body());

		// 가입 직후 바로 로그인된 상태
		HttpResponse<String> me = get("/api/users/me", signupToken);
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).contains("\"name\":\"홍길동\"").contains("\"reminderAgreed\":true")
				.doesNotContain(phone).doesNotContain("phone");

		// DB에는 원본 토큰이 없음 (해시만)
		Integer rawRows = jdbc.queryForObject(
				"SELECT count(*) FROM auth_sessions WHERE token_hash = ?", Integer.class, signupToken);
		assertThat(rawRows).isZero();

		// 하이픈 넣은 번호로 로그인
		String hyphenated = phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
		HttpResponse<String> login = login(hyphenated, PASSWORD, false);
		assertThat(login.statusCode()).isEqualTo(200);
		String loginToken = token(login.body());
		assertThat(loginToken).isNotEqualTo(signupToken);

		// 로그아웃하면 그 토큰만 무효
		assertThat(post("/api/auth/logout", loginToken, "").statusCode()).isEqualTo(204);
		assertThat(get("/api/users/me", loginToken).statusCode()).isEqualTo(401);
		assertThat(get("/api/users/me", signupToken).statusCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("토큰이 없거나 엉터리면 401")
	void unauthorized() throws Exception {
		assertThat(get("/api/users/me", null).statusCode()).isEqualTo(401);
		assertThat(get("/api/users/me", "not-a-real-token").statusCode()).isEqualTo(401);
		assertThat(post("/api/auth/logout", null, "").statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("틀린 비밀번호와 없는 번호는 같은 응답, 5번 틀리면 맞는 비밀번호로도 429")
	void wrongPasswordAndLockout() throws Exception {
		assertThat(post("/api/auth/signup", null, """
				{"name":"홍길동","phone":"%s","password":"%s","privacyAgreed":true}"""
				.formatted(phone, PASSWORD)).statusCode()).isEqualTo(201);

		HttpResponse<String> wrong = login(phone, "wrongpass1", true);
		HttpResponse<String> unknown = login("01000000000", PASSWORD, true);
		assertThat(wrong.statusCode()).isEqualTo(401);
		assertThat(unknown.statusCode()).isEqualTo(401);
		assertThat(wrong.body()).isEqualTo(unknown.body());

		for (int i = 0; i < 4; i++) { // 앞의 1번 + 4번 = 5번
			assertThat(login(phone, "wrongpass1", true).statusCode()).isEqualTo(401);
		}
		HttpResponse<String> locked = login(phone, PASSWORD, true);
		assertThat(locked.statusCode()).isEqualTo(429);
		assertThat(locked.body()).contains("ACCOUNT_LOCKED");
	}

	// ------------------------------------------------------------ helpers

	private HttpResponse<String> login(String phone, String password, boolean rememberMe) throws Exception {
		return post("/api/auth/login", null, """
				{"phone":"%s","password":"%s","rememberMe":%s}""".formatted(phone, password, rememberMe));
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
