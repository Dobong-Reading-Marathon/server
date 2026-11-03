package com.dobong.dobong.auth.dto;

import com.dobong.dobong.user.MeResponse;
import java.time.Instant;

/**
 * 로그인, 회원가입 성공 응답
 *
 * 프론트는 token을 저장했다가 이후 요청마다 "Authorization: Bearer {token}" 헤더로 보냄.
 * 서버에는 해시만 있어서 잃어버리면 다시 로그인해야 함.
 *
 * @param expiresAt 현재 만료 시각 (사용하면 연장될 수 있어 표시용)
 * @param user      /api/users/me 와 같은 모양
 */
public record LoginResponse(String token, Instant expiresAt, MeResponse user) {

	@Override
	public String toString() {
		return "LoginResponse[token=***, expiresAt=" + expiresAt + "]";
	}
}
