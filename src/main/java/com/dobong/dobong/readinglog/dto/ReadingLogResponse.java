package com.dobong.dobong.readinglog.dto;

import com.dobong.dobong.readinglog.ReadingLog;

import java.time.LocalDate;

/**
 * 독서기록 응답 (F-0401 작성, F-0406 수정)
 * 저장된 기록의 필요한 값만 담는다. from()으로 엔티티를 변환한다.
 */

public record ReadingLogResponse(
        Long id,
        Long enrollmentBookId,
        Integer startPage,
        Integer endPage,
        LocalDate readDate,
        String quote,
        String review,
        ReadingLog.ReadingLogStatus status
) {

    // Entity를 응답 DTO로 변환한다.
    public static ReadingLogResponse from(ReadingLog log) {
        return new ReadingLogResponse(
                log.getId(),
                log.getEnrollmentBook().getId(),
                log.getStartPage(),
                log.getEndPage(),
                log.getReadDate(),
                log.getQuote(),
                log.getReview(),
                log.getStatus()
        );
    }
}