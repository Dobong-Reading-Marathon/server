package com.dobong.dobong.book;

import com.dobong.dobong.book.dto.BookSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 도서 정보 자동 입력 API (F-0302).
 * 로그인한 참가자만 쓸 수 있다 (SecurityConfig의 /api/** 규칙). 조회 전용이고 저장은 F-0301이 한다.
 *
 * 파라미터를 required=false로 받는 이유: 빠졌을 때도 값이 빈 경우와 같은 INVALID_INPUT 400으로 응답하려고.
 * 검증은 BookSearchService가 한다.
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookSearchController {

    private final BookSearchService bookSearchService;

    // GET /api/books/search?query=제목 : 제목으로 검색 (최대 10권)
    @GetMapping("/search")
    public BookSearchResponse.Items search(@RequestParam(required = false) String query) {
        return bookSearchService.search(query);
    }

    // GET /api/books/lookup?isbn=9788937460470 : ISBN13으로 한 권 조회
    @GetMapping("/lookup")
    public BookSearchResponse lookup(@RequestParam(required = false) String isbn) {
        return bookSearchService.lookup(isbn);
    }
}
