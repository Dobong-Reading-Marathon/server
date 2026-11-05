package com.dobong.dobong.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 처리방침의 절 하나 (privacy_policy_sections 테이블, V8)
 *
 * body는 일반 텍스트 (HTML 아님). 줄바꿈만 있음.
 */
@Entity
@Table(name = "privacy_policy_sections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivacyPolicySection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Integer version;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String body;
}
