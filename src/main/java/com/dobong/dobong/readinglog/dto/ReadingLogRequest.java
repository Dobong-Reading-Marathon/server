package com.dobong.dobong.readinglog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * 독서기록 작성 요청 (F-0401)
 * 형식 검증은 여기서, 쪽 범위와 소유자 확인은 서비스에서 한다.
 */

public record ReadingLogRequest(
        // 기록할 내서재 도서 번호
        @NotNull Long enrollmentBookId,

        // 시작,끝 쪽(1 이상)
        @NotNull @Min(1) Integer startPage,
        @NotNull @Min(1) Integer endPage,

        //읽은 날짜
        @NotNull @PastOrPresent
        LocalDate readDate,

        // 글귀, 서평
        String quote,
        String review
){}
