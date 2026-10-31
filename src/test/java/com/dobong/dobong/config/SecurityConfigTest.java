package com.dobong.dobong.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 실제 서버를 띄우고 HTTP로 보안 규칙을 확인한다. 로컬 DB가 필요 (docker compose up -d).
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.cors.allowed-origins=https://front.example")
class SecurityConfigTest {

	private static final String ALLOWED = "https://front.example";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("로그인 없이 /api/** 를 부르면 401과 공통 오류 형식")
	void apiRequiresLogin() throws Exception {
		HttpResponse<String> res = send(HttpRequest.newBuilder(uri("/api/anything")).GET().build());

		assertThat(res.statusCode()).isEqualTo(401);
		assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("application/json"));
		assertThat(res.body()).contains("\"code\":\"UNAUTHORIZED\"");
	}

	@Test
	@DisplayName("관리자 주소와 규칙에 없는 주소도 로그인 없이는 막힌다")
	void adminAndUnknownAreBlocked() throws Exception {
		assertThat(send(HttpRequest.newBuilder(uri("/api/admin/anything")).GET().build()).statusCode()).isEqualTo(401);
		assertThat(send(HttpRequest.newBuilder(uri("/not-api")).GET().build()).statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("허용한 프론트 주소의 CORS 사전 확인은 통과한다")
	void corsAllowedOrigin() throws Exception {
		HttpResponse<String> res = send(preflight(ALLOWED));

		assertThat(res.statusCode()).isEqualTo(200);
		assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).hasValue(ALLOWED);
	}

	@Test
	@DisplayName("허용하지 않은 주소의 CORS 사전 확인은 거부한다")
	void corsOtherOriginRejected() throws Exception {
		HttpResponse<String> res = send(preflight("https://evil.example"));

		assertThat(res.statusCode()).isEqualTo(403);
		assertThat(res.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
	}

	@Test
	@DisplayName("Spring 기본 계정(user + 임시 비밀번호)이 만들어지지 않는다")
	void noDefaultUser() {
		assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
	}

	private HttpRequest preflight(String origin) {
		return HttpRequest.newBuilder(uri("/api/anything"))
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.header("Origin", origin)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization,content-type")
				.build();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
