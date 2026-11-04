package com.dobong.dobong.admin;

import com.dobong.dobong.auth.SessionService.IssuedSession;
import com.dobong.dobong.auth.SessionTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 세션 발급, 확인, 삭제
 *
 * 참가자 세션(SessionService)과 테이블만 다르고 토큰 방식은 같음. 연장은 없음.
 */
@Service
@RequiredArgsConstructor
public class AdminSessionService {

	// 토큰 길이 상한. 쓸데없이 긴 헤더로 해시 계산을 시키는 것 방지 (실제 토큰은 43자)
	static final int MAX_TOKEN_LENGTH = 100;

	private final AdminSessionRepository sessionRepository;
	private final AdminProperties properties;
	private final Clock clock;

	@Transactional
	public IssuedSession issue(Long adminId) {
		Instant now = clock.instant();
		// 만료 세션 정리 (참가자와 같이 로그인할 때 정리)
		sessionRepository.deleteAllExpired(now);

		String token = SessionTokens.newToken();
		AdminSession session = AdminSession.create(SessionTokens.hash(token), adminId, now, properties.sessionMax());
		sessionRepository.save(session);
		return new IssuedSession(token, session.getExpiresAt());
	}

	// 토큰이 유효하면 관리자 정보, 아니면 빈 값
	@Transactional
	public Optional<AdminPrincipal> authenticate(String token) {
		if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
			return Optional.empty();
		}
		Optional<AdminSession> found = sessionRepository.findByTokenHash(SessionTokens.hash(token));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		AdminSession session = found.get();
		if (session.isExpired(clock.instant())) {
			sessionRepository.delete(session);
			return Optional.empty();
		}
		return Optional.of(new AdminPrincipal(session.getAdminId(), session.getId()));
	}

	@Transactional
	public void revoke(Long sessionId) {
		sessionRepository.deleteById(sessionId);
	}
}
