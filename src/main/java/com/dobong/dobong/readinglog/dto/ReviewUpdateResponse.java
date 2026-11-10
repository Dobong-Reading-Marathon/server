package com.dobong.dobong.readinglog.dto;

import com.dobong.dobong.readinglog.ReadingLog;

/**
 * 서평 작성·수정 응답 (F-0402)
 */

public record ReviewUpdateResponse (
    Long id,
    String review,
    int charCount,
    boolean meetsMinLength,
    ReadingLog.ReadingLogStatus status
){
    // 서평 최소 글자 수
    private static final int MIN_REVIEW_LENGTH = 500;

    public static ReviewUpdateResponse from(ReadingLog log){
        String review = log.getReview();
        int charCount = (review == null) ? 0 : review.codePointCount(0, review.length());
        boolean meetsMinLength = charCount >= MIN_REVIEW_LENGTH;
        return new ReviewUpdateResponse(
                log.getId(),
                review,
                charCount,
                meetsMinLength,
                log.getStatus()
        );
    }
}
