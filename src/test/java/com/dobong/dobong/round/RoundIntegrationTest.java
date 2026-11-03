package com.dobong.dobong.round;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 서버를 띄우고 HTTP로 현재 회차 조회를 확인. 로컬 DB 필요 (docker compose up -d).
 * local 설정이라 V6(2027 회차)과 시연용 회차(db/seed)가 모두 들어가 있음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoundIntegrationTest {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	@DisplayName("로그인 없이 현재 회차 200. 로컬에서는 시연용 회차 덕분에 항상 신청 가능, 코스 6개")
	void currentRound() throws Exception {
		HttpResponse<String> res = send("GET");

		assertThat(res.statusCode()).isEqualTo(200);
		assertThat(res.body()).contains("\"applyOpen\":true")
				.contains("걷기코스").contains("풀코스(가족)").contains("고수코스")
				.contains("\"distanceKm\":42.195");
	}

	@Test
	@DisplayName("V6으로 2027 회차와 코스 6개가 들어가 있다")
	void seeded2027() {
		List<String> names = jdbc.queryForList("""
				SELECT c.name FROM courses c JOIN rounds r ON r.id = c.round_id
				WHERE r.year = 2027 AND r.round_no = 1 ORDER BY c.sort_order""", String.class);

		assertThat(names).containsExactly("걷기코스", "단축코스", "하프코스", "풀코스(개인)", "풀코스(가족)", "고수코스");
	}

	@Test
	@DisplayName("조회만 열려 있고, 다른 방식(POST)은 로그인 없이 막힌다")
	void onlyGetIsPublic() throws Exception {
		assertThat(send("POST").statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> send(String method) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/rounds/current"))
				.method(method, HttpRequest.BodyPublishers.noBody())
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
