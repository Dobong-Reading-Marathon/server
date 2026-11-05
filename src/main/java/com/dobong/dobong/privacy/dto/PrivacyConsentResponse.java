package com.dobong.dobong.privacy.dto;

import java.time.Instant;

/**
 * 내 개인정보 동의 내역
 *
 * @param privacyPolicyVersion 내가 동의한 처리방침 버전
 * @param privacyAgreedAt      동의 일시 (회원가입 시각)
 * @param latestPolicyVersion  현재 처리방침 버전. privacyPolicyVersion보다 크면 개정된 처리방침이 있음
 * @param reminderAgreedAt     독서 독려 알림 동의 일시. 동의 안 했으면 null
 */
public record PrivacyConsentResponse(
		int privacyPolicyVersion,
		Instant privacyAgreedAt,
		int latestPolicyVersion,
		boolean reminderAgreed,
		Instant reminderAgreedAt) {
}
