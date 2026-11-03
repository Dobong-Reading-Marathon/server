package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 서버를 띄우고 HTTP로 회원가입을 확인. 로컬 DB가 필요 (docker compose up -d).
 * 테스트가 만든 회원은 끝나고 지움.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignupIntegrationTest {

	private static final String PASSWORD = "reading2027";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private String phone;

	@BeforeEach
	void setUp() {
		// 실제 번호와 겹치지 않게 테스트마다 무작위 번호
		phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM users WHERE phone = ?", phone);
	}

	@Test
	@DisplayName("가입하면 201, 응답에 전화번호가 없고 DB에는 원문 비밀번호가 없다")
	void signup() throws Exception {
		HttpResponse<String> res = signup(body("홍길동", phone, PASSWORD, true));

		assertThat(res.statusCode()).isEqualTo(201);
		assertThat(res.body()).contains("\"name\":\"홍길동\"").doesNotContain(phone).doesNotContain(PASSWORD);
		// F-0102: 가입 직후 바로 로그인된 상태 (토큰, 내 정보)
		assertThat(res.body()).contains("\"token\"").contains("\"user\"");

		String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE phone = ?", String.class, phone);
		assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
	}

	@Test
	@DisplayName("하이픈만 다른 같은 번호로 다시 가입하면 409")
	void duplicateWithHyphens() throws Exception {
		assertThat(signup(body("홍길동", phone, PASSWORD, true)).statusCode()).isEqualTo(201);

		String hyphenated = phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
		HttpResponse<String> res = signup(body("홍길동", hyphenated, PASSWORD, true));

		assertThat(res.statusCode()).isEqualTo(409);
		assertThat(res.body()).contains("PHONE_ALREADY_REGISTERED");
	}

	@Test
	@DisplayName("개인정보에 동의하지 않으면 400이고 회원이 만들어지지 않는다")
	void privacyNotAgreed() throws Exception {
		HttpResponse<String> res = signup(body("홍길동", phone, PASSWORD, false));

		assertThat(res.statusCode()).isEqualTo(400);
		assertThat(res.body()).contains("INVALID_INPUT").contains("privacyAgreed");
		Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE phone = ?", Integer.class, phone);
		assertThat(count).isZero();
	}

	@Test
	@DisplayName("독서 독려 알림에 동의하면 DB에 동의 시각이 저장되고, 안 보내면 비어 있다")
	void reminderAgreedAt() throws Exception {
		assertThat(signup(bodyWithReminder(phone, true)).statusCode()).isEqualTo(201);
		assertThat(reminderAgreedAt(phone)).isNotNull();

		jdbc.update("DELETE FROM users WHERE phone = ?", phone);
		assertThat(signup(body("홍길동", phone, PASSWORD, true)).statusCode()).isEqualTo(201);
		assertThat(reminderAgreedAt(phone)).isNull();
	}

	@Test
	@DisplayName("오류 응답에 보낸 비밀번호가 되돌아오지 않는다")
	void errorDoesNotEchoPassword() throws Exception {
		HttpResponse<String> res = signup(body("", phone, "secretpw99", true));

		assertThat(res.statusCode()).isEqualTo(400);
		assertThat(res.body()).doesNotContain("secretpw99");
	}

	private static String body(String name, String phone, String password, boolean agreed) {
		return """
				{"name":"%s","phone":"%s","password":"%s","privacyAgreed":%s}"""
				.formatted(name, phone, password, agreed);
	}

	private static String bodyWithReminder(String phone, boolean reminder) {
		return """
				{"name":"홍길동","phone":"%s","password":"%s","privacyAgreed":true,"reminderAgreed":%s}"""
				.formatted(phone, PASSWORD, reminder);
	}

	private Object reminderAgreedAt(String phone) {
		return jdbc.queryForObject("SELECT reminder_agreed_at FROM users WHERE phone = ?", Object.class, phone);
	}

	private HttpResponse<String> signup(String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/signup"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
