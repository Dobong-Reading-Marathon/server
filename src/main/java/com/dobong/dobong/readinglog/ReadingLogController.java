package com.dobong.dobong.readinglog;

import com.dobong.dobong.auth.AuthPrincipal;
import com.dobong.dobong.readinglog.dto.ReadingLogRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogResponse;
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
 * 독서기록 API (F-0401).
 * 요청을 받아 서비스에 넘기고 결과를 응답한다.
 */

@RestController
@RequestMapping("/api/reading-logs")
@RequiredArgsConstructor
public class ReadingLogController {
    private final ReadingLogService readingLogService;

    // 독서기록 작성
    @PostMapping
    public ResponseEntity<ReadingLogResponse> create(
            // 로그인한 사용자
            @AuthenticationPrincipal AuthPrincipal principal,
            // 요청 JSON 받기 + 형식 검증
            @Valid @RequestBody ReadingLogRequest request){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(readingLogService.create(principal.userId(),request));

    }
}
