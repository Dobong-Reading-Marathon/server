package com.dobong.dobong.auth;

/**
 * 요청을 보낸 참가자. 컨트롤러에서 @AuthenticationPrincipal AuthPrincipal principal 로 받음.
 *
 * 본인 데이터를 다룰 때는 요청 본문이나 주소의 id가 아니라 반드시 principal.userId()를 사용.
 * 그래야 남의 id를 넣어 남의 데이터를 보거나 바꾸는 공격(IDOR)을 막을 수 있음.
 *
 * @param userId    users.id
 * @param sessionId auth_sessions.id (로그아웃할 때 사용)
 */
public record AuthPrincipal(Long userId, Long sessionId) {
}
