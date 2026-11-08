package com.dobong.dobong.participant;

import com.dobong.dobong.progress.EnrollmentTarget;
import java.time.Instant;

/**
 * 참가자 목록의 한 줄. 참가 신청과 코스 기준, 회원 정보.
 *
 * @param phone         숫자만 (01012345678)
 * @param certificateNo 인증서 번호. 완주 승인(F-0803) 전에는 null
 * @param enrolledAt    참가 신청 시각
 */
public record ParticipantRow(
		EnrollmentTarget target,
		long userId,
		String name,
		String phone,
		String certificateNo,
		Instant enrolledAt) {
}
