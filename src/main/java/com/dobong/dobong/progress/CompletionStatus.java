package com.dobong.dobong.progress;

/**
 * 완주 단계 (F-0601). 13 완주 인증 화면의 단계와 같다.
 *
 * COMPLETED만 사서 승인(F-0803)으로 DB(enrollments.status)에 남고, 나머지는 조회할 때마다 판정한다.
 * 그래서 승인 대기였다가 기록이 수정·삭제되어 기준 아래로 내려가면 다시 IN_PROGRESS가 된다.
 */
public enum CompletionStatus {

	/** 기록을 쌓는 중 */
	IN_PROGRESS,

	/** 쪽수와 서평 편수 기준을 모두 채움. 사서 승인 대기 */
	PENDING_APPROVAL,

	/** 사서가 완주를 승인함 */
	COMPLETED
}
