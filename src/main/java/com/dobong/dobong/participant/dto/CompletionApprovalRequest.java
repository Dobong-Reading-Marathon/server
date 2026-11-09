package com.dobong.dobong.participant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 완주 승인 요청 (F-0803)
 *
 * @param verifiedLoanCount 사서가 도서관 시스템에서 확인한 대출 권수. 앱은 대출 권수를 셀 수 없어서 사서가 넣는다
 * @param note              승인 사유. 완주 기준을 못 채웠는데 승인할 때 필수 (예: 오프라인 수첩 합산)
 */
public record CompletionApprovalRequest(
		@NotNull(message = "확인한 대출 권수를 입력해 주세요.")
		@Min(value = 0, message = "대출 권수는 0 이상이어야 합니다.")
		@Max(value = 10000, message = "대출 권수가 너무 큽니다.")
		Integer verifiedLoanCount,

		@Size(max = 255, message = "승인 사유는 255자 이하로 입력해 주세요.")
		String note) {
}
