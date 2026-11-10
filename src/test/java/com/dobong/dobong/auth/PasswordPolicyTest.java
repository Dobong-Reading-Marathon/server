package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

	private static final String PHONE = "01012345678";

	@ParameterizedTest
	@ValueSource(strings = {
			"reading1", // 딱 8자
			"reading2027",
			"Marathon1!",
			"책읽기book7777"
	})
	@DisplayName("규칙에 맞는 비밀번호는 통과한다")
	void accepts(String password) {
		assertThatCode(() -> PasswordPolicy.validate(password, PHONE)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("딱 64자는 통과한다")
	void accepts64() {
		String password = "a1" + "b".repeat(62);
		assertThatCode(() -> PasswordPolicy.validate(password, PHONE)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"read123",           // 7자
			"abcdefgh",          // 숫자 없음
			"12345678",          // 영문 없음
			"abcd 1234",         // 공백
			"pw12345678xx"       // 전화번호 뒤 8자리 포함
	})
	@DisplayName("규칙에 맞지 않으면 INVALID_PASSWORD")
	void rejects(String password) {
		assertThatThrownBy(() -> PasswordPolicy.validate(password, PHONE))
				.isInstanceOf(ApiException.class)
				.extracting("code").isEqualTo("INVALID_PASSWORD");
	}

	@Test
	@DisplayName("65자는 거부한다")
	void rejects65() {
		String password = "a1" + "b".repeat(63);
		assertThatThrownBy(() -> PasswordPolicy.validate(password, PHONE)).isInstanceOf(ApiException.class);
	}

	@Test
	@DisplayName("64자 안이어도 UTF-8로 72바이트를 넘으면 거부한다")
	void rejectsOver72Bytes() {
		String password = "a1" + "가".repeat(24); // 26자, 2 + 72 = 74바이트
		assertThatThrownBy(() -> PasswordPolicy.validate(password, PHONE)).isInstanceOf(ApiException.class);
	}

	@Test
	@DisplayName("null은 거부한다")
	void rejectsNull() {
		assertThatThrownBy(() -> PasswordPolicy.validate(null, PHONE)).isInstanceOf(ApiException.class);
	}
}
