package com.dobong.dobong.progress;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.progress.dto.ProgressResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProgressController {

	private final CompletionService completionService;

	// [F-0601] 내 완주 진행 현황. 로그인 필요 (SecurityConfig의 /api/** 규칙)
	@GetMapping("/api/enrollments/{enrollmentId}/progress")
	public ProgressResponse myProgress(@PathVariable long enrollmentId, @AuthenticationPrincipal AuthPrincipal principal) {
		return completionService.getMyProgress(principal.userId(), enrollmentId);
	}
}
