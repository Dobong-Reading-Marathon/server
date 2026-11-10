package com.dobong.dobong.book.dto;

import java.util.List;

/**
 * 도서 정보 자동 입력 결과 한 권 (F-0302).
 * title, author, publisher, publishYear, totalPages, isbn은 F-0301 등록 요청(BookRegisterRequest)과 이름을 맞췄다.
 * coverUrl, sourceLink는 화면 표시용이다. 예스24 이용 정책에 따라 프론트가 sourceLink로 출처를 표기하고 상품 페이지로 연결한다.
 * 값이 없는 칸(예: 쪽수)은 null이고, 프론트는 비워 두고 사용자가 직접 입력하게 한다.
 */
public record BookSearchResponse(
        String title,
        String author,
        String publisher,
        Integer publishYear,
        Integer totalPages,
        String isbn,
        String coverUrl,
        String sourceLink
) {

    /** 검색 결과 목록. 결과가 없으면 items는 빈 배열이다 (오류 아님). */
    public record Items(List<BookSearchResponse> items) {
    }
}
