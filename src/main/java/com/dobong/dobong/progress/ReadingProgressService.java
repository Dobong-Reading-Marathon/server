package com.dobong.dobong.progress;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 누적 자동 집계(F-0502). 대시보드(F-0501), 완주 판정(F-0601), 참가자 목록(F-0801), 명단 다운로드(F-0804)가 쓴다.
 *
 * 집계 값은 DB에 저장하지 않고 조회할 때마다 계산한다.
 * 나중에 느려지면 호출하는 쪽은 그대로 두고 이 클래스 안만 캐시나 저장 방식으로 바꾼다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadingProgressService {

	private final ReadingProgressRepository repository;

	/**
	 * 참가자 한 명의 집계. 집계 대상 기록이 없으면 모두 0인 결과를 돌려준다.
	 *
	 * 이 메서드는 enrollmentId가 누구의 것인지 확인하지 않는다.
	 * 참가자 요청에서 쓸 때는 호출하는 쪽에서 principal.userId()의 참가 신청인지 먼저 확인한다 (아니면 404).
	 */
	public ReadingProgress getProgress(long enrollmentId) {
		return ReadingProgressCalculator.calculate(repository.findByEnrollment(enrollmentId))
				.getOrDefault(enrollmentId, ReadingProgress.empty(enrollmentId));
	}

	/**
	 * 회차 전체 참가자의 집계 (키: 참가 신청 id).
	 * 집계 대상 기록이 없는 참가자는 들어 있지 않으므로, 쓰는 쪽에서 {@link ReadingProgress#empty(long)}로 채운다.
	 */
	public Map<Long, ReadingProgress> getProgressByRound(long roundId) {
		return ReadingProgressCalculator.calculate(repository.findByRound(roundId));
	}
}
