package com.dobong.dobong.dashboard;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.dashboard.dto.RecentLogsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	// [F-0501] 홈 대시보드의 최근 기록. 로그인 필요 (SecurityConfig의 /api/** 규칙)
	@GetMapping("/api/enrollments/{enrollmentId}/recent-logs")
	public RecentLogsResponse recentLogs(
			@AuthenticationPrincipal AuthPrincipal principal,
			@PathVariable long enrollmentId,
			@RequestParam(defaultValue = "" + DashboardService.DEFAULT_SIZE) int size) {
		return dashboardService.getRecentLogs(principal.userId(), enrollmentId, size);
	}
}
