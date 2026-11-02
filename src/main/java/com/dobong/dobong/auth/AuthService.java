package com.dobong.dobong.auth;

import com.dobong.dobong.auth.dto.SignupRequest;
import com.dobong.dobong.auth.dto.SignupResponse;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.common.FixedWindowRateLimiter;
import com.dobong.dobong.common.PhoneNumbers;
import com.dobong.dobong.user.User;
import com.dobong.dobong.user.UserRepository;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 회원가입(F-0101)
 */
@Slf4j
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;
	private final FixedWindowRateLimiter signupLimiter;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			SignupProperties signupProperties, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.signupLimiter = new FixedWindowRateLimiter(signupProperties.ipLimit(), signupProperties.ipWindow(), clock);
	}

	@Transactional
	public SignupResponse signup(SignupRequest request, String clientIp) {
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

		String loanMemberNo = StringUtils.hasText(request.loanMemberNo()) ? request.loanMemberNo().strip() : null;
		boolean reminderAgreed = Boolean.TRUE.equals(request.reminderAgreed());
		User user = User.create(request.name().strip(), phone, passwordEncoder.encode(request.password()),
				loanMemberNo, reminderAgreed, clock.instant());
		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException e) {
			// 위의 중복 확인 직후 같은 번호로 동시에 가입한 경우, DB의 UNIQUE가 막는다
			throw alreadyRegistered();
		}

		log.info("회원가입 userId={} phone={}", user.getId(), PhoneNumbers.mask(phone));
		return new SignupResponse(user.getId(), user.getName());
	}

	private static ApiException alreadyRegistered() {
		return ApiException.conflict("PHONE_ALREADY_REGISTERED", "이미 가입된 전화번호입니다. 로그인해 주세요.");
	}
}
