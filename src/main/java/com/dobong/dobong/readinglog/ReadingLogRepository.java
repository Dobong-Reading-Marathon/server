package com.dobong.dobong.readinglog;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 독서기록(ReadingLog) 저장소.
 * 기본 저장·조회는 JpaRepository가 제공한다.
 * 소유자 확인은 EnrollmentRepository.existsByIdAndUserId를 서비스에서 사용한다.
 */
public interface ReadingLogRepository extends JpaRepository<ReadingLog, Long> {

}