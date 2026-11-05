package com.dobong.dobong.calendar;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.dobong.dobong.calendar.dto.CalendarResponse.Day;
import com.dobong.dobong.calendar.dto.CalendarResponse.Log;
import com.dobong.dobong.calendar.dto.CalendarResponse.Summary;
import com.dobong.dobong.progress.PageRanges;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 한 달 치 독서기록으로 월 통계와 날짜별 목록을 만든다. DB에 접근하지 않는 순수 계산이다.
 *
 * <ul>
 *   <li>독서 일수: 집계에 들어가는 기록(counted)이 1건 이상 있는 날짜 수</li>
 *   <li>권수: 그 달에 집계에 들어가는 기록이 있는 책 수. 완독 권수가 아니다</li>
 *   <li>쪽수: 책별로 집계에 들어가는 기록의 쪽 범위를 합친 쪽 수의 합. 겹치는 쪽은 한 번만 센다 ({@link PageRanges})</li>
 * </ul>
 *
 * 월 쪽수를 모두 더하면 누적 쪽수보다 클 수 있다. 같은 쪽을 다른 달에 다시 읽으면 두 달에 모두 센다.
 */
final class CalendarCalculator {

	private CalendarCalculator() {
	}

	static Summary summarize(List<CalendarLogRow> rows) {
		List<CalendarLogRow> counted = rows.stream().filter(CalendarLogRow::counted).toList();
		int readDays = (int) counted.stream().map(CalendarLogRow::readDate).distinct().count();
		int books = (int) counted.stream().map(CalendarLogRow::enrollmentBookId).distinct().count();
		return new Summary(readDays, books, countPages(counted));
	}

	/** 기록이 있는 날짜만 날짜 순서로. 날짜 안의 기록 순서는 입력 순서(작성 순서)를 따른다 */
	static List<Day> days(List<CalendarLogRow> rows) {
		Map<LocalDate, List<CalendarLogRow>> byDate = rows.stream()
				.collect(groupingBy(CalendarLogRow::readDate, TreeMap::new, toList()));

		return byDate.entrySet().stream()
				.map(e -> new Day(
						e.getKey(),
						countPages(e.getValue().stream().filter(CalendarLogRow::counted).toList()),
						e.getValue().stream().map(CalendarCalculator::toLog).toList()))
				.toList();
	}

	/** 책별로 쪽 범위를 합쳐서 더한다 */
	private static int countPages(List<CalendarLogRow> countedRows) {
		Collection<List<CalendarLogRow>> byBook = countedRows.stream()
				.collect(groupingBy(CalendarLogRow::enrollmentBookId, LinkedHashMap::new, toList()))
				.values();
		return byBook.stream()
				.mapToInt(logs -> PageRanges.countReadPages(logs.stream().map(CalendarLogRow::pageRange).toList()))
				.sum();
	}

	private static Log toLog(CalendarLogRow row) {
		return new Log(row.readingLogId(), row.enrollmentBookId(), row.bookTitle(),
				row.startPage(), row.endPage(), row.status(), row.counted());
	}
}
