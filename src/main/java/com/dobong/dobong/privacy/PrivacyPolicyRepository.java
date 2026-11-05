package com.dobong.dobong.privacy;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyPolicyRepository extends JpaRepository<PrivacyPolicy, Integer> {

	// 시행일이 today 이전인 버전 중 가장 큰 번호 = 현재 버전
	Optional<PrivacyPolicy> findFirstByEffectiveFromLessThanEqualOrderByVersionDesc(LocalDate today);
}
