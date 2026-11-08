package com.dobong.dobong.admin.submission;

import com.dobong.dobong.admin.AdminPrincipal;
import com.dobong.dobong.admin.submission.AdminSubmissionDtos.RevisionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            return error(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "잘못된 상태 값입니다.");
        }
        return ResponseEntity.ok(service.inbox(status, keyword));
    }

    // [02 추가] 승인
    @PostMapping("/api/admin/submissions/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @AuthenticationPrincipal AdminPrincipal admin) {
        return respond(service.approve(id, admin.adminId()));
    }

    // [02 추가] 보완요청 (사유 필수)
    @PostMapping("/api/admin/submissions/{id}/revision-request")
    public ResponseEntity<?> requestRevision(@PathVariable Long id,
                                             @Valid @RequestBody RevisionRequest body,
                                             @AuthenticationPrincipal AdminPrincipal admin) {
        return respond(service.requestRevision(id, admin.adminId(), body.comment()));
    }

    // [02 추가] 승인 취소 (심사 대기로 되돌림)
    @PostMapping("/api/admin/submissions/{id}/cancel-approval")
    public ResponseEntity<?> cancelApproval(@PathVariable Long id) {
        return respond(service.cancelApproval(id));
    }

    // [02 추가] 처리 결과를 HTTP 응답으로 변환
    private ResponseEntity<?> respond(AdminSubmissionService.Result result) {
        return switch (result) {
            case OK -> ResponseEntity.noContent().build();
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND",
                    "해당 기록을 찾을 수 없습니다.");
            case INVALID_STATE -> error(HttpStatus.CONFLICT, "INVALID_STATE",
                    "현재 상태에서는 처리할 수 없습니다. 목록을 새로고침해 주세요.");
        };
    }

    // [02 추가] 팀 공통 오류 형식 JSON (기존 inbox의 오류도 이걸로 통일)
    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        String json = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":[]}";
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON).body(json);
    }
}