package com.dobong.dobong.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정 (admins 테이블)
 *
 * 계정 생성은 InitialAdminCreator(환경변수)만 함. 그래서 이 엔티티로 INSERT하지 않음.
 * 비밀번호는 접두사 없는 BCrypt 해시로만 저장.
 */
@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "login_id", nullable = false, length = 50, unique = true)
	private String loginId;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String name;

	// 연속 로그인 실패 횟수. 성공하면 0 (V7)
	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	// 이 시각까지 로그인 잠금. null이면 잠기지 않음 (V7)
	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	public boolean hasLoginFailures() {
		return failedLoginCount > 0 || lockedUntil != null;
	}
}
