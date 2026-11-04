package com.dobong.dobong.admin;

import com.dobong.dobong.admin.dto.AdminLoginRequest;
import com.dobong.dobong.admin.dto.AdminLoginResponse;
import com.dobong.dobong.admin.dto.AdminMeResponse;
import com.dobong.dobong.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	// [F-0104] 관리자 로그인. 로그인 없이 호출 가능 (SecurityConfig permitAll)
	@PostMapping("/api/admin/auth/login")
	public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest http) {
		return adminAuthService.login(request.loginId(), request.password(), ClientIp.of(http));
	}

	// [F-0104] 관리자 로그아웃. 관리자 토큰 필요
	@PostMapping("/api/admin/auth/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal admin) {
		adminAuthService.logout(admin);
		return ResponseEntity.noContent().build();
	}

	// [F-0104] 내 관리자 정보. 관리자 화면을 열 때 로그인 유지 확인
	@GetMapping("/api/admin/me")
	public AdminMeResponse me(@AuthenticationPrincipal AdminPrincipal admin) {
		return adminAuthService.me(admin);
	}
}
