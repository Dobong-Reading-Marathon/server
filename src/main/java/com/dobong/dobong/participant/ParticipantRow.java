package com.dobong.dobong.participant;

import com.dobong.dobong.progress.EnrollmentTarget;
import java.time.Instant;

/**
 * 참가자 한 명의 참가 신청과 코스 기준, 회원 정보. 참가자 목록(F-0801)과 사용자별 기록 조회(F-0802)가 쓴다.
 *
 * @param phone         숫자만 (01012345678)
 * @param loanMemberNo  대출회원번호. 없으면 null (사용자별 기록 조회에서만 보냄)
 * @param certificateNo 인증서 번호. 완주 승인(F-0803) 전에는 null
 * @param approvedAt    완주 승인 시각. 승인 전에는 null
 * @param enrolledAt    참가 신청 시각
 */
public record ParticipantRow(
		EnrollmentTarget target,
		long userId,
		String name,
		String phone,
		String loanMemberNo,
		String certificateNo,
		Instant approvedAt,
		Instant enrolledAt) {
}
