package com.dobong.dobong.enrollment;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.enrollment.dto.EnrollRequest;
import com.dobong.dobong.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

	private final EnrollmentService enrollmentService;

	// [F-0205] 회차 참가 신청. 로그인 필요
	@PostMapping("/api/enrollments")
	public ResponseEntity<EnrollmentResponse> enroll(@AuthenticationPrincipal AuthPrincipal principal,
			@Valid @RequestBody EnrollRequest request) {
		EnrollmentResponse body = enrollmentService.enroll(principal.userId(), request.courseId());
		return ResponseEntity.status(HttpStatus.CREATED).body(body);
	}

	// [F-0205] 현재 회차의 내 참가. 신청 안 했으면 204 (본문 없음)
	@GetMapping("/api/enrollments/current")
	public ResponseEntity<EnrollmentResponse> current(@AuthenticationPrincipal AuthPrincipal principal) {
		return enrollmentService.current(principal.userId())
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
