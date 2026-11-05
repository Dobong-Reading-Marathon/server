package com.dobong.dobong.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.dobong.dobong.calendar.dto.CalendarResponse.Day;
import com.dobong.dobong.calendar.dto.CalendarResponse.Summary;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendarCalculatorTest {

	private static final LocalDate MAR_2 = LocalDate.of(2027, 3, 2);
	private static final LocalDate MAR_5 = LocalDate.of(2027, 3, 5);

	private static long nextId = 1;

	private static CalendarLogRow log(long bookId, int start, int end, LocalDate date, boolean counted) {
		return new CalendarLogRow(nextId++, bookId, "책" + bookId, start, end, date, "SUBMITTED", counted);
	}

	@Test
	@DisplayName("같은 책의 겹치는 범위는 한 번만 세고, 책마다 따로 더한다")
	void pagesMergedPerBook() {
		Summary summary = CalendarCalculator.summarize(List.of(
				log(1, 1, 120, MAR_2, true),
				log(1, 100, 250, MAR_5, true),
				log(2, 1, 50, MAR_5, true)));

		assertThat(summary).isEqualTo(new Summary(2, 2, 300));
	}

	@Test
	@DisplayName("집계에 안 들어가는 기록은 독서 일수, 권수, 쪽수에 넣지 않는다")
	void excludesNotCounted() {
		Summary summary = CalendarCalculator.summarize(List.of(
				log(1, 1, 100, MAR_2, true),
				log(2, 1, 300, MAR_5, false)));

		assertThat(summary).isEqualTo(new Summary(1, 1, 100));
	}

	@Test
	@DisplayName("날짜별 목록에는 집계에 안 들어가는 기록도 넣고, 그 날 쪽수는 집계 대상만 센다")
	void daysIncludeAllLogs() {
		List<Day> days = CalendarCalculator.days(List.of(
				log(1, 1, 100, MAR_2, true),
				log(1, 50, 150, MAR_2, true),
				log(2, 1, 300, MAR_2, false)));

		assertThat(days).hasSize(1);
		assertThat(days.get(0).pages()).isEqualTo(150);
		assertThat(days.get(0).logs()).hasSize(3);
		assertThat(days.get(0).logs().get(2).counted()).isFalse();
	}

	@Test
	@DisplayName("기록이 있는 날짜만 날짜 순서로 돌려준다")
	void daysSortedByDate() {
		List<Day> days = CalendarCalculator.days(List.of(
				log(1, 1, 10, MAR_5, true),
				log(1, 11, 20, MAR_2, true)));

		assertThat(days).extracting(Day::date).containsExactly(MAR_2, MAR_5);
	}

	@Test
	@DisplayName("집계에 안 들어가는 기록만 있는 날도 목록에는 나오고, 독서 일수에는 안 센다")
	void dayWithOnlyNotCounted() {
		List<CalendarLogRow> rows = List.of(log(1, 1, 10, MAR_2, false));

		assertThat(CalendarCalculator.days(rows)).singleElement()
				.satisfies(day -> assertThat(day.pages()).isZero());
		assertThat(CalendarCalculator.summarize(rows).readDays()).isZero();
	}

	@Test
	@DisplayName("기록이 없으면 통계는 0이고 목록은 비어 있다")
	void empty() {
		assertThat(CalendarCalculator.summarize(List.of())).isEqualTo(new Summary(0, 0, 0));
		assertThat(CalendarCalculator.days(List.of())).isEmpty();
	}
}
