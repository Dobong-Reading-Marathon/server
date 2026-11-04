package com.dobong.dobong.enrollment.dto;

import com.dobong.dobong.enrollment.Enrollment;
import com.dobong.dobong.enrollment.EnrollmentStatus;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.dto.CourseResponse;
import java.time.Instant;

/**
 * 내 참가 정보
 *
 * @param id     참가 id
 * @param status ACTIVE(참가 중) / COMPLETED(완주)
 * @param course 신청한 코스와 완주 기준
 */
public record EnrollmentResponse(
		Long id,
		Long roundId,
		int year,
		int roundNo,
		EnrollmentStatus status,
		CourseResponse course,
		Instant createdAt) {

	public static EnrollmentResponse from(Enrollment e) {
		Round r = e.getRound();
		return new EnrollmentResponse(e.getId(), r.getId(), r.getYear(), r.getRoundNo(), e.getStatus(),
				CourseResponse.from(e.getCourse()), e.getCreatedAt());
	}
}
