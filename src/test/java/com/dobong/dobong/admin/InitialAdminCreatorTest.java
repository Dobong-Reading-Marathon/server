package com.dobong.dobong.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class InitialAdminCreatorTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private void givenAdminCount(int count) {
		when(jdbcTemplate.queryForObject("SELECT count(*) FROM admins", Integer.class)).thenReturn(count);
	}

	@Test
	@DisplayName("관리자가 없고 환경변수가 있으면, 비밀번호를 BCrypt로 해시해서 계정을 만든다")
	void createsAdminWithHashedPassword() {
		givenAdminCount(0);

		new InitialAdminCreator(jdbcTemplate, "admin", "password123", "관리자").run(null);

		ArgumentCaptor<Object> hash = ArgumentCaptor.forClass(Object.class);
		verify(jdbcTemplate).update(startsWith("INSERT INTO admins"), eq("admin"), hash.capture(), eq("관리자"));
		assertThat((String) hash.getValue()).isNotEqualTo("password123");
		assertThat(new BCryptPasswordEncoder().matches("password123", (String) hash.getValue())).isTrue();
	}

	@Test
	@DisplayName("관리자가 이미 있으면 아무것도 하지 않는다")
	void skipsWhenAdminExists() {
		givenAdminCount(1);

		new InitialAdminCreator(jdbcTemplate, "admin", "password123", "관리자").run(null);

		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	@DisplayName("환경변수가 없으면 경고만 남기고 서버는 계속 시작한다")
	void skipsWhenEnvironmentVariablesMissing() {
		givenAdminCount(0);

		new InitialAdminCreator(jdbcTemplate, "", "", "관리자").run(null);

		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}

	@Test
	@DisplayName("비밀번호가 8자보다 짧으면 서버 시작을 멈춘다")
	void failsWhenPasswordTooShort() {
		givenAdminCount(0);

		InitialAdminCreator creator = new InitialAdminCreator(jdbcTemplate, "admin", "short", "관리자");

		assertThatThrownBy(() -> creator.run(null)).isInstanceOf(IllegalStateException.class);
		verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
	}
}
