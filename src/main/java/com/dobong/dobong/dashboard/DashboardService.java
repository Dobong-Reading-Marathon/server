package com.dobong.dobong.dashboard;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.dashboard.dto.RecentLogsResponse;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 대시보드 (F-0501). 진행 현황은 CompletionService(F-0601)가 맡고, 여기서는 최근 기록을 돌려준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

	public static final int DEFAULT_SIZE = 5;
	public static final int MAX_SIZE = 20;

	private final EnrollmentRepository enrollmentRepository;
	private final RecentLogRepository recentLogRepository;

	/**
	 * 본인 참가 신청의 최근 기록 size건.
	 * 본인 참가 신청이 아니면 404로 응답한다. 403으로 응답하면 남의 참가 신청 번호가 있다는 사실이 드러나기 때문.
	 */
	public RecentLogsResponse getRecentLogs(long userId, long enrollmentId, int size) {
		if (size < 1 || size > MAX_SIZE) {
			throw ApiException.badRequest("INVALID_INPUT", "입력값을 확인해 주세요.");
		}
		if (!enrollmentRepository.existsByIdAndUserId(enrollmentId, userId)) {
			throw ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다.");
		}
		return new RecentLogsResponse(enrollmentId, recentLogRepository.findRecent(enrollmentId, size));
	}
}
