package com.dobong.dobong.participant;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 독서기록 한 건 (사용자별 기록 조회 F-0802)
 *
 * @param reviewLength 서평 글자 수. 서평 작성(F-0402), 집계와 같이 DB char_length로 센다. 서평이 없으면 0
 * @param counted      공통 조건(소장도서, 끝 쪽 ≤ 전체 쪽수, 회차 기간)을 만족해 집계에 들어가면 true
 */
public record ReadingLogDetailRow(
		long readingLogId,
		long enrollmentBookId,
		LocalDate readDate,
		int startPage,
		int endPage,
		String status,
		String quote,
		String review,
		int reviewLength,
		String librarianComment,
		Instant submittedAt,
		Instant reviewedAt,
		boolean counted) {
}
