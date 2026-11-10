package com.dobong.dobong.admin.dto;

import java.time.Instant;

/**
 * 관리자 로그인 성공 응답
 *
 * 프론트는 token을 저장했다가 관리자 API 요청마다 "Authorization: Bearer {token}" 헤더로 보냄.
 *
 * @param expiresAt 만료 시각 (로그인 8시간 뒤, 연장 없음)
 * @param admin     /api/admin/me 와 같은 모양
 */
public record AdminLoginResponse(String token, Instant expiresAt, AdminMeResponse admin) {

	@Override
	public String toString() {
		return "AdminLoginResponse[token=***, expiresAt=" + expiresAt + "]";
	}
}
