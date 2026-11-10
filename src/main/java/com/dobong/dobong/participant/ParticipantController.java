package com.dobong.dobong.participant;

import com.dobong.dobong.admin.AdminPrincipal;
import com.dobong.dobong.participant.dto.CompletionApprovalRequest;
import com.dobong.dobong.participant.dto.CompletionApprovalResponse;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse;
import com.dobong.dobong.participant.dto.ParticipantPageResponse;
import com.dobong.dobong.progress.CompletionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ParticipantController {

	static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final ParticipantService participantService;
	private final CompletionApprovalService completionApprovalService;

	// [F-0801] 회차 참가자 목록. 관리자만 (SecurityConfig의 /api/admin/** 규칙)
	@GetMapping("/api/admin/rounds/{roundId}/enrollments")
	public ParticipantPageResponse participants(
			@PathVariable long roundId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long courseId,
			@RequestParam(required = false) CompletionStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + ParticipantService.DEFAULT_SIZE) int size) {
		return participantService.getPage(roundId, new ParticipantFilter(keyword, courseId, status), page, size);
	}

	// [F-0804] 명단 다운로드 (xlsx). 관리자만. 필터는 참가자 목록과 같고 페이지 없이 전부
	// 파일을 다 만든 뒤 보내므로, 실패하면 파일 대신 JSON 오류가 나간다
	@GetMapping("/api/admin/rounds/{roundId}/enrollments/export")
	public ResponseEntity<byte[]> export(
			@PathVariable long roundId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long courseId,
			@RequestParam(required = false) CompletionStatus status,
			@AuthenticationPrincipal AdminPrincipal admin) {
		ParticipantExport export = participantService.export(roundId, new ParticipantFilter(keyword, courseId, status));
		// 개인정보가 든 파일이라 누가 언제 받았는지 남긴다. 이름, 전화번호는 로그에 넣지 않는다
		log.info("명단 다운로드: adminId={}, roundId={}, count={}", admin.adminId(), roundId, export.count());
		return ResponseEntity.ok()
				.contentType(XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(export.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(export.content());
	}

	// [F-0803] 완주 승인. 관리자만. 인증서 번호를 붙이고 완주(COMPLETED)로 바꾼다
	@PostMapping("/api/admin/enrollments/{enrollmentId}/completion-approval")
	public CompletionApprovalResponse approveCompletion(
			@PathVariable long enrollmentId,
			@Valid @RequestBody CompletionApprovalRequest request,
			@AuthenticationPrincipal AdminPrincipal admin) {
		return completionApprovalService.approve(enrollmentId, admin.adminId(), request);
	}

	// [F-0802] 사용자별 기록 조회. 관리자만 (SecurityConfig의 /api/admin/** 규칙)
	@GetMapping("/api/admin/enrollments/{enrollmentId}")
	public EnrollmentDetailResponse detail(@PathVariable long enrollmentId) {
		return participantService.getDetail(enrollmentId);
	}
}
