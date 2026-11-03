package com.dobong.dobong.auth;

import com.dobong.dobong.auth.dto.LoginRequest;
import com.dobong.dobong.auth.dto.LoginResponse;
import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	// [F-0101] 회원가입. 가입 직후 로그인 토큰까지 응답
	@PostMapping("/api/auth/signup")
	public ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request, ClientIp.of(http)));
	}

	// [F-0102] 로그인
	@PostMapping("/api/auth/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
		return authService.login(request.phone(), request.password(), rememberMe, ClientIp.of(http));
	}

	// [F-0102] 로그아웃. 로그인 필요 (SecurityConfig의 /api/** 규칙)
	@PostMapping("/api/auth/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
		authService.logout(principal);
		return ResponseEntity.noContent().build();
	}
}
