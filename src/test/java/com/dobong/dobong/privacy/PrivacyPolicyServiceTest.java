package com.dobong.dobong.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.privacy.dto.PrivacyConsentResponse;
import com.dobong.dobong.privacy.dto.PrivacyPolicyResponse;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PrivacyPolicyServiceTest {

	// 한국 시간 2026-11-01 00:30 (UTC로는 10월 31일). 날짜를 한국 기준으로 보는지 확인
	private static final Instant NOW = Instant.parse("2026-10-31T15:30:00Z");
	private static final LocalDate TODAY_KST = LocalDate.of(2026, 11, 1);

	private final PrivacyPolicyRepository policyRepository = mock(PrivacyPolicyRepository.class);
	private final PrivacyPolicySectionRepository sectionRepository = mock(PrivacyPolicySectionRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final PrivacyPolicyService service = new PrivacyPolicyService(policyRepository, sectionRepository,
			userRepository, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

	private static PrivacyPolicy policy(int version, LocalDate effectiveFrom) {
		PrivacyPolicy p = new PrivacyPolicy();
		ReflectionTestUtils.setField(p, "version", version);
		ReflectionTestUtils.setField(p, "effectiveFrom", effectiveFrom);
		return p;
	}

	private static PrivacyPolicySection section(int version, int order, String title, String body) {
		PrivacyPolicySection s = new PrivacyPolicySection();
		ReflectionTestUtils.setField(s, "version", version);
		ReflectionTestUtils.setField(s, "sortOrder", order);
		ReflectionTestUtils.setField(s, "title", title);
		ReflectionTestUtils.setField(s, "body", body);
		return s;
	}

	@Test
	@DisplayName("한국 날짜 기준으로 시행된 현재 버전과 절을 순서대로 돌려준다")
	void current() {
		when(policyRepository.findFirstByEffectiveFromLessThanEqualOrderByVersionDesc(TODAY_KST))
				.thenReturn(Optional.of(policy(1, TODAY_KST)));
		when(sectionRepository.findByVersionOrderBySortOrderAsc(1)).thenReturn(List.of(
				section(1, 1, "1. 수집 항목", "필수: 이름"),
				section(1, 2, "2. 수집·이용 목적", "독서마라톤 운영")));

		PrivacyPolicyResponse res = service.current();

		assertThat(res.version()).isEqualTo(1);
		assertThat(res.effectiveFrom()).isEqualTo(TODAY_KST);
		assertThat(res.sections()).extracting(PrivacyPolicyResponse.Section::title)
				.containsExactly("1. 수집 항목", "2. 수집·이용 목적");
		assertThat(service.currentVersion()).isEqualTo(1);
	}

	@Test
	@DisplayName("시행 중인 처리방침이 없으면 404")
	void notFound() {
		when(policyRepository.findFirstByEffectiveFromLessThanEqualOrderByVersionDesc(any()))
				.thenReturn(Optional.empty());

		assertThatThrownBy(service::current)
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "PRIVACY_POLICY_NOT_FOUND");
		assertThatThrownBy(service::currentVersion)
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "PRIVACY_POLICY_NOT_FOUND");
	}

	@Test
	@DisplayName("내 동의 내역: 동의한 버전과 일시, 현재 버전, 알림 동의를 돌려준다")
	void myConsent() {
		User user = User.create("홍길동", "01012345678", "hash", null, true, 1, NOW);
		when(userRepository.findById(7L)).thenReturn(Optional.of(user));
		when(policyRepository.findFirstByEffectiveFromLessThanEqualOrderByVersionDesc(TODAY_KST))
				.thenReturn(Optional.of(policy(2, TODAY_KST)));

		PrivacyConsentResponse res = service.myConsent(7L);

		assertThat(res.privacyPolicyVersion()).isEqualTo(1);
		assertThat(res.privacyAgreedAt()).isEqualTo(NOW);
		assertThat(res.latestPolicyVersion()).isEqualTo(2);
		assertThat(res.reminderAgreed()).isTrue();
		assertThat(res.reminderAgreedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("내 동의 내역: 회원이 없으면 401")
	void myConsentUserMissing() {
		when(userRepository.findById(8L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.myConsent(8L))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "UNAUTHORIZED");
	}
}
