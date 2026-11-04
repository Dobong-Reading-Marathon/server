package com.dobong.dobong.readinglog.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 서평 작성·수정 요청 (F-0402)
 */

public record ReviewUpdateRequest(
        // 서평 본문 (공백만 있는 값은 받지 않는다)
        @NotBlank String review
){}
