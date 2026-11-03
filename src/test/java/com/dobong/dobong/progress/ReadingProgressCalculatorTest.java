package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadingProgressCalculatorTest {

	private static ReadingLogRow log(long enrollmentId, long bookId, int totalPages, int start, int end, boolean approvedLongReview) {
		return new ReadingLogRow(enrollmentId, bookId, totalPages, start, end, approvedLongReview);
	}

	@Test
	@DisplayName("책 2권의 쪽수, 완독 권수, 서평 편수를 합산한다")
	void sumsBooks() {
		Map<Long, ReadingProgress> result = ReadingProgressCalculator.calculate(List.of(
				log(1, 10, 300, 1, 150, false),
				log(1, 10, 300, 151, 300, true),
				log(1, 20, 200, 1, 120, false)));

		assertThat(result.get(1L)).isEqualTo(new ReadingProgress(1, 420, 1, 1));
	}

	@Test
	@DisplayName("빈 구간이 있으면 완독이 아니다")
	void notCompletedWithGap() {
		Map<Long, ReadingProgress> result = ReadingProgressCalculator.calculate(List.of(
				log(1, 10, 300, 1, 100, false),
				log(1, 10, 300, 200, 300, false)));

		assertThat(result.get(1L)).isEqualTo(new ReadingProgress(1, 201, 0, 0));
	}

	@Test
	@DisplayName("같은 책의 승인된 500자 서평이 여러 개여도 1편으로 센다")
	void oneReviewPerBook() {
		Map<Long, ReadingProgress> result = ReadingProgressCalculator.calculate(List.of(
				log(1, 10, 300, 1, 100, true),
				log(1, 10, 300, 101, 200, true)));

		assertThat(result.get(1L).reviewCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("참가자별로 따로 합산한다")
	void separatesEnrollments() {
		Map<Long, ReadingProgress> result = ReadingProgressCalculator.calculate(List.of(
				log(1, 10, 300, 1, 300, true),
				log(2, 30, 100, 1, 50, false)));

		assertThat(result).containsOnlyKeys(1L, 2L);
		assertThat(result.get(1L)).isEqualTo(new ReadingProgress(1, 300, 1, 1));
		assertThat(result.get(2L)).isEqualTo(new ReadingProgress(2, 50, 0, 0));
	}

	@Test
	@DisplayName("km는 1,000쪽을 1km로 환산한다")
	void convertsToKm() {
		assertThat(new ReadingProgress(1, 42195, 0, 0).readKm()).isEqualTo(42.195);
	}

	@Test
	@DisplayName("기록이 없으면 결과가 비어 있다")
	void emptyRows() {
		assertThat(ReadingProgressCalculator.calculate(List.of())).isEmpty();
	}
}
