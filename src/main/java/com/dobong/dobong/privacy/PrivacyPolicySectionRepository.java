package com.dobong.dobong.privacy;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyPolicySectionRepository extends JpaRepository<PrivacyPolicySection, Long> {

	List<PrivacyPolicySection> findByVersionOrderBySortOrderAsc(Integer version);
}
