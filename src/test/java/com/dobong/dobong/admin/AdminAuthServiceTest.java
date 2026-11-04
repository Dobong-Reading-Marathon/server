package com.dobong.dobong.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dobong.dobong.admin.dto.AdminLoginResponse;
import com.dobong.dobong.auth.AuthProperties;
import com.dobong.dobong.auth.SessionService.IssuedSession;
import com.dobong.dobong.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAuthServiceTest {

	private static final Instant NOW = Instant.parse("2027-01-05T01:00:00Z");
	private static final String LOGIN_ID = "librarian01";
	private static final String PASSWORD = "reading2027";
	private static final String IP = "203.0.113.10";

	private final AdminRepository adminRepository = mock(AdminRepository.class);
	// 테스트 속도를 위해 cost 4. 실제 서버는 기본값 10
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final AdminSessionService sessionService = mock(AdminSessionService.class);
	// 참가자와 같은 값 (5번 실패 15분 잠금). IP 실패 한도는 테스트를 위해 3으로
	private final AuthProperties authProperties = new AuthProperties(Duration.ofDays(30), Duration.ofDays(90),
			Duration.ofHours(12), 5, Duration.ofMinutes(15), 3, Duration.ofMinutes(10));
	private final AdminAuthService service = new AdminAuthService(adminRepository, encoder, sessionService,
			authProperties, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

	private Admin admin;

	@BeforeEach
	void setUp() {
		admin = admin(0, null);
		when(adminRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(admin));
		when(adminRepository.findByLoginId("nobody")).thenReturn(Optional.empty());
		when(sessionService.issue(1L)).thenReturn(new IssuedSession("token-1", NOW.plus(Duration.ofHours(8))));
	}

	private Admin admin(int failedCount, Instant lockedUntil) {
		Admin a = new Admin();
		ReflectionTestUtils.setField(a, "id", 1L);
		ReflectionTestUtils.setField(a, "loginId", LOGIN_ID);
		ReflectionTestUtils.setField(a, "passwordHash", encoder.encode(PASSWORD));
		ReflectionTestUtils.setField(a, "name", "관리자");
		ReflectionTestUtils.setField(a, "failedLoginCount", failedCount);
		ReflectionTestUtils.setField(a, "lockedUntil", lockedUntil);
		return a;
	}

	@Test
	@DisplayName("맞는 아이디·비밀번호면 토큰과 관리자 정보를 돌려준다 (응답에 로그인 아이디 없음)")
	void loginSuccess() {
		AdminLoginResponse res = service.login(LOGIN_ID, PASSWORD, IP);

		assertThat(res.token()).isEqualTo("token-1");
		assertThat(res.admin().id()).isEqualTo(1L);
		assertThat(res.admin().name()).isEqualTo("관리자");
		assertThat(res.toString()).doesNotContain("token-1").doesNotContain(LOGIN_ID);
		verify(adminRepository, never()).resetLoginFailures(anyLong());
	}

	@Test
	@DisplayName("전에 틀린 기록이 있으면 로그인 성공 시 초기화한다")
	void successResetsFailures() {
		when(adminRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(admin(2, null)));

		service.login(LOGIN_ID, PASSWORD, IP);

		verify(adminRepository).resetLoginFailures(1L);
	}

	@Test
	@DisplayName("없는 아이디와 틀린 비밀번호는 같은 401 응답")
	void sameErrorForUnknownAndWrong() {
		ApiException unknown = catchApi(() -> service.login("nobody", PASSWORD, IP));
		ApiException wrong = catchApi(() -> service.login(LOGIN_ID, "wrongpass1", IP));

		assertThat(unknown.getCode()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(wrong.getCode());
		assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
		verify(sessionService, never()).issue(anyLong());
	}

	@Test
	@DisplayName("틀리면 실패 1회 기록, 잠금 시각은 15분 뒤로 넘김 (5번째에 DB가 잠금)")
	void wrongPasswordRecordsFailure() {
		catchApi(() -> service.login(LOGIN_ID, "wrongpass1", IP));

		verify(adminRepository).recordLoginFailure(1L, NOW, 5, NOW.plus(Duration.ofMinutes(15)));
	}

	@Test
	@DisplayName("잠긴 계정은 맞는 비밀번호여도 429, 실패 횟수도 더 늘리지 않는다")
	void lockedAccount() {
		when(adminRepository.findByLoginId(LOGIN_ID))
				.thenReturn(Optional.of(admin(5, NOW.plus(Duration.ofMinutes(10)))));

		ApiException e = catchApi(() -> service.login(LOGIN_ID, PASSWORD, IP));

		assertThat(e.getCode()).isEqualTo("ACCOUNT_LOCKED");
		assertThat(e.getMessage()).contains("15분");
		verify(adminRepository, never()).recordLoginFailure(anyLong(), any(), anyInt(), any());
		verify(sessionService, never()).issue(anyLong());
	}

	@Test
	@DisplayName("잠금 시각이 지났으면 다시 로그인할 수 있다")
	void lockExpired() {
		when(adminRepository.findByLoginId(LOGIN_ID))
				.thenReturn(Optional.of(admin(5, NOW.minusSeconds(1))));

		assertThat(service.login(LOGIN_ID, PASSWORD, IP).token()).isEqualTo("token-1");
		verify(adminRepository).resetLoginFailures(1L);
	}

	@Test
	@DisplayName("같은 IP에서 한도만큼 실패하면 맞는 비밀번호여도 429")
	void ipLimit() {
		for (int i = 0; i < 3; i++) {
			catchApi(() -> service.login("nobody", PASSWORD, IP));
		}

		ApiException e = catchApi(() -> service.login(LOGIN_ID, PASSWORD, IP));

		assertThat(e.getCode()).isEqualTo("TOO_MANY_REQUESTS");
		// 다른 IP는 영향 없음
		assertThat(service.login(LOGIN_ID, PASSWORD, "198.51.100.7").token()).isEqualTo("token-1");
	}

	@Test
	@DisplayName("로그아웃하면 그 세션만 지운다")
	void logout() {
		service.logout(new AdminPrincipal(1L, 9L));

		verify(sessionService).revoke(eq(9L));
	}

	@Test
	@DisplayName("내 정보: 관리자가 없어졌으면 401")
	void meNotFound() {
		when(adminRepository.findById(2L)).thenReturn(Optional.empty());

		ApiException e = catchApi(() -> service.me(new AdminPrincipal(2L, 1L)));

		assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
	}

	private static ApiException catchApi(Runnable call) {
		Throwable t = catchThrowable(call::run);
		assertThat(t).isInstanceOf(ApiException.class);
		return (ApiException) t;
	}
}
