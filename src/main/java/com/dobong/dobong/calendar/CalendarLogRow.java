package com.dobong.dobong.calendar;

import com.dobong.dobong.progress.PageRange;
import java.time.LocalDate;

/**
 * 독서달력에 쓰는 독서기록 한 줄.
 *
 * @param counted 누적 집계(F-0502)의 공통 조건(소장도서, 끝 쪽 ≤ 전체 쪽수, 회차 기간)을 만족하면 true
 */
public record CalendarLogRow(
		long readingLogId,
		long enrollmentBookId,
		String bookTitle,
		int startPage,
		int endPage,
		LocalDate readDate,
		String status,
		boolean counted) {

	PageRange pageRange() {
		return new PageRange(startPage, endPage);
	}
}
