package com.dobong.dobong.privacy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dobong.dobong.privacy.dto.PrivacyPolicyResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PlaceholderPolicyGuardTest {

	private final PrivacyPolicyService service = mock(PrivacyPolicyService.class);

	private static PrivacyPolicyResponse policy(String body) {
		return new PrivacyPolicyResponse(1, LocalDate.of(2026, 11, 1), List.of(
				new PrivacyPolicyResponse.Section("1. 수집 항목", "필수: 이름"),
				new PrivacyPolicyResponse.Section("2. 수집·이용 목적", body)));
	}

	private void run(String profile) {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles(profile);
		new PlaceholderPolicyGuard(service, env).run(null);
	}

	@Test
	@DisplayName("운영(prod)에서 자리표시 문안이 남아 있으면 서버 시작 실패")
	void prodWithPlaceholderFails() {
		when(service.findCurrent()).thenReturn(Optional.of(policy(PlaceholderPolicyGuard.PLACEHOLDER)));

		assertThatThrownBy(() -> run("prod")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("운영(prod)에서 처리방침이 없어도 서버 시작 실패")
	void prodWithoutPolicyFails() {
		when(service.findCurrent()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> run("prod")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("local에서는 자리표시 문안이 있어도 경고만 하고 시작")
	void localOnlyWarns() {
		when(service.findCurrent()).thenReturn(Optional.of(policy(PlaceholderPolicyGuard.PLACEHOLDER)));

		assertThatCode(() -> run("local")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("실제 문안이면 운영에서도 시작")
	void prodWithRealTextStarts() {
		when(service.findCurrent()).thenReturn(Optional.of(policy("독서마라톤 참가 관리와 완주 인증")));

		assertThatCode(() -> run("prod")).doesNotThrowAnyException();
	}
}
