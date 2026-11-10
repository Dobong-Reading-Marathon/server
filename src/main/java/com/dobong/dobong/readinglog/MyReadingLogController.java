package com.dobong.dobong.readinglog;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.readinglog.dto.MyReadingLogsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MyReadingLogController {

	private final MyReadingLogService myReadingLogService;

	// 내 독서기록 목록 (서평 본문, 글자 수 포함). 로그인 필요 (SecurityConfig의 /api/** 규칙)
	@GetMapping("/api/me/reading-logs")
	public MyReadingLogsResponse myReadingLogs(
			@AuthenticationPrincipal AuthPrincipal principal,
			@RequestParam(required = false) Long roundId) {
		return myReadingLogService.getMyReadingLogs(principal.userId(), roundId);
	}
}
