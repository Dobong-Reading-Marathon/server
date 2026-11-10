package com.dobong.dobong.auth;

import com.dobong.dobong.common.ApiException;
import java.nio.charset.StandardCharsets;

/**
 * 새 비밀번호 규칙. 가입(F-0101)과 비밀번호 변경·재설정(F-0105)에서 같이 사용
 *
 * 8자 이상 64자 이하, 영문자와 숫자를 각각 1개 이상, 공백 불가, 전화번호 뒤 8자리 포함 불가
 * UTF-8 기준 72바이트 이하: BCrypt는 72바이트 뒤를 무시하므로 그보다 길면 거부
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 64;
	static final int BCRYPT_MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	// 규칙에 맞지 않으면 400 INVALID_PASSWORD. phone은 숫자만 남긴 값
	public static void validate(String password, String phone) {
		if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
			throw invalid("비밀번호는 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하로 입력해 주세요.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw invalid("비밀번호가 너무 깁니다. 한글은 영문보다 길게 계산되니 조금 줄여 주세요.");
		}
		if (password.chars().anyMatch(Character::isWhitespace)) {
			throw invalid("비밀번호에 공백을 넣을 수 없습니다.");
		}
		boolean hasLetter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
		boolean hasDigit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
		if (!hasLetter || !hasDigit) {
			throw invalid("비밀번호에 영문자와 숫자를 모두 넣어 주세요.");
		}
		if (phone != null && phone.length() >= 8 && password.contains(phone.substring(phone.length() - 8))) {
			throw invalid("비밀번호에 전화번호를 넣을 수 없습니다.");
		}
	}

	private static ApiException invalid(String message) {
		return ApiException.badRequest("INVALID_PASSWORD", message);
	}
}
