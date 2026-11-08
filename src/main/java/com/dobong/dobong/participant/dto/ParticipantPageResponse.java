package com.dobong.dobong.participant.dto;

import java.util.List;

/**
 * 참가자 목록 한 페이지 (F-0801)
 *
 * @param page          현재 페이지 번호 (0부터)
 * @param totalElements 조건에 맞는 참가자 수
 * @param content       참가 신청 순서. 조건에 맞는 참가자가 없으면 빈 배열
 */
public record ParticipantPageResponse(
		long roundId,
		int page,
		int size,
		long totalElements,
		int totalPages,
		List<ParticipantResponse> content) {
}
