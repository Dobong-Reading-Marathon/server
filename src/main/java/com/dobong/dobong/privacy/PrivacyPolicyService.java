package com.dobong.dobong.privacy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.privacy.dto.PrivacyConsentResponse;
import com.dobong.dobong.privacy.dto.PrivacyPolicyResponse;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [F-0103] 개인정보 처리방침과 동의 내역
 *
 * 현재 버전 = 시행일이 오늘(한국 날짜) 이전인 버전 중 가장 큰 번호.
 */
@Service
@RequiredArgsConstructor
public class PrivacyPolicyService {

	private final PrivacyPolicyRepository policyRepository;
	private final PrivacyPolicySectionRepository sectionRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	// 현재 처리방침 전문. 없으면 빈 값 (서버 시작 검사에서 사용)
	@Transactional(readOnly = true)
	public Optional<PrivacyPolicyResponse> findCurrent() {
		return findCurrentPolicy().map(policy -> new PrivacyPolicyResponse(
				policy.getVersion(),
				policy.getEffectiveFrom(),
				sectionRepository.findByVersionOrderBySortOrderAsc(policy.getVersion()).stream()
						.map(s -> new PrivacyPolicyResponse.Section(s.getTitle(), s.getBody()))
						.toList()));
	}

	@Transactional(readOnly = true)
	public PrivacyPolicyResponse current() {
		return findCurrent().orElseThrow(PrivacyPolicyService::notFound);
	}

	// 현재 버전 번호. 회원가입에서 동의 버전으로 저장
	@Transactional(readOnly = true)
	public int currentVersion() {
		return findCurrentPolicy().orElseThrow(PrivacyPolicyService::notFound).getVersion();
	}

	@Transactional(readOnly = true)
	public PrivacyConsentResponse myConsent(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다."));
		return new PrivacyConsentResponse(
				user.getPrivacyAgreedVersion(),
				user.getPrivacyAgreedAt(),
				currentVersion(),
				user.getReminderAgreedAt() != null,
				user.getReminderAgreedAt());
	}

	private Optional<PrivacyPolicy> findCurrentPolicy() {
		return policyRepository.findFirstByEffectiveFromLessThanEqualOrderByVersionDesc(LocalDate.now(clock));
	}

	private static ApiException notFound() {
		return ApiException.notFound("PRIVACY_POLICY_NOT_FOUND", "개인정보 처리방침을 찾을 수 없습니다.");
	}
}
