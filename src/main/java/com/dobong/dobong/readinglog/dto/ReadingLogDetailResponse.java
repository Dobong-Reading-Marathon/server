package com.dobong.dobong.readinglog.dto;

import com.dobong.dobong.readinglog.ReadingLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 독서기록 상세 응답 (F-0407 상세 조회: 상태, 사서 코멘트)
 */

public record ReadingLogDetailResponse (
        Long id,
        Long enrollmentBookId,
        Integer startPage,
        Integer endPage,
        LocalDate readDate,
        String quote,
        String review,
        ReadingLog.ReadingLogStatus status,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        String librarianComment
){
    // Entity를 상세 응답 DTO로 변환한다.
    public static ReadingLogDetailResponse from(ReadingLog log){
        return new ReadingLogDetailResponse(
                log.getId(),
                log.getEnrollmentBook().getId(),
                log.getStartPage(),
                log.getEndPage(),
                log.getReadDate(),
                log.getQuote(),
                log.getReview(),
                log.getStatus(),
                log.getSubmittedAt(),
                log.getReviewedAt(),
                log.getLibrarianComment()
        );
    }
}


