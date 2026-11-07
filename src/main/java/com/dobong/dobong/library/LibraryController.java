package com.dobong.dobong.library;

import com.dobong.dobong.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내서재 API (F-0503).
 * 요청을 받아 서비스에 넘기고 결과를 응답한다.
 */
@RestController
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    // 내 서재 도서 목록 조회 (F-0503)
    @GetMapping("/api/books")
    public List<LibraryItemResponse> getMyLibrary(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 검색어 (선택, 제목 및 저자)
            @RequestParam(required = false) String keyword,
            // 상태 필터 (선택)
            @RequestParam(required = false) LibraryStatus status) {

        return libraryService.getMyLibrary(principal.userId(), keyword, status);
    }
}