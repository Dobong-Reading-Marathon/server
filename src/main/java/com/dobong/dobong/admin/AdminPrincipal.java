package com.dobong.dobong.admin;

/**
 * 요청을 보낸 관리자. 관리자 컨트롤러에서 @AuthenticationPrincipal AdminPrincipal admin 으로 받음.
 *
 * /api/admin/** 주소에서만 채워짐. 참가자 API에서는 AuthPrincipal 사용.
 * 승인자(approved_by), 심사자(reviewed_by)를 저장할 때 adminId 사용.
 *
 * @param adminId   admins.id
 * @param sessionId admin_sessions.id (로그아웃할 때 사용)
 */
public record AdminPrincipal(Long adminId, Long sessionId) {
}
