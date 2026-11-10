package com.dobong.dobong.readinglog.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 독서기록 목록 (내서재 카드형·수첩형). 한 회차 참가 신청의 기록을 모두 돌려준다.
 *
 * @param logs 읽은 날짜가 빠른 순서, 같은 날은 작성 순서(id). 기록이 없으면 빈 목록
 */
public record MyReadingLogsResponse(long enrollmentId, long roundId, List<Item> logs) {

	/**
	 * @param publisher        출판사. 없으면 null
	 * @param publishYear      출간 연도. 없으면 null
	 * @param libraryOwned     도봉도서관 소장도서이면 true
	 * @param status           DRAFT / SUBMITTED / APPROVED / REVISION_REQUESTED / SAVED
	 * @param quote            마음에 드는 글귀. 없으면 null
	 * @param review           서평 본문. 없으면 null
	 * @param reviewLength     서평 글자 수 (공백·줄바꿈 포함, 이모지 1자). 서평이 없으면 0
	 * @param librarianComment 사서 코멘트 (보완요청 사유 등). 없으면 null
	 * @param counted          누적 집계의 공통 조건을 만족해 집계에 들어가면 true
	 */
	public record Item(
			long readingLogId,
			long enrollmentBookId,
			String bookTitle,
			String author,
			String publisher,
			Integer publishYear,
			int totalPages,
			boolean libraryOwned,
			int startPage,
			int endPage,
			LocalDate readDate,
			String status,
			String quote,
			String review,
			int reviewLength,
			String librarianComment,
			OffsetDateTime submittedAt,
			OffsetDateTime reviewedAt,
			boolean counted) {
	}
}
