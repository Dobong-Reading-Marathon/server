package com.dobong.dobong.progress;

/**
 * 독서기록 하나의 쪽 범위. 양 끝을 포함한다 (1–120 = 120쪽).
 */
public record PageRange(int start, int end) {

	public PageRange {
		if (start < 1 || end < start) {
			throw new IllegalArgumentException("잘못된 쪽 범위입니다: " + start + "–" + end);
		}
	}
}
