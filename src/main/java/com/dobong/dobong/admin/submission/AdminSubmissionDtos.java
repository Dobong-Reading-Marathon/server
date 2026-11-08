package com.dobong.dobong.admin.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class AdminSubmissionDtos {

    // 목록의 한 줄
    public record Item(Long id, String participantName, String bookTitle,
                       Integer startPage, Integer endPage, LocalDate readDate,
                       String status, OffsetDateTime submittedAt) {}

    // 최종 응답: 심사 대기 건수 + 목록
    public record InboxResponse(long pendingCount, List<Item> items) {}

    // [02 추가] 보완요청 요청 본문
    public record RevisionRequest(
            @NotBlank(message = "보완요청 사유를 입력해 주세요.")
            @Size(max = 1000, message = "사유는 1000자 이내로 입력해 주세요.")
            String comment) {}
}