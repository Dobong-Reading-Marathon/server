package com.dobong.dobong.book;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * 도서 수동 등록 요청 (F-0301).
 * 프론트가 보낸 값을 받는 상자이고, 빈 값이나 잘못된 형식은 여기서 걸러진다.
 */

public record BookRegisterRequest(
        // 어느 참가 신청에 등록할지
        @NotNull Long enrollmentId,

        // 필수: 도서명, 저자, 전체 쪽수(1 이상)
        @NotBlank String title,
        @NotBlank String author,
        @NotNull @Positive Integer totalPages,

        // 선택: ISBN은 값이 있을 때만 하이픈 없는 13자리 숫자인지 검사한다.
        @Pattern(regexp = "^\\d{13}$", message = "ISBN은 하이픈 없이 13자리 숫자입니다.") String isbn,
        String publisher,
        Integer publishYear,

        // 내서재 정보(enrollment_books)
        Boolean libraryOwned,
        String registrationNo,
        LocalDate loanDate,
        LocalDate returnDate
) {}
