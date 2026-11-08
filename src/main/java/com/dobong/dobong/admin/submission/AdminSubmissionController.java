package com.dobong.dobong.admin.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminSubmissionController {

    private final AdminSubmissionService service;

    // 접수함 목록 (관리자만, SecurityConfig의 /api/admin/** 규칙)
    @GetMapping("/api/admin/submissions")
    public ResponseEntity<?> inbox(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String keyword) {
        if (!service.isValidStatus(status)) {
            // 팀 공통 오류 형식과 같은 JSON
            String json = "{\"code\":\"INVALID_STATUS\",\"message\":\"잘못된 상태 값입니다.\",\"fieldErrors\":[]}";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return ResponseEntity.ok(service.inbox(status, keyword));
    }
}