package com.dobong.dobong.admin.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class AdminSubmissionDtos {

    // 목록의 한 줄. 서평 본문과 글자 수(DB의 char_length 기준)를 함께 준다
    public record Item(Long id, String participantName, String bookTitle,
                       Integer startPage, Integer endPage, LocalDate readDate,
                       String status, OffsetDateTime submittedAt,
                       String review, int reviewLength) {}

    // 기록 한 건 상세 (심사 화면 오른쪽). 글귀·서평 본문과 사서 코멘트를 함께 준다
    public record Detail(Long id, Long enrollmentId, String participantName,
                         String bookTitle, String author,
                         Integer startPage, Integer endPage, LocalDate readDate,
                         String status, String quote, String review, int reviewLength,
                         String librarianComment, OffsetDateTime submittedAt,
                         OffsetDateTime reviewedAt) {}

    // 최종 응답: 심사 대기 건수 + 목록
    public record InboxResponse(long pendingCount, List<Item> items) {}

    // [02 추가] 보완요청 요청 본문
    public record RevisionRequest(
            @NotBlank(message = "보완요청 사유를 입력해 주세요.")
            @Size(max = 1000, message = "사유는 1000자 이내로 입력해 주세요.")
            String comment) {}
}