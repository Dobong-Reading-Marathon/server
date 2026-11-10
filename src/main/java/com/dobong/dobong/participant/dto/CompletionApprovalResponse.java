package com.dobong.dobong.participant.dto;

import com.dobong.dobong.progress.CompletionStatus;
import java.time.Instant;

/**
 * 완주 승인 결과 (F-0803)
 *
 * @param status        항상 COMPLETED
 * @param certificateNo 새로 붙인 인증서 번호
 * @param approvalNote  승인 사유. 없으면 null
 */
public record CompletionApprovalResponse(
		long enrollmentId,
		CompletionStatus status,
		String certificateNo,
		int verifiedLoanCount,
		String approvalNote,
		Instant approvedAt) {
}
