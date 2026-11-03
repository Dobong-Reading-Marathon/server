package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dobong.dobong.auth.dto.LoginResponse;
import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2027-01-05T01:00:00Z");
	private static final String PHONE = "01012345678";
	private static final String PASSWORD = "reading2027";
	private static final String IP = "203.0.113.10";

	private final UserRepository userRepository = mock(UserRepository.class);
	// 테스트 속도를 위해 cost 4. 실제 서버는 기본값 10
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final SessionService sessionService = mock(SessionService.class);
	// 로그인 IP 실패 한도는 테스트를 위해 3으로
	private final AuthProperties authProperties = new AuthProperties(Duration.ofDays(30), Duration.ofDays(90),
			Duration.ofHours(12), 5, Duration.ofMinutes(15), 3, Duration.ofMinutes(10));
	private final AuthService authService = new AuthService(userRepository, encoder, sessionService,
			new SignupProperties(3, Duration.ofHours(1)), authProperties, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

	@BeforeEach
	void stubSession() {
		when(sessionService.issue(any(), anyBoolean()))
				.thenReturn(new SessionService.IssuedSession("token-value", NOW.plus(Duration.ofDays(30))));
	}

	private static SignupRequest request(String phone, String password) {
		return new SignupRequest("  홍길동 ", phone, password, " D12345 ", true, null);
	}

	private User savedUser() {
		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(saved.capture());
		return saved.getValue();
	}

	@Test
	@DisplayName("가입하면 번호는 숫자만, 비밀번호는 BCrypt 해시로, 동의 시각과 함께 저장한다")
	void savesUser() {
		authService.signup(request("010-1234-5678", PASSWORD), IP);

		User user = savedUser();
		assertThat(user.getName()).isEqualTo("홍길동");
		assertThat(user.getPhone()).isEqualTo(PHONE);
		assertThat(user.getLoanMemberNo()).isEqualTo("D12345");
		assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
		assertThat(user.getPrivacyAgreedAt()).isEqualTo(NOW);
		assertThat(user.isMustChangePassword()).isFalse();
	}

	@Test
	@DisplayName("대출회원번호가 비어 있으면 null로 저장한다")
	void blankLoanMemberNo() {
		authService.signup(new SignupRequest("홍길동", PHONE, PASSWORD, "  ", true, null), IP);

		assertThat(savedUser().getLoanMemberNo()).isNull();
	}

	@Test
	@DisplayName("독서 독려 알림에 동의하면 동의 시각을 저장한다")
	void reminderAgreed() {
		authService.signup(new SignupRequest("홍길동", PHONE, PASSWORD, null, true, true), IP);

		assertThat(savedUser().getReminderAgreedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("독서 독려 알림에 동의하지 않거나 값을 보내지 않으면 null")
	void reminderNotAgreed() {
		authService.signup(new SignupRequest("홍길동", PHONE, PASSWORD, null, true, false), IP);
		authService.signup(new SignupRequest("홍길동", "01099998888", PASSWORD, null, true, null), IP);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(userRepository, times(2)).saveAndFlush(saved.capture());
		assertThat(saved.getAllValues()).allSatisfy(u -> assertThat(u.getReminderAgreedAt()).isNull());
	}

	@Test
	@DisplayName("이미 가입된 번호면 409")
	void duplicate() {
		when(userRepository.existsByPhone(PHONE)).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(request(PHONE, PASSWORD), IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("PHONE_ALREADY_REGISTERED");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("동시에 같은 번호로 가입해 DB가 막아도 409")
	void concurrentDuplicate() {
		when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> authService.signup(request(PHONE, PASSWORD), IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("PHONE_ALREADY_REGISTERED");
	}

	@Test
	@DisplayName("휴대폰 번호 형식이 아니면 400 INVALID_PHONE")
	void invalidPhone() {
		assertThatThrownBy(() -> authService.signup(request("02-123-4567", PASSWORD), IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PHONE");
	}

	@Test
	@DisplayName("비밀번호 규칙 위반이면 400 INVALID_PASSWORD")
	void invalidPassword() {
		assertThatThrownBy(() -> authService.signup(request(PHONE, "12345678"), IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_PASSWORD");
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("같은 IP에서 한도를 넘으면 429. 다른 IP는 영향 없음")
	void ipLimit() {
		for (int i = 0; i < 3; i++) { // 한도 3
			authService.signup(request("0101111000" + i, PASSWORD), IP);
		}

		assertThatThrownBy(() -> authService.signup(request("01011110009", PASSWORD), IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("TOO_MANY_REQUESTS");
		authService.signup(request("01011110009", PASSWORD), "198.51.100.1");
	}

	// ---------------------------------------------------------------- 가입 후 자동 로그인

	@Test
	@DisplayName("가입하면 로그인 상태 유지 기준으로 세션을 발급하고 토큰과 내 정보를 응답한다")
	void signupIssuesSession() {
		LoginResponse res = authService.signup(request(PHONE, PASSWORD), IP);

		verify(sessionService).issue(any(), eq(true));
		assertThat(res.token()).isEqualTo("token-value");
		assertThat(res.user().name()).isEqualTo("홍길동");
	}

	// ---------------------------------------------------------------- 로그인

	private User existingUser() {
		return User.create("홍길동", PHONE, encoder.encode(PASSWORD), null, true, NOW);
	}

	@Test
	@DisplayName("비밀번호가 맞으면 rememberMe 값대로 세션을 발급한다")
	void loginSuccess() {
		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(existingUser()));

		LoginResponse res = authService.login("010-1234-5678", PASSWORD, false, IP);

		verify(sessionService).issue(any(), eq(false));
		assertThat(res.token()).isEqualTo("token-value");
		assertThat(res.user().reminderAgreed()).isTrue();
		verify(userRepository, never()).resetLoginFailures(any());
	}

	@Test
	@DisplayName("비밀번호가 틀리면 실패를 DB에서 한 번에 기록하고, 없는 번호와 같은 문구로 실패한다")
	void loginWrongPassword() {
		User user = existingUser();
		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.login(PHONE, "wrongpass1", true, IP))
				.isInstanceOf(ApiException.class)
				.hasMessage(AuthService.INVALID_CREDENTIALS)
				.extracting("code").isEqualTo("INVALID_CREDENTIALS");
		verify(userRepository).recordLoginFailure(user.getId(), NOW, 5, NOW.plus(Duration.ofMinutes(15)));
		verify(sessionService, never()).issue(any(), anyBoolean());
	}

	@Test
	@DisplayName("없는 번호도 같은 문구로 실패한다 (가입 여부 노출 방지)")
	void loginUnknownPhone() {
		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(PHONE, PASSWORD, true, IP))
				.isInstanceOf(ApiException.class)
				.hasMessage(AuthService.INVALID_CREDENTIALS);
		verify(userRepository, never()).recordLoginFailure(any(), any(), anyInt(), any());
	}

	@Test
	@DisplayName("잠긴 계정은 비밀번호가 맞아도 429 ACCOUNT_LOCKED")
	void loginLocked() {
		User locked = mock(User.class);
		when(locked.isLocked(NOW)).thenReturn(true);
		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(locked));

		assertThatThrownBy(() -> authService.login(PHONE, PASSWORD, true, IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("ACCOUNT_LOCKED");
		verify(sessionService, never()).issue(any(), anyBoolean());
	}

	@Test
	@DisplayName("이전에 틀린 기록이 있으면 로그인 성공 시 초기화한다")
	void loginResetsFailures() {
		User user = mock(User.class);
		when(user.getId()).thenReturn(7L);
		when(user.getPasswordHash()).thenReturn(encoder.encode(PASSWORD));
		when(user.hasLoginFailures()).thenReturn(true);
		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));

		authService.login(PHONE, PASSWORD, true, IP);

		verify(userRepository).resetLoginFailures(7L);
	}

	@Test
	@DisplayName("같은 IP에서 실패가 한도를 넘으면 429. 다른 IP는 영향 없음")
	void loginIpLimit() {
		when(userRepository.findByPhone(any())).thenReturn(Optional.empty());
		for (int i = 0; i < 3; i++) { // 한도 3
			assertThatThrownBy(() -> authService.login(PHONE, PASSWORD, true, IP)).isInstanceOf(ApiException.class);
		}

		assertThatThrownBy(() -> authService.login(PHONE, PASSWORD, true, IP))
				.isInstanceOf(ApiException.class).extracting("code").isEqualTo("TOO_MANY_REQUESTS");

		when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(existingUser()));
		assertThat(authService.login(PHONE, PASSWORD, true, "198.51.100.1").token()).isEqualTo("token-value");
	}

	// ---------------------------------------------------------------- 로그아웃

	@Test
	@DisplayName("로그아웃하면 그 세션만 삭제한다")
	void logout() {
		authService.logout(new AuthPrincipal(1L, 99L));

		verify(sessionService).revoke(99L);
	}
}
