package com.dobong.dobong.auth;

import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.auth.dto.SignupResponse;
import com.dobong.dobong.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	// [F-0101] 회원가입
	@PostMapping("/api/auth/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request, ClientIp.of(http)));
	}
}
