package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
	private final AuthService authService = new AuthService(userRepository, encoder,
			new SignupProperties(3, Duration.ofHours(1)), Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

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
}
