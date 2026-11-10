package com.dobong.dobong.readinglog;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.Enrollment;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.MyReadingLogsResponse;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 독서기록 목록 (내서재 카드형·수첩형).
 *
 * 참가 신청은 로그인한 회원(userId)과 회차로 찾는다. 요청으로 참가 신청 id를 받지 않으므로 남의 기록을 볼 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyReadingLogService {

	private final MyReadingLogRepository myReadingLogRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final RoundService roundService;

	/** @param roundId 회차. 비우면 현재 회차 (F-0201 규칙) */
	public MyReadingLogsResponse getMyReadingLogs(long userId, Long roundId) {
		Enrollment enrollment = findEnrollment(userId, roundId)
				.orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다."));
		return new MyReadingLogsResponse(
				enrollment.getId(),
				enrollment.getRound().getId(),
				myReadingLogRepository.findAll(enrollment.getId()));
	}

	private Optional<Enrollment> findEnrollment(long userId, Long roundId) {
		if (roundId != null) {
			return enrollmentRepository.findByUserIdAndRoundId(userId, roundId);
		}
		return roundService.findCurrentRound()
				.map(Round::getId)
				.flatMap(currentRoundId -> enrollmentRepository.findByUserIdAndRoundId(userId, currentRoundId));
	}
}
