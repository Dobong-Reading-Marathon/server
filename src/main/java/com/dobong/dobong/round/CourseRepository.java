package com.dobong.dobong.round;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

	List<Course> findByRoundIdOrderBySortOrderAscIdAsc(Long roundId);
}
