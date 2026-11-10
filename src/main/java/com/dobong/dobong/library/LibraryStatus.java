package com.dobong.dobong.library;

/** 내서재 책의 기록 상태 (책 한 권의 대표 상태, 응답 값이자 필터 값) */
public enum LibraryStatus {
    DRAFT,               // 임시저장
    SUBMITTED,           // 심사 대기
    APPROVED,            // 승인
    REVISION_REQUESTED,  // 보완요청
    SAVED,               // 저장 (서평 500자 미만으로 제출, 심사 안 함)
    NO_RECORD            // 기록 전
}