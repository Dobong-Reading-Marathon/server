package com.dobong.dobong.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

	@Test
	@DisplayName("하이픈, 공백, 점을 지우고 숫자만 남긴다")
	void normalizes() {
		assertThat(PhoneNumbers.normalizeOrNull("010-1234-5678")).isEqualTo("01012345678");
		assertThat(PhoneNumbers.normalizeOrNull(" 010 1234 5678 ")).isEqualTo("01012345678");
		assertThat(PhoneNumbers.normalizeOrNull("010.1234.5678")).isEqualTo("01012345678");
		assertThat(PhoneNumbers.normalizeOrNull("0111234567")).isEqualTo("0111234567");
	}

	@Test
	@DisplayName("휴대폰 번호가 아니면 null")
	void rejectsInvalid() {
		assertThat(PhoneNumbers.normalizeOrNull(null)).isNull();
		assertThat(PhoneNumbers.normalizeOrNull("")).isNull();
		assertThat(PhoneNumbers.normalizeOrNull("02-123-4567")).isNull();
		assertThat(PhoneNumbers.normalizeOrNull("010-1234-56789")).isNull();
		assertThat(PhoneNumbers.normalizeOrNull("010abcd5678")).isNull();
		assertThat(PhoneNumbers.normalizeOrNull("+82 10 1234 5678")).isNull();
	}

	@Test
	@DisplayName("로그용 마스킹은 가운데 4자리를 가린다")
	void masks() {
		assertThat(PhoneNumbers.mask("01012345678")).isEqualTo("010****5678");
		assertThat(PhoneNumbers.mask(null)).isEqualTo("***");
	}
}
