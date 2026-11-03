package com.dobong.dobong.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참가자 회원
 *
 * 비밀번호는 접두사 없는 BCrypt 해시로만 저장. 원문은 어디에도 저장하거나 로그로 남기지 않음.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	// 숫자만 저장 (예: 01012345678). 로그인 아이디 역할
	@Column(nullable = false, length = 20, unique = true)
	private String phone;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "loan_member_no", length = 30)
	private String loanMemberNo;

	@Column(name = "privacy_agreed_at", nullable = false)
	private Instant privacyAgreedAt;

	// 독서 독려 알림 수신 동의 시각. null이면 동의 안 함 (V2 기준)
	@Column(name = "reminder_agreed_at")
	private Instant reminderAgreedAt;

	// 관리자가 임시 비밀번호로 초기화했을 때 true (기능 F-0105)
	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	// 연속 로그인 실패 횟수. 성공하면 0 (V4)
	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	// 이 시각까지 로그인 잠금. null이면 잠기지 않음 (V4)
	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * 회원가입
	 *
	 * phone은 PhoneNumbers로 정리한 값, passwordHash는 이미 해시한 값을 넣음.
	 * reminderAgreed가 true면 알림 동의 시각을 now로 남김.
	 */
	public static User create(String name, String phone, String passwordHash, String loanMemberNo,
			boolean reminderAgreed, Instant now) {
		User user = new User();
		user.name = name;
		user.phone = phone;
		user.passwordHash = passwordHash;
		user.loanMemberNo = loanMemberNo;
		user.privacyAgreedAt = now;
		user.reminderAgreedAt = reminderAgreed ? now : null;
		user.mustChangePassword = false;
		user.failedLoginCount = 0;
		user.createdAt = now;
		user.updatedAt = now;
		return user;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	public boolean hasLoginFailures() {
		return failedLoginCount > 0 || lockedUntil != null;
	}

	@PreUpdate
	void touchUpdatedAt() {
		this.updatedAt = Instant.now();
	}
}
