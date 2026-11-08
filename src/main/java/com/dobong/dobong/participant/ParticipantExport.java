package com.dobong.dobong.participant;

/**
 * 명단 다운로드 결과 (F-0804)
 *
 * @param fileName participants-{연도}-{회차 번호}.xlsx
 * @param content  xlsx 파일 내용
 * @param count    파일에 넣은 참가자 수
 */
public record ParticipantExport(String fileName, byte[] content, int count) {
}
