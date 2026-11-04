package com.dobong.dobong.admin;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRepository extends JpaRepository<Admin, Long> {

	Optional<Admin> findByLoginId(String loginId);

	/**
	 * 로그인 실패 1회 기록 (UserRepository.recordLoginFailure와 같은 방식)
	 *
	 * 동시에 여러 번 틀려도 횟수가 정확하도록 DB에서 UPDATE 한 번으로 셈.
	 * 잠금이 이미 풀린 계정이면 1회부터 다시 셈. maxAttempts번째 실패면 lockUntil까지 잠금.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE admins SET
			    failed_login_count = CASE
			        WHEN locked_until IS NOT NULL AND locked_until <= CAST(:now AS timestamptz) THEN 1
			        ELSE failed_login_count + 1 END,
			    locked_until = CASE
			        WHEN locked_until IS NOT NULL AND locked_until <= CAST(:now AS timestamptz) THEN NULL
			        WHEN failed_login_count + 1 >= :maxAttempts THEN CAST(:lockUntil AS timestamptz)
			        ELSE locked_until END
			WHERE id = :id
			""", nativeQuery = true)
	int recordLoginFailure(@Param("id") Long id, @Param("now") Instant now,
			@Param("maxAttempts") int maxAttempts, @Param("lockUntil") Instant lockUntil);

	// 로그인 성공 시 실패 기록 초기화
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE admins SET failed_login_count = 0, locked_until = NULL WHERE id = :id", nativeQuery = true)
	int resetLoginFailures(@Param("id") Long id);
}
