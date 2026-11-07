package com.dobong.dobong.readinglog.dto;

import java.util.List;

/**
 * 독서기록 일괄 제출 응답 (F-0405).
 * 제출 처리된 기록 번호 목록을 돌려준다.
 */

public record ReadingLogSubmitResponse(
        // 제출된 기록 번호 목록
        List<Long> submittedIds
) {}