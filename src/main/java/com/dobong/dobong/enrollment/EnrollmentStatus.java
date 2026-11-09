package com.dobong.dobong.enrollment;

/**
 * 참가 상태 (enrollments.status)
 *
 * ACTIVE: 참가 중 (신청하면 바로 이 상태)
 * COMPLETED: 완주 확정 (F-0803 완주 승인에서 변경)
 */
public enum EnrollmentStatus {
	ACTIVE,
	COMPLETED
}
