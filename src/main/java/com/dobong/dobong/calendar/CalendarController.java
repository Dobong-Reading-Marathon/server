package com.dobong.dobong.calendar;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.calendar.dto.CalendarResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CalendarController {

	private final CalendarService calendarService;

	// [F-0504] 내 독서달력. 로그인 필요 (SecurityConfig의 /api/** 규칙)
	@GetMapping("/api/me/calendar")
	public CalendarResponse myCalendar(
			@AuthenticationPrincipal AuthPrincipal principal,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Long roundId) {
		return calendarService.getMyCalendar(principal.userId(), year, month, roundId);
	}
}
