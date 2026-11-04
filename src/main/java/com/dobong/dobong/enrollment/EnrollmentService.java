package com.dobong.dobong.enrollment;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.dto.EnrollmentResponse;
import com.dobong.dobong.round.Course;
import com.dobong.dobong.round.CourseRepository;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [F-0205] 회차 참가 신청
 *
 * userId는 항상 로그인 토큰에서 받은 값만 사용 (컨트롤러의 principal.userId()).
 * "오늘"은 한국 날짜 (TimeConfig의 Clock).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final EnrollmentRepository enrollmentRepository;
	private final CourseRepository courseRepository;
	private final RoundService roundService;
	private final Clock clock;

	@Transactional
	public EnrollmentResponse enroll(Long userId, Long courseId) {
		Course course = courseRepository.findById(courseId)
				.orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "코스를 찾을 수 없습니다."));
		Round round = course.getRound();

		if (!round.isApplyOpen(LocalDate.now(clock))) {
			throw ApiException.badRequest("APPLY_CLOSED", "참가 신청 기간이 아닙니다.");
		}
		if (enrollmentRepository.existsByUserIdAndRoundId(userId, round.getId())) {
			throw alreadyEnrolled();
		}

		Enrollment saved;
		try {
			// 바로 INSERT해서 동시에 두 번 누른 경우도 여기서 잡음 (DB UNIQUE)
			saved = enrollmentRepository.saveAndFlush(Enrollment.create(userId, course, clock.instant()));
		} catch (DataIntegrityViolationException e) {
			throw alreadyEnrolled();
		}

		// 개인정보 없이 id만 기록
		log.info("참가 신청 userId={} roundId={} courseId={}", userId, round.getId(), course.getId());
		return EnrollmentResponse.from(saved);
	}

	// 현재 회차(F-0201 규칙)의 내 참가. 신청 안 했으면 빈 값
	@Transactional(readOnly = true)
	public Optional<EnrollmentResponse> current(Long userId) {
		Round round = roundService.findCurrentRound()
				.orElseThrow(() -> ApiException.notFound("ROUND_NOT_FOUND", "진행 중인 회차가 없습니다."));
		return enrollmentRepository.findByUserIdAndRoundId(userId, round.getId())
				.map(EnrollmentResponse::from);
	}

	private static ApiException alreadyEnrolled() {
		return ApiException.conflict("ALREADY_ENROLLED", "이미 이 회차에 참가 신청했습니다.");
	}
}
