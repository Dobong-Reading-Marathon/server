package com.dobong.dobong.privacy;

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
 * 실제 서버를 띄우고 HTTP로 처리방침 조회와 동의 버전 저장을 확인. 로컬 DB 필요 (docker compose up -d).
 * 테스트가 만든 회원은 끝나고 지움.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrivacyIntegrationTest {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");

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
	@DisplayName("로그인 없이 처리방침 전문 200, 절 4개")
	void policyIsPublic() throws Exception {
		HttpResponse<String> res = get("/api/privacy-policy", null);

		assertThat(res.statusCode()).isEqualTo(200);
		assertThat(res.body()).contains("\"version\"").contains("1. 수집 항목").contains("4. 동의 거부 권리와 불이익");
	}

	@Test
	@DisplayName("받은 버전으로 가입하면 그 버전이 저장되고, 내 동의 내역에 나온다")
	void signupStoresVersion() throws Exception {
		int version = currentVersion();

		HttpResponse<String> signup = signup(version);
		assertThat(signup.statusCode()).isEqualTo(201);

		Integer saved = jdbc.queryForObject(
				"SELECT privacy_agreed_version FROM users WHERE phone = ?", Integer.class, phone);
		assertThat(saved).isEqualTo(version);

		HttpResponse<String> consent = get("/api/users/me/privacy-consent", token(signup.body()));
		assertThat(consent.statusCode()).isEqualTo(200);
		assertThat(consent.body()).contains("\"privacyPolicyVersion\":" + version)
				.contains("\"privacyAgreedAt\"").doesNotContain(phone);
	}

	@Test
	@DisplayName("현재와 다른 버전으로 가입하면 409이고 회원이 만들어지지 않는다. 버전이 없으면 400")
	void outdatedOrMissingVersion() throws Exception {
		HttpResponse<String> outdated = signup(currentVersion() + 1);
		assertThat(outdated.statusCode()).isEqualTo(409);
		assertThat(outdated.body()).contains("PRIVACY_POLICY_OUTDATED");

		HttpResponse<String> missing = post("/api/auth/signup", """
				{"name":"홍길동","phone":"%s","password":"reading2027","privacyAgreed":true}""".formatted(phone));
		assertThat(missing.statusCode()).isEqualTo(400);
		assertThat(missing.body()).contains("privacyPolicyVersion");

		Integer count = jdbc.queryForObject("SELECT count(*) FROM users WHERE phone = ?", Integer.class, phone);
		assertThat(count).isZero();
	}

	@Test
	@DisplayName("내 동의 내역은 로그인 없이 401")
	void consentRequiresLogin() throws Exception {
		assertThat(get("/api/users/me/privacy-consent", null).statusCode()).isEqualTo(401);
	}

	// ------------------------------------------------------------ helpers

	private int currentVersion() throws Exception {
		Matcher m = VERSION.matcher(get("/api/privacy-policy", null).body());
		assertThat(m.find()).isTrue();
		return Integer.parseInt(m.group(1));
	}

	private HttpResponse<String> signup(int version) throws Exception {
		return post("/api/auth/signup", """
				{"name":"홍길동","phone":"%s","password":"reading2027","privacyAgreed":true,"privacyPolicyVersion":%d}"""
				.formatted(phone, version));
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

	private HttpResponse<String> post(String path, String json) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
