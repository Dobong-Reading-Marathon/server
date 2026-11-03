package com.dobong.dobong.round.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 현재 회차와 코스 목록
 *
 * @param applyStart 참가 신청 시작일 (비어 있으면 운영 시작일 기준)
 * @param applyEnd   참가 신청 마감일 (비어 있으면 운영 종료일 기준)
 * @param applyOpen  오늘(한국 날짜) 참가 신청을 받는지. 화면은 true일 때만 신청 버튼 표시
 * @param courses    코스 순서대로
 */
public record RoundResponse(
		Long id,
		int year,
		int roundNo,
		LocalDate startDate,
		LocalDate endDate,
		LocalDate applyStart,
		LocalDate applyEnd,
		boolean applyOpen,
		List<CourseResponse> courses) {
}
