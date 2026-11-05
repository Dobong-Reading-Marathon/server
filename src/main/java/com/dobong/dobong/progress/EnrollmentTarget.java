package com.dobong.dobong.progress;

/**
 * 참가 신청 하나와 그 코스의 완주 기준. 완주 판정에 쓴다.
 *
 * @param completed    사서가 완주를 승인했으면 true (enrollments.status = 'COMPLETED')
 * @param libraryBooks 내서재에 등록한 소장도서 수. 대출 권수 참고값 (F-0303)
 */
public record EnrollmentTarget(
		long enrollmentId,
		long roundId,
		int year,
		int roundNo,
		long courseId,
		String courseName,
		boolean family,
		int targetPages,
		int targetBooks,
		int targetReviews,
		boolean completed,
		int libraryBooks) {
}
