package com.dobong.dobong.enrollment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	Optional<Enrollment> findByUserIdAndRoundId(Long userId, Long roundId);

	boolean existsByUserIdAndRoundId(Long userId, Long roundId);

	// 요청으로 받은 참가 id가 본인 것인지 확인 (IDOR 방지). 책 등록, 독서기록 등에서 사용
	boolean existsByIdAndUserId(Long id, Long userId);
}
