package com.dobong.dobong.readinglog.dto;

import java.util.List;

/**
 * 독서기록 일괄 제출 응답 (F-0405).
 * 서평이 500자 이상인 기록은 심사 대기(SUBMITTED)로, 미만인 기록은 저장(SAVED)으로 처리한다.
 */

public record ReadingLogSubmitResponse(
        // 심사 대기로 간 기록 번호 목록
        List<Long> submittedIds,
        // 심사 없이 저장으로 끝난 기록 번호 목록
        List<Long> savedIds
) {}
