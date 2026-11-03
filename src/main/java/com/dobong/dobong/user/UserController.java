package com.dobong.dobong.user;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserRepository userRepository;

	// [F-0102] 내 정보. 앱을 열 때 로그인이 유지되어 있는지 확인하는 용도
	@GetMapping("/api/users/me")
	public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
		return userRepository.findById(principal.userId())
				.map(MeResponse::from)
				.orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다."));
	}
}
