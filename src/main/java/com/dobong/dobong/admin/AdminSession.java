package com.dobong.dobong.admin;

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
 * 관리자 로그인 세션 (admin_sessions 테이블, V7)
 *
 * 원본 토큰은 저장하지 않고 SHA-256 해시만 저장.
 * 연장 없이 로그인 후 sessionMax가 지나면 만료.
 */
@Entity
@Table(name = "admin_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "admin_id", nullable = false)
	private Long adminId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	static AdminSession create(String tokenHash, Long adminId, Instant now, Duration sessionMax) {
		AdminSession s = new AdminSession();
		s.tokenHash = tokenHash;
		s.adminId = adminId;
		s.createdAt = now;
		s.expiresAt = now.plus(sessionMax);
		return s;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}
}
