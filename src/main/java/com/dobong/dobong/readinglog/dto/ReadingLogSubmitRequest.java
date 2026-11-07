package com.dobong.dobong.readinglog.dto;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

/**
 * 독서기록 일괄 제출 요청 (F-0405).
 * 제출 일시는 서버가 넣으므로 제출할 기록 번호 목록만 받는다.
 */

public record ReadingLogSubmitRequest (
        @NotEmpty(message = "제출할 기록을 선택해 주세요.")
        List<Long> readingLogIds
){}


