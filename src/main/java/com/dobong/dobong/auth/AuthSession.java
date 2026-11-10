package com.dobong.dobong.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 세션 (auth_sessions 테이블, V3)
 *
 * 원본 토큰은 저장하지 않고 SHA-256 해시만 저장.
 */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "remember_me", nullable = false)
	private boolean rememberMe;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "absolute_expires_at", nullable = false)
	private Instant absoluteExpiresAt;

	/**
	 * 새 세션
	 *
	 * rememberMe true: 만료 = 지금 + idle (최대 지금 + max), 사용할 때마다 연장
	 * rememberMe false: 만료 = 지금 + sessionMax, 연장 없음
	 */
	static AuthSession create(String tokenHash, Long userId, boolean rememberMe, Instant now, AuthProperties props) {
		AuthSession s = new AuthSession();
		s.tokenHash = tokenHash;
		s.userId = userId;
		s.rememberMe = rememberMe;
		s.createdAt = now;
		s.lastUsedAt = now;
		if (rememberMe) {
			s.absoluteExpiresAt = now.plus(props.rememberMax());
			s.expiresAt = min(now.plus(props.rememberIdle()), s.absoluteExpiresAt);
		} else {
			s.absoluteExpiresAt = now.plus(props.sessionMax());
			s.expiresAt = s.absoluteExpiresAt;
		}
		return s;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	// 사용 기록 갱신. 유지 세션이면 만료도 연장 (최대 만료는 넘지 않음)
	void touch(Instant now, Duration idle) {
		this.lastUsedAt = now;
		if (rememberMe) {
			this.expiresAt = min(now.plus(idle), absoluteExpiresAt);
		}
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}
}
