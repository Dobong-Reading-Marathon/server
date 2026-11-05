package com.dobong.dobong.progress;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.progress.dto.ProgressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완주 조건 판정 (F-0601). 누적 집계(F-0502) 결과를 코스 기준과 비교한다.
 * 판정 결과는 저장하지 않고 조회할 때마다 계산한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletionService {

	private final EnrollmentTargetRepository enrollmentTargetRepository;
	private final ReadingProgressService readingProgressService;

	/**
	 * 참가자 본인의 진행 현황.
	 * 본인 참가 신청이 아니면 404로 응답한다. 403으로 응답하면 남의 참가 신청 번호가 있다는 사실이 드러나기 때문.
	 */
	public ProgressResponse getMyProgress(long userId, long enrollmentId) {
		EnrollmentTarget target = enrollmentTargetRepository.findByIdAndUserId(enrollmentId, userId)
				.orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다."));
		return ProgressResponse.of(target, readingProgressService.getProgress(enrollmentId));
	}
}
