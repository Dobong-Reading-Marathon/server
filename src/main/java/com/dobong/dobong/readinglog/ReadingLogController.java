package com.dobong.dobong.readinglog;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.readinglog.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.dobong.dobong.readinglog.dto.ReadingLogSubmitRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogSubmitResponse;

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

    // 독서기록 일괄 제출 (F-0405)
    @PostMapping("/submit")
    public ResponseEntity<ReadingLogSubmitResponse> submit(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 요청 본문 + 형식 검증
            @Valid @RequestBody ReadingLogSubmitRequest request){

        return ResponseEntity.ok(readingLogService.submit(principal.userId(), request));
    }

    // 독서기록 수정 (F-0406)
    @PatchMapping("/{id}")
    public ResponseEntity <ReadingLogResponse> update(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 주소의 {id}
            @PathVariable Long id,
            // 요청 본문 + 형식 검증
            @Valid @RequestBody ReadingLogUpdateRequest request){

        return ResponseEntity.ok(readingLogService.update(principal.userId(), id, request));
    }

    // 독서기록 삭제 (F-0406)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 주소의 {id}
            @PathVariable Long id){
        readingLogService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

}