package com.dobong.dobong.readinglog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

/**
 * 독서기록 수정 요청 (F-0406)
 * 보낸 값만 바꾸고, 안 보낸 값(null)은 기존 값을 유지한다.
 * 형식 검증은 여기서, 쪽 범위와 소유자 확인은 서비스에서 한다.
 */


public record ReadingLogUpdateRequest(

        // 시작,끝 쪽(1 이상)
        @Min(1) Integer startPage,
        @Min(1) Integer endPage,

        //읽은 날짜
        @PastOrPresent
        LocalDate readDate,

        // 글귀, 서평
        String quote,
        String review
){}
