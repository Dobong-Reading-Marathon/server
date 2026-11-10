package com.dobong.dobong.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

	@Test
	@DisplayName("와일드카드(*)가 있으면 서버 시작을 멈춘다")
	void rejectsWildcard() {
		assertThatThrownBy(() -> new CorsProperties(List.of("*")))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CorsProperties(List.of("https://*.pages.dev")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("설정이 없거나 빈 값이면 빈 목록 (다른 주소의 요청을 모두 막음)")
	void emptyByDefault() {
		assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
		assertThat(new CorsProperties(List.of("", "  ")).allowedOrigins()).isEmpty();
	}

	@Test
	@DisplayName("앞뒤 공백은 지운다")
	void strips() {
		assertThat(new CorsProperties(List.of(" https://a.example ", "http://localhost:5173")).allowedOrigins())
				.containsExactly("https://a.example", "http://localhost:5173");
	}
}
