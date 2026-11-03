package com.dobong.dobong.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.round.dto.RoundResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundServiceTest {

	private static final Round R2026 = Round.create(2026, 1,
			LocalDate.of(2026, 1, 5), LocalDate.of(2026, 11, 30),
			LocalDate.of(2026, 2, 2), LocalDate.of(2026, 6, 30));
	private static final Round R2027 = Round.create(2027, 1,
			LocalDate.of(2027, 1, 5), LocalDate.of(2027, 11, 30),
			LocalDate.of(2027, 2, 2), LocalDate.of(2027, 6, 30));
	private static final List<Round> ROUNDS = List.of(R2026, R2027);

	private static Round pick(LocalDate today) {
		return RoundService.pickCurrent(ROUNDS, today).orElseThrow();
	}

	@Test
	@DisplayName("1. 신청 기간이면 그 회차")
	void applying() {
		assertThat(pick(LocalDate.of(2027, 3, 1))).isSameAs(R2027);
	}

	@Test
	@DisplayName("2. 신청은 끝났지만 운영 기간이면 그 회차")
	void running() {
		assertThat(pick(LocalDate.of(2027, 8, 1))).isSameAs(R2027);
		assertThat(pick(LocalDate.of(2027, 1, 10))).isSameAs(R2027); // 운영은 시작, 신청은 아직
	}

	@Test
	@DisplayName("3. 행사 전이면 가장 가까운 다음 회차 (2026년 12월 → 2027 회차)")
	void upcoming() {
		assertThat(pick(LocalDate.of(2026, 12, 15))).isSameAs(R2027);
	}

	@Test
	@DisplayName("4. 다 끝났으면 가장 최근에 끝난 회차")
	void past() {
		assertThat(pick(LocalDate.of(2027, 12, 15))).isSameAs(R2027);
	}

	@Test
	@DisplayName("같은 단계에 여러 개면 연도가 큰 회차 (시연용 2026/99와 2027이 겹칠 때)")
	void latestWinsOnOverlap() {
		Round demo = Round.create(2026, 99, LocalDate.of(2026, 10, 27), LocalDate.of(2027, 11, 3),
				LocalDate.of(2026, 10, 27), LocalDate.of(2027, 11, 3));

		assertThat(RoundService.pickCurrent(List.of(demo, R2027), LocalDate.of(2027, 3, 1)).orElseThrow())
				.isSameAs(R2027);
		assertThat(RoundService.pickCurrent(List.of(demo, R2027), LocalDate.of(2026, 11, 3)).orElseThrow())
				.isSameAs(demo);
	}

	@Test
	@DisplayName("회차가 하나도 없으면 404 ROUND_NOT_FOUND")
	void noRounds() {
		RoundRepository roundRepository = mock(RoundRepository.class);
		when(roundRepository.findAll()).thenReturn(List.of());
		RoundService service = new RoundService(roundRepository, mock(CourseRepository.class), Clock.systemUTC());

		assertThatThrownBy(service::current)
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("ROUND_NOT_FOUND");
	}

	@Test
	@DisplayName("한국 날짜 기준: UTC로는 2월 1일 밤이어도 한국이 2월 2일이면 신청 기간")
	void usesKoreanDate() {
		RoundRepository roundRepository = mock(RoundRepository.class);
		CourseRepository courseRepository = mock(CourseRepository.class);
		when(roundRepository.findAll()).thenReturn(List.of(R2027));
		when(courseRepository.findByRoundIdOrderBySortOrderAscIdAsc(any()))
				.thenReturn(List.of(Course.create(R2027, "풀코스(개인)", false, 42195, 120, 15, 4)));
		// UTC 2027-02-01 15:30 = 한국 2027-02-02 00:30
		Clock clock = Clock.fixed(Instant.parse("2027-02-01T15:30:00Z"), ZoneId.of("Asia/Seoul"));

		RoundResponse res = new RoundService(roundRepository, courseRepository, clock).current();

		assertThat(res.applyOpen()).isTrue();
		assertThat(res.year()).isEqualTo(2027);
		assertThat(res.courses()).hasSize(1);
		assertThat(res.courses().get(0).distanceKm()).isEqualTo(42.195);
	}
}
