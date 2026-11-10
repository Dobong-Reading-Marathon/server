package com.dobong.dobong.round;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundTest {

	private static final Round ROUND_2027 = Round.create(2027, 1,
			LocalDate.of(2027, 1, 5), LocalDate.of(2027, 11, 30),
			LocalDate.of(2027, 2, 2), LocalDate.of(2027, 6, 30));

	@Test
	@DisplayName("신청 기간: 시작일과 마감일 당일은 포함, 하루 전과 하루 뒤는 제외")
	void applyPeriodBoundaries() {
		assertThat(ROUND_2027.isApplyOpen(LocalDate.of(2027, 2, 1))).isFalse();
		assertThat(ROUND_2027.isApplyOpen(LocalDate.of(2027, 2, 2))).isTrue();
		assertThat(ROUND_2027.isApplyOpen(LocalDate.of(2027, 6, 30))).isTrue();
		assertThat(ROUND_2027.isApplyOpen(LocalDate.of(2027, 7, 1))).isFalse();
	}

	@Test
	@DisplayName("운영 기간: 시작일과 종료일 당일은 포함, 하루 전과 하루 뒤는 제외")
	void operationBoundaries() {
		assertThat(ROUND_2027.isInOperation(LocalDate.of(2027, 1, 4))).isFalse();
		assertThat(ROUND_2027.isInOperation(LocalDate.of(2027, 1, 5))).isTrue();
		assertThat(ROUND_2027.isInOperation(LocalDate.of(2027, 11, 30))).isTrue();
		assertThat(ROUND_2027.isInOperation(LocalDate.of(2027, 12, 1))).isFalse();
	}

	@Test
	@DisplayName("신청 기간이 비어 있으면 운영 기간을 신청 기간으로 본다")
	void applyPeriodFallsBackToOperation() {
		Round round = Round.create(2027, 2, LocalDate.of(2027, 1, 5), LocalDate.of(2027, 11, 30), null, null);

		assertThat(round.isApplyOpen(LocalDate.of(2027, 1, 5))).isTrue();
		assertThat(round.isApplyOpen(LocalDate.of(2027, 11, 30))).isTrue();
		assertThat(round.isApplyOpen(LocalDate.of(2027, 12, 1))).isFalse();
	}

	@Test
	@DisplayName("화면 거리는 완주 쪽수 ÷ 1,000")
	void distanceKm() {
		assertThat(Course.create(ROUND_2027, "풀코스(개인)", false, 42195, 120, 15, 4).distanceKm()).isEqualTo(42.195);
		assertThat(Course.create(ROUND_2027, "걷기코스", false, 3500, 10, 3, 1).distanceKm()).isEqualTo(3.5);
	}
}
