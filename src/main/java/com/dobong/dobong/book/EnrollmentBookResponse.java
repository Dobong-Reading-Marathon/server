package com.dobong.dobong.book;

import java.time.LocalDate;

/**
 * 도서 등록 결과 응답 (F-0301).
 * 책 정보(books)와 내서재 정보(enrollment_books)를 한 번에 돌려준다.
 * 아동도서 여부(F-0303)도 책 정보로 함께 돌려준다.
 */

public record EnrollmentBookResponse (
        Long id,
        Long bookId,
        String title,
        String author,
        String publisher,
        Integer publishYear,
        Integer totalPages,
        String isbn,
        boolean libraryOwned,
        String registrationNo,
        LocalDate loanDate,
        LocalDate returnDate,
        boolean children
){
    // Entity를 응답 DTO로 바꾼다.
    public static EnrollmentBookResponse from(EnrollmentBook eb){
        return new EnrollmentBookResponse(
                eb.getId(),
                eb.getBook().getId(),
                eb.getBook().getTitle(),
                eb.getBook().getAuthor(),
                eb.getBook().getPublisher(),
                eb.getBook().getPublishYear(),
                eb.getBook().getTotalPages(),
                eb.getBook().getIsbn(),
                eb.isLibraryOwned(),
                eb.getRegistrationNo(),
                eb.getLoanDate(),
                eb.getReturnDate(),
                eb.getBook().isChildren()
        );
    }
}
