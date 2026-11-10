package com.dobong.dobong.admin;

import com.dobong.dobong.admin.dto.AdminLoginResponse;
import com.dobong.dobong.admin.dto.AdminMeResponse;
import com.dobong.dobong.auth.AuthProperties;
import com.dobong.dobong.auth.SessionService.IssuedSession;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.common.FixedWindowRateLimiter;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [F-0104] 관리자 로그인/로그아웃
 *
 * 참가자 로그인(AuthService)과 같은 규칙: 실패 문구 하나, 없는 아이디도 BCrypt 비교, 5번 틀리면 잠금.
 * 잠금 횟수·시간과 IP 제한 값은 참가자 설정(app.auth)을 그대로 씀. IP 실패 횟수는 참가자와 따로 셈.
 * 로그에는 입력한 아이디, 비밀번호, 토큰을 남기지 않음 (비밀번호를 아이디 칸에 넣는 실수가 흔함).
 */
@Slf4j
@Service
public class AdminAuthService {

	static final String INVALID_CREDENTIALS = "아이디 또는 비밀번호가 올바르지 않습니다.";

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;
	private final AdminSessionService sessionService;
	private final AuthProperties authProperties;
	private final Clock clock;
	private final FixedWindowRateLimiter loginFailureLimiter;
	// 없는 아이디일 때 비교용 해시. 응답 시간을 있는 아이디와 비슷하게 맞춤
	private final String dummyHash;

	public AdminAuthService(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
			AdminSessionService sessionService, AuthProperties authProperties, Clock clock) {
		this.adminRepository = adminRepository;
		this.passwordEncoder = passwordEncoder;
		this.sessionService = sessionService;
		this.authProperties = authProperties;
		this.clock = clock;
		this.loginFailureLimiter = new FixedWindowRateLimiter(
				authProperties.ipFailureLimit(), authProperties.ipFailureWindow(), clock);
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-0");
	}

	// 실패 기록(실패 횟수 증가)이 예외 때문에 롤백되지 않도록 noRollbackFor 지정
	@Transactional(noRollbackFor = ApiException.class)
	public AdminLoginResponse login(String loginId, String password, String clientIp) {
		if (loginFailureLimiter.isBlocked(clientIp)) {
			throw ApiException.tooManyRequests("TOO_MANY_REQUESTS", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
		}
		Instant now = clock.instant();
		Admin admin = adminRepository.findByLoginId(loginId).orElse(null);

		if (admin == null) {
			passwordEncoder.matches(password, dummyHash); // 응답 시간 맞추기
			loginFailureLimiter.record(clientIp);
			log.warn("관리자 로그인 실패 (없는 아이디) ip={}", clientIp);
			throw invalidCredentials();
		}
		if (admin.isLocked(now)) {
			log.warn("관리자 로그인 잠김 adminId={} ip={}", admin.getId(), clientIp);
			throw ApiException.tooManyRequests("ACCOUNT_LOCKED", "비밀번호를 여러 번 틀려 로그인이 잠시 막혔습니다. "
					+ authProperties.lockDuration().toMinutes() + "분 뒤에 다시 시도해 주세요.");
		}
		if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
			adminRepository.recordLoginFailure(admin.getId(), now, authProperties.maxFailedAttempts(),
					now.plus(authProperties.lockDuration()));
			loginFailureLimiter.record(clientIp);
			log.warn("관리자 로그인 실패 adminId={} ip={}", admin.getId(), clientIp);
			throw invalidCredentials();
		}

		if (admin.hasLoginFailures()) {
			adminRepository.resetLoginFailures(admin.getId());
		}
		IssuedSession session = sessionService.issue(admin.getId());
		log.info("관리자 로그인 adminId={}", admin.getId());
		return new AdminLoginResponse(session.token(), session.expiresAt(), AdminMeResponse.from(admin));
	}

	// 이 토큰(세션)만 무효로 만듦
	public void logout(AdminPrincipal principal) {
		sessionService.revoke(principal.sessionId());
	}

	@Transactional(readOnly = true)
	public AdminMeResponse me(AdminPrincipal principal) {
		// 세션은 관리자 삭제 시 함께 지워지므로 보통 없을 수 없지만, 그 사이 삭제된 경우 401
		return adminRepository.findById(principal.adminId())
				.map(AdminMeResponse::from)
				.orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다."));
	}

	private static ApiException invalidCredentials() {
		return ApiException.unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
	}
}
