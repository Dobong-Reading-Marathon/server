package com.dobong.dobong.round.dto;

import com.dobong.dobong.round.Course;

/**
 * 코스와 완주 기준
 *
 * @param family     가족 코스 여부
 * @param distanceKm 화면 표시용 거리 (완주 쪽수 ÷ 1,000)
 */
public record CourseResponse(Long id, String name, boolean family, int targetPages, int targetBooks,
		int targetReviews, double distanceKm) {

	public static CourseResponse from(Course c) {
		return new CourseResponse(c.getId(), c.getName(), c.isFamily(), c.getTargetPages(), c.getTargetBooks(),
				c.getTargetReviews(), c.distanceKm());
	}
}
