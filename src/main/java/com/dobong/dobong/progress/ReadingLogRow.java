package com.dobong.dobong.progress;

/**
 * 집계에 쓰는 독서기록 한 줄. 집계 대상 조건(소장도서, 회차 기간, 끝 쪽 ≤ 전체 쪽수)은 조회할 때 이미 걸러져 있다.
 *
 * @param approvedLongReview 사서가 승인했고 서평이 500자 이상이면 true
 */
public record ReadingLogRow(
		long enrollmentId,
		long enrollmentBookId,
		int totalPages,
		int startPage,
		int endPage,
		boolean approvedLongReview) {

	PageRange pageRange() {
		return new PageRange(startPage, endPage);
	}
}
