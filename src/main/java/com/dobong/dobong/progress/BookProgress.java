package com.dobong.dobong.progress;

/**
 * 등록 도서 한 권의 집계 결과. 참가자 누적 집계({@link ReadingProgress})는 이 값들의 합이다.
 *
 * @param readPages     읽은 쪽수. 쪽 범위를 합쳐 겹치는 쪽은 한 번만 셈
 * @param completed     완독 (읽은 쪽수 = 전체 쪽수)
 * @param reviewCounted 승인된 500자 이상 서평이 있어 서평 1편으로 셈
 */
public record BookProgress(long enrollmentBookId, int readPages, boolean completed, boolean reviewCounted) {

	/** 집계에 들어가는 기록이 없는 책 (소장도서가 아니거나 기록이 없음) */
	public static BookProgress empty(long enrollmentBookId) {
		return new BookProgress(enrollmentBookId, 0, false, false);
	}
}
