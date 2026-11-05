package com.dobong.dobong.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 홈 대시보드의 최근 기록 (F-0501). 진행률, 누적 쪽수, 서평 편수, 남은 목표는 진행 현황 API(F-0601)에서 받는다.
 *
 * @param logs 읽은 날짜가 늦은 순서. 기록이 없으면 빈 목록
 */
public record RecentLogsResponse(long enrollmentId, List<RecentLog> logs) {

	/**
	 * @param status       DRAFT / SUBMITTED / APPROVED / REVISION_REQUESTED
	 * @param quote        마음에 드는 글귀. 없으면 null
	 * @param reviewLength 서평 글자 수 (공백·줄바꿈 포함, 이모지 1자). 서평이 없으면 0
	 * @param counted      누적 집계의 공통 조건을 만족해 집계에 들어가면 true
	 */
	public record RecentLog(
			long readingLogId,
			long enrollmentBookId,
			String bookTitle,
			int startPage,
			int endPage,
			LocalDate readDate,
			String status,
			String quote,
			int reviewLength,
			boolean counted) {
	}
}
