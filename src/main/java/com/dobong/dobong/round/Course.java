package com.dobong.dobong.round;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회차별 코스와 완주 기준 (courses 테이블)
 *
 * 참가 신청(F-0205), 코스 변경(F-0202), 완주 판정(F-0601)에서도 사용.
 */
@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_id", nullable = false)
	private Round round;

	@Column(nullable = false, length = 50)
	private String name;

	// 가족 코스 여부 (구성원 명단은 F-0203)
	@Column(name = "is_family", nullable = false)
	private boolean family;

	@Column(name = "target_pages", nullable = false)
	private Integer targetPages;

	@Column(name = "target_reviews", nullable = false)
	private Integer targetReviews;

	@Column(name = "target_books", nullable = false)
	private Integer targetBooks;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder;

	public static Course create(Round round, String name, boolean family, int targetPages, int targetBooks,
			int targetReviews, int sortOrder) {
		Course course = new Course();
		course.round = round;
		course.name = name;
		course.family = family;
		course.targetPages = targetPages;
		course.targetBooks = targetBooks;
		course.targetReviews = targetReviews;
		course.sortOrder = sortOrder;
		return course;
	}

	// 화면 표시용 거리. 1,000쪽 = 1km
	public double distanceKm() {
		return targetPages / 1000.0;
	}
}
