package com.dobong.dobong.participant;

import com.dobong.dobong.participant.dto.ParticipantPageResponse;
import com.dobong.dobong.progress.CompletionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ParticipantController {

	private final ParticipantService participantService;

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
}
