package com.dobong.dobong.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 세션 발급, 확인, 삭제
 */
@Service
@RequiredArgsConstructor
public class SessionService {

	// 사용 기록 갱신을 매 요청마다 하지 않고 이 간격보다 오래됐을 때만 함
	static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);
	// 토큰 길이 상한. 쓸데없이 긴 헤더로 해시 계산을 시키는 것 방지 (실제 토큰은 43자)
	static final int MAX_TOKEN_LENGTH = 100;

	private final AuthSessionRepository sessionRepository;
	private final AuthProperties properties;
	private final Clock clock;

	// 원본 토큰은 이 값으로 한 번만 응답에 실리고 서버에는 남지 않음
	public record IssuedSession(String token, Instant expiresAt) {

		@Override
		public String toString() {
			return "IssuedSession[token=***, expiresAt=" + expiresAt + "]";
		}
	}

	@Transactional
	public IssuedSession issue(Long userId, boolean rememberMe) {
		Instant now = clock.instant();
		// 만료 세션 정리. 서버가 쉬다 깨어나는 환경(Cloud Run)이라 예약 작업 대신 로그인할 때 정리
		sessionRepository.deleteAllExpired(now);

		String token = SessionTokens.newToken();
		AuthSession session = AuthSession.create(SessionTokens.hash(token), userId, rememberMe, now, properties);
		sessionRepository.save(session);
		return new IssuedSession(token, session.getExpiresAt());
	}

	// 토큰이 유효하면 요청한 사람 정보, 아니면 빈 값
	@Transactional
	public Optional<AuthPrincipal> authenticate(String token) {
		if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
			return Optional.empty();
		}
		Optional<AuthSession> found = sessionRepository.findByTokenHash(SessionTokens.hash(token));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		AuthSession session = found.get();
		Instant now = clock.instant();
		if (session.isExpired(now)) {
			sessionRepository.delete(session);
			return Optional.empty();
		}
		if (session.getLastUsedAt().plus(TOUCH_INTERVAL).isBefore(now)) {
			session.touch(now, properties.rememberIdle());
		}
		return Optional.of(new AuthPrincipal(session.getUserId(), session.getId()));
	}

	@Transactional
	public void revoke(Long sessionId) {
		sessionRepository.deleteById(sessionId);
	}
}
