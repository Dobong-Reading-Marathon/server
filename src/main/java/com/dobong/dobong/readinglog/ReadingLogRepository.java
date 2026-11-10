package com.dobong.dobong.readinglog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 독서기록(ReadingLog) 저장소.
 * 기본 저장·조회는 JpaRepository가 제공한다.
 * 소유자 확인은 EnrollmentRepository.existsByIdAndUserId를 서비스에서 사용한다.
 */
public interface ReadingLogRepository extends JpaRepository<ReadingLog, Long> {
    // 책들의 독서기록 (내서재 F-0503)
    List<ReadingLog> findByEnrollmentBookIdIn(List<Long> enrollmentBookIds);
}