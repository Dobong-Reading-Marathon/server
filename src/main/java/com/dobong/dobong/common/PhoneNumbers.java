package com.dobong.dobong.common;

import java.util.regex.Pattern;

/**
 * 휴대폰 번호를 숫자만 남긴 형태(01012345678)
 *
 * 가입과 로그인에서 반드시 이 함수를 같이 써야 함.
 * 그래야 "010-1234-5678"과 "01012345678"이 다른 계정이 되지 않음.
 */
public final class PhoneNumbers {

	// 010, 011, 016, 017, 018, 019 + 7~8자리
	private static final Pattern MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");

	private PhoneNumbers() {
	}

	// 하이픈, 공백, 점을 지운 뒤 휴대폰 번호 형식이면 숫자만 돌려줌. 아니면 null
	public static String normalizeOrNull(String raw) {
		if (raw == null) {
			return null;
		}
		String digits = raw.replaceAll("[\\s.\\-]", "");
		return MOBILE.matcher(digits).matches() ? digits : null;
	}

	// 로그용 가리기 (01012345678 → 010****5678)
	public static String mask(String phone) {
		if (phone == null || phone.length() < 8) {
			return "***";
		}
		return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
	}
}
