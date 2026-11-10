package com.dobong.dobong.participant;

import java.time.LocalDate;

/**
 * 참가자가 내서재에 등록한 책 한 권 (사용자별 기록 조회 F-0802)
 *
 * @param registrationNo 소장도서 등록번호. 없으면 null
 */
public record EnrollmentBookRow(
		long enrollmentBookId,
		long bookId,
		String title,
		String author,
		int totalPages,
		boolean libraryOwned,
		boolean children,
		String registrationNo,
		LocalDate loanDate,
		LocalDate returnDate) {
}
