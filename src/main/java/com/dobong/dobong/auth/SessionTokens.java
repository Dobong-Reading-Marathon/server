package com.dobong.dobong.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 세션 토큰 만들기와 해시
 *
 * 토큰은 암호학적 난수 32바이트(256비트)라 추측 불가.
 * 그래서 비밀번호처럼 느린 BCrypt가 필요 없고, 요청마다 빠르게 비교할 수 있는 SHA-256 사용.
 */
final class SessionTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private SessionTokens() {
	}

	static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
		}
	}
}
