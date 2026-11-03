package com.dobong.dobong.user;

/**
 * 로그인한 참가자 본인 정보
 * 화면에서 쓰는 값만 넣음. 전화번호, 대출회원번호는 보여 주는 화면이 없어서 뺌.
 *
 * @param mustChangePassword true면 임시 비밀번호 상태 (F-0105)
 * @param reminderAgreed 독서 독려 알림 수신 동의 여부
 */
public record MeResponse(Long id, String name, boolean mustChangePassword, boolean reminderAgreed) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getName(), user.isMustChangePassword(),
				user.getReminderAgreedAt() != null);
	}
}
