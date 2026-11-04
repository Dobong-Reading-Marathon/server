package com.dobong.dobong.readinglog;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.readinglog.dto.ReadingLogRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogResponse;
import com.dobong.dobong.readinglog.dto.ReviewUpdateRequest;
import com.dobong.dobong.readinglog.dto.ReviewUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 독서기록 API (F-0401, F-0402).
 * 요청을 받아 서비스에 넘기고 결과를 응답한다.
 */

@RestController
@RequestMapping("/api/reading-logs")
@RequiredArgsConstructor
public class ReadingLogController {
    private final ReadingLogService readingLogService;

    // 독서기록 작성 (F-0401)
    @PostMapping
    public ResponseEntity<ReadingLogResponse> create(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 요청 JSON 받기 + 형식 검증
            @Valid @RequestBody ReadingLogRequest request){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(readingLogService.create(principal.userId(),request));

    }

    // 서평 작성·수정 (F-0402)
    @PatchMapping("/{id}/review")
    public ResponseEntity<ReviewUpdateResponse> updateReview(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 주소의 {id}
            @PathVariable Long id,
            // 요청 본문 + 형식 검증
            @Valid @RequestBody ReviewUpdateRequest request){

        return ResponseEntity.ok(readingLogService.updateReview(principal.userId(), id, request));
    }
}