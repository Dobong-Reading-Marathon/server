package com.dobong.dobong.participant;

import com.dobong.dobong.progress.CompletionStatus;

/**
 * 참가자 목록(F-0801)과 명단 다운로드(F-0804)의 검색 조건. 모두 선택이고, null이면 거르지 않는다.
 *
 * @param keyword  이름 또는 전화번호 일부
 * @param courseId 코스 id
 * @param status   완주 상태
 */
public record ParticipantFilter(String keyword, Long courseId, CompletionStatus status) {
}
