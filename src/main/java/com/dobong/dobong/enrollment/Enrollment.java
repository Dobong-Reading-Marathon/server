package com.dobong.dobong.enrollment;

import com.dobong.dobong.round.Course;
import com.dobong.dobong.round.Round;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회차 참가 (enrollments 테이블)
 *
 * 한 회원은 한 회차에 한 번만 참가 (DB UNIQUE(user_id, round_id)).
 */
@Entity
@Table(name = "enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 회원 정보 자체는 필요 없어서 id만 저장
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EnrollmentStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// 신청한 코스의 회차로 참가. 상태는 ACTIVE
	public static Enrollment create(Long userId, Course course, Instant now) {
		Enrollment e = new Enrollment();
		e.userId = userId;
		e.round = course.getRound();
		e.course = course;
		e.status = EnrollmentStatus.ACTIVE;
		e.createdAt = now;
		e.updatedAt = now;
		return e;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
