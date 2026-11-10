package com.dobong.dobong.privacy;

import com.dobong.dobong.privacy.dto.PrivacyPolicyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 서버 시작 시 처리방침에 자리표시 문안이 남아 있는지 검사 (F-0103)
 *
 * 운영(prod): 남아 있거나 처리방침이 없으면 서버 시작 실패. 목적·보관 기간 고지 없이 개인정보를 받는 것을 막음
 * 그 밖(local, dev): 경고 로그만 남김
 * 도서관 문안을 받으면 새 Flyway 마이그레이션으로 교체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceholderPolicyGuard implements ApplicationRunner {

	static final String PLACEHOLDER = "[도서관 제공 문안]";

	private final PrivacyPolicyService privacyPolicyService;
	private final Environment environment;

	@Override
	public void run(ApplicationArguments args) {
		boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
		PrivacyPolicyResponse policy = privacyPolicyService.findCurrent().orElse(null);

		if (policy == null) {
			fail(prod, "시행 중인 개인정보 처리방침이 없습니다.");
			return;
		}
		boolean hasPlaceholder = policy.sections().stream()
				.anyMatch(s -> s.title().contains(PLACEHOLDER) || s.body().contains(PLACEHOLDER));
		if (hasPlaceholder) {
			fail(prod, "개인정보 처리방침 " + policy.version() + "번에 " + PLACEHOLDER
					+ "이 남아 있습니다. 도서관 문안으로 교체해야 합니다.");
		}
	}

	private static void fail(boolean prod, String message) {
		if (prod) {
			throw new IllegalStateException(message + " 운영 서버를 시작할 수 없습니다.");
		}
		log.warn("{} (운영에서는 서버 시작이 막힘)", message);
	}
}
