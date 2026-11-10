package com.dobong.dobong.book;

import com.dobong.dobong.auth.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 도서 등록 API (F-0301).
 * 요청을 받아 BookService에 넘기고 결과를 돌려주는 창구이다. 로직은 Service에 있다.
 */

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    // POST /api/books : 책을 내서재에 등록한다.
    @PostMapping
    public ResponseEntity<EnrollmentBookResponse>register(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BookRegisterRequest request){

        EnrollmentBookResponse response = bookService.register(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
