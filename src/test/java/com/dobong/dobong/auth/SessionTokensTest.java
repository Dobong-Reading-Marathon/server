package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTokensTest {

	@Test
	@DisplayName("토큰은 매번 다르고, URL에 써도 되는 43자 문자열이다 (256비트)")
	void newTokenIsRandom() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			String token = SessionTokens.newToken();
			assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
			tokens.add(token);
		}
		assertThat(tokens).hasSize(1000);
	}

	@Test
	@DisplayName("같은 토큰은 같은 해시, 다른 토큰은 다른 해시. 해시는 64자 hex이고 원본을 포함하지 않는다")
	void hash() {
		String token = SessionTokens.newToken();

		assertThat(SessionTokens.hash(token)).isEqualTo(SessionTokens.hash(token))
				.hasSize(64).matches("^[0-9a-f]+$").doesNotContain(token);
		assertThat(SessionTokens.hash(token)).isNotEqualTo(SessionTokens.hash(SessionTokens.newToken()));
	}
}
