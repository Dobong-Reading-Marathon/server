package com.dobong.dobong.calendar.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 내 독서달력 (F-0504). 한 달의 월 통계와, 기록이 있는 날짜별 기록 목록.
 *
 * @param days 기록이 있는 날짜만 날짜 순서로. 달력 칸은 화면에서 그린다
 */
public record CalendarResponse(
		long enrollmentId,
		long roundId,
		int year,
		int month,
		Summary summary,
		List<Day> days) {

	/**
	 * @param readDays 집계에 들어가는 기록이 있는 날짜 수
	 * @param books    그 달에 집계에 들어가는 기록이 있는 책 수 (완독 권수가 아님)
	 * @param pages    그 달에 읽은 쪽수. 책별로 쪽 범위를 합쳐서 셈
	 */
	public record Summary(int readDays, int books, int pages) {
	}

	/**
	 * @param pages 그 날 읽은 쪽수. 집계에 들어가는 기록만, 책별로 쪽 범위를 합쳐서 셈
	 * @param logs  그 날의 모든 기록 (집계에 안 들어가는 기록 포함). 작성 순서
	 */
	public record Day(LocalDate date, int pages, List<Log> logs) {
	}

	/**
	 * @param status  DRAFT / SUBMITTED / APPROVED / REVISION_REQUESTED / SAVED
	 * @param counted 누적 집계의 공통 조건을 만족해 집계에 들어가면 true
	 */
	public record Log(
			long readingLogId,
			long enrollmentBookId,
			String bookTitle,
			int startPage,
			int endPage,
			String status,
			boolean counted) {
	}
}
