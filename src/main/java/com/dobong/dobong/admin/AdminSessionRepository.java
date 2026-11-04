package com.dobong.dobong.admin;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

	Optional<AdminSession> findByTokenHash(String tokenHash);

	// 만료된 세션 정리 (로그인할 때 호출)
	@Modifying
	@Query("DELETE FROM AdminSession s WHERE s.expiresAt <= :now")
	int deleteAllExpired(@Param("now") Instant now);
}
