package com.dobong.dobong.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

	Optional<AuthSession> findByTokenHash(String tokenHash);

	// 만료된 세션 정리 (로그인할 때 호출)
	@Modifying
	@Query("DELETE FROM AuthSession s WHERE s.expiresAt <= :now")
	int deleteAllExpired(@Param("now") Instant now);
}
