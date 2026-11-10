package com.dobong.dobong.privacy;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.privacy.dto.PrivacyConsentResponse;
import com.dobong.dobong.privacy.dto.PrivacyPolicyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PrivacyPolicyController {

	private final PrivacyPolicyService privacyPolicyService;

	// [F-0103] 개인정보 처리방침 전문. 로그인 없이 호출 가능 (가입 전에 봐야 함)
	@GetMapping("/api/privacy-policy")
	public PrivacyPolicyResponse current() {
		return privacyPolicyService.current();
	}

	// [F-0103] 내 개인정보 동의 내역. 로그인 필요
	@GetMapping("/api/users/me/privacy-consent")
	public PrivacyConsentResponse myConsent(@AuthenticationPrincipal AuthPrincipal principal) {
		return privacyPolicyService.myConsent(principal.userId());
	}
}
