package com.dobong.dobong.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인정보 처리방침 버전 (privacy_policies 테이블, V8)
 *
 * Flyway로만 추가함. 이 엔티티로 INSERT·UPDATE하지 않음.
 */
@Entity
@Table(name = "privacy_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyPolicy {

	@Id
	private Integer version;

	// 시행일. 이 날짜(한국 날짜)부터 현재 버전이 됨
	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;
}
