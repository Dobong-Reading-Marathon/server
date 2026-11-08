package com.dobong.dobong.admin.submission;

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
}