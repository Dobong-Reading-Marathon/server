package com.dobong.dobong.round;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.round.dto.CourseResponse;
import com.dobong.dobong.round.dto.RoundResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [F-0201] 현재 회차 고르기
 *
 * 위에서부터 먼저 맞는 회차 하나 (같은 단계에 여러 개면 연도·회차 번호가 큰 것)
 * 1. 오늘이 참가 신청 기간인 회차
 * 2. 오늘이 운영 기간인 회차 (신청은 끝났지만 행사 진행 중)
 * 3. 앞으로 시작할 회차 중 가장 가까운 것
 * 4. 지난 회차 중 가장 최근 것
 * "오늘"은 한국 날짜 (TimeConfig의 Clock)
 */
@Service
@RequiredArgsConstructor
public class RoundService {

	// 연도, 회차 번호가 큰 것이 먼저
	private static final Comparator<Round> LATEST_FIRST = Comparator
			.comparing(Round::getYear)
			.thenComparing(Round::getRoundNo)
			.reversed();

	private final RoundRepository roundRepository;
	private final CourseRepository courseRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public RoundResponse current() {
		LocalDate today = LocalDate.now(clock);
		Round round = findCurrentRound()
				.orElseThrow(() -> ApiException.notFound("ROUND_NOT_FOUND", "진행 중인 회차가 없습니다."));
		return toResponse(round, today);
	}

	// 현재 회차 (위 규칙 적용). 참가 신청(F-0205) 등 다른 기능에서도 같은 회차를 쓰기 위해 공개
	@Transactional(readOnly = true)
	public Optional<Round> findCurrentRound() {
		return pickCurrent(roundRepository.findAll(), LocalDate.now(clock));
	}

	static Optional<Round> pickCurrent(List<Round> rounds, LocalDate today) {
		Optional<Round> applying = rounds.stream().filter(r -> r.isApplyOpen(today)).min(LATEST_FIRST);
		if (applying.isPresent()) {
			return applying;
		}
		Optional<Round> running = rounds.stream().filter(r -> r.isInOperation(today)).min(LATEST_FIRST);
		if (running.isPresent()) {
			return running;
		}
		Optional<Round> upcoming = rounds.stream()
				.filter(r -> r.getStartDate().isAfter(today))
				.min(Comparator.comparing(Round::getStartDate).thenComparing(LATEST_FIRST));
		if (upcoming.isPresent()) {
			return upcoming;
		}
		return rounds.stream()
				.filter(r -> r.getEndDate().isBefore(today))
				.min(Comparator.comparing(Round::getEndDate).reversed().thenComparing(LATEST_FIRST));
	}

	private RoundResponse toResponse(Round r, LocalDate today) {
		List<CourseResponse> courses = courseRepository.findByRoundIdOrderBySortOrderAscIdAsc(r.getId()).stream()
				.map(CourseResponse::from)
				.toList();
		return new RoundResponse(r.getId(), r.getYear(), r.getRoundNo(), r.getStartDate(), r.getEndDate(),
				r.getApplyStart(), r.getApplyEnd(), r.isApplyOpen(today), courses);
	}
}
