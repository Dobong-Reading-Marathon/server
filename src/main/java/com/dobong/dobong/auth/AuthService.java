package com.dobong.dobong.auth;

import com.dobong.dobong.auth.dto.LoginResponse;
import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.common.FixedWindowRateLimiter;
import com.dobong.dobong.common.PhoneNumbers;
import com.dobong.dobong.privacy.PrivacyPolicyService;
import com.dobong.dobong.user.MeResponse;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 회원가입(F-0101), 로그인·로그아웃(F-0102)
 * 가입 시 동의한 처리방침 버전 저장(F-0103)
 *
 * 로그인 실패 문구는 하나뿐 (어느 쪽이 틀렸는지 알려 주지 않음).
 * 없는 번호여도 BCrypt 비교를 한 번 해서 응답 시간으로 가입 여부를 알아낼 수 없게 함.
 * 로그에는 비밀번호, 토큰, 전화번호 원문을 남기지 않음.
 */
@Slf4j
@Service
public class AuthService {

	static final String INVALID_CREDENTIALS = "전화번호 또는 비밀번호가 올바르지 않습니다.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessionService sessionService;
	private final PrivacyPolicyService privacyPolicyService;
	private final AuthProperties authProperties;
	private final Clock clock;
	private final FixedWindowRateLimiter signupLimiter;
	private final FixedWindowRateLimiter loginFailureLimiter;
	// 없는 번호일 때 비교용 해시. 응답 시간을 있는 번호와 비슷하게 맞춤
	private final String dummyHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionService sessionService,
			PrivacyPolicyService privacyPolicyService, SignupProperties signupProperties,
			AuthProperties authProperties, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.sessionService = sessionService;
		this.privacyPolicyService = privacyPolicyService;
		this.authProperties = authProperties;
		this.clock = clock;
		this.signupLimiter = new FixedWindowRateLimiter(signupProperties.ipLimit(), signupProperties.ipWindow(), clock);
		this.loginFailureLimiter = new FixedWindowRateLimiter(
				authProperties.ipFailureLimit(), authProperties.ipFailureWindow(), clock);
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-0");
	}

	// [F-0101] 회원가입. 가입 직후 바로 로그인된 상태로 응답 (로그인 상태 유지 기준)
	@Transactional
	public LoginResponse signup(SignupRequest request, String clientIp) {
		// 가입 여부를 하나씩 확인해 보는 것도 막기 위해 중복 확인보다 먼저
		if (!signupLimiter.tryAcquire(clientIp)) {
			throw ApiException.tooManyRequests("TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
		}

		String phone = PhoneNumbers.normalizeOrNull(request.phone());
		if (phone == null) {
			throw ApiException.badRequest("INVALID_PHONE", "휴대폰 번호 형식이 올바르지 않습니다.");
		}
		PasswordPolicy.validate(request.password(), phone);

		if (userRepository.existsByPhone(phone)) {
			throw alreadyRegistered();
		}

		// 화면에 보여 준 처리방침이 현재 버전인지 확인 (예전 문안에 동의한 것으로 기록되지 않게)
		int policyVersion = privacyPolicyService.currentVersion();
		if (request.privacyPolicyVersion() != policyVersion) {
			throw ApiException.conflict("PRIVACY_POLICY_OUTDATED",
					"개인정보 처리방침이 바뀌었습니다. 다시 확인하고 동의해 주세요.");
		}

		String loanMemberNo = StringUtils.hasText(request.loanMemberNo()) ? request.loanMemberNo().strip() : null;
		boolean reminderAgreed = Boolean.TRUE.equals(request.reminderAgreed());
		User user = User.create(request.name().strip(), phone, passwordEncoder.encode(request.password()),
				loanMemberNo, reminderAgreed, policyVersion, clock.instant());
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 위의 중복 확인 직후 같은 번호로 동시에 가입한 경우, DB의 UNIQUE가 막는다
			throw alreadyRegistered();
		}

		log.info("회원가입 userId={} phone={}", user.getId(), PhoneNumbers.mask(phone));
		return loginResponse(user, sessionService.issue(user.getId(), true));
	}

	//[F-0102] 로그인. 실패 기록(실패 횟수 증가)이 예외 때문에 롤백되지 않도록 noRollbackFor 지정.
	@Transactional(noRollbackFor = ApiException.class)
	public LoginResponse login(String rawPhone, String password, boolean rememberMe, String clientIp) {
		if (loginFailureLimiter.isBlocked(clientIp)) {
			throw ApiException.tooManyRequests("TOO_MANY_REQUESTS", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
		}
		Instant now = clock.instant();
		String phone = PhoneNumbers.normalizeOrNull(rawPhone);
		User user = phone == null ? null : userRepository.findByPhone(phone).orElse(null);

		if (user == null) {
			passwordEncoder.matches(password, dummyHash); // 응답 시간 맞추기
			loginFailureLimiter.record(clientIp);
			throw invalidCredentials();
		}
		if (user.isLocked(now)) {
			throw ApiException.tooManyRequests("ACCOUNT_LOCKED", "비밀번호를 여러 번 틀려 로그인이 잠시 막혔습니다. "
					+ authProperties.lockDuration().toMinutes() + "분 뒤에 다시 시도하거나 도서관에 문의해 주세요.");
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			userRepository.recordLoginFailure(user.getId(), now, authProperties.maxFailedAttempts(),
					now.plus(authProperties.lockDuration()));
			loginFailureLimiter.record(clientIp);
			log.info("로그인 실패 userId={}", user.getId());
			throw invalidCredentials();
		}

		if (user.hasLoginFailures()) {
			userRepository.resetLoginFailures(user.getId());
		}
		log.info("로그인 userId={} rememberMe={}", user.getId(), rememberMe);
		return loginResponse(user, sessionService.issue(user.getId(), rememberMe));
	}

	// [F-0102] 로그아웃. 이 토큰(세션)만 무효로 만듦
	public void logout(AuthPrincipal principal) {
		sessionService.revoke(principal.sessionId());
	}

	private static LoginResponse loginResponse(User user, SessionService.IssuedSession session) {
		return new LoginResponse(session.token(), session.expiresAt(), MeResponse.from(user));
	}

	private static ApiException invalidCredentials() {
		return ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
	}

	private static ApiException alreadyRegistered() {
		return ApiException.conflict("PHONE_ALREADY_REGISTERED", "이미 가입된 전화번호입니다. 로그인해 주세요.");
	}
}
