package com.dobong.dobong.participant.dto;

import com.dobong.dobong.participant.EnrollmentBookRow;
import com.dobong.dobong.participant.Participant;
import com.dobong.dobong.participant.ParticipantRow;
import com.dobong.dobong.participant.ReadingLogDetailRow;
import com.dobong.dobong.progress.BookProgress;
import com.dobong.dobong.progress.Completion;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.EnrollmentTarget;
import com.dobong.dobong.progress.dto.ProgressResponse.CourseInfo;
import com.dobong.dobong.progress.dto.ProgressResponse.GoalInfo;
import com.dobong.dobong.progress.dto.ProgressResponse.RoundInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 참가자 한 명의 정보, 누적 집계, 등록 도서별 기록 전체 (F-0802). 관리자 전용.
 *
 * @param status     IN_PROGRESS(진행 중) / PENDING_APPROVAL(완주 승인 대기) / COMPLETED(완주)
 * @param approvedAt        완주 승인 시각. 승인 전에는 null
 * @param verifiedLoanCount 완주 승인(F-0803) 때 사서가 확인한 대출 권수. 승인 전에는 null
 * @param approvalNote      완주 승인 사유. 없으면 null
 * @param progress          누적 집계. 모양은 내 진행 현황(F-0601)과 같다
 * @param books             등록 순서. 집계에 들어가지 않는 책도 모두 넣는다
 */
public record EnrollmentDetailResponse(
		long enrollmentId,
		RoundInfo round,
		UserInfo user,
		CourseInfo course,
		CompletionStatus status,
		String certificateNo,
		Instant approvedAt,
		Integer verifiedLoanCount,
		String approvalNote,
		Instant enrolledAt,
		ProgressInfo progress,
		List<BookDetail> books) {

	public static EnrollmentDetailResponse of(Participant participant, List<EnrollmentBookRow> books,
			Map<Long, BookProgress> bookProgress, List<ReadingLogDetailRow> logs) {
		ParticipantRow row = participant.row();
		EnrollmentTarget target = row.target();
		Completion completion = participant.completion();
		Map<Long, List<LogDetail>> logsByBook = logs.stream().collect(Collectors.groupingBy(
				ReadingLogDetailRow::enrollmentBookId, Collectors.mapping(LogDetail::from, Collectors.toList())));

		return new EnrollmentDetailResponse(
				target.enrollmentId(),
				new RoundInfo(target.roundId(), target.year(), target.roundNo()),
				new UserInfo(row.userId(), row.name(), row.phone(), row.loanMemberNo()),
				new CourseInfo(target.courseId(), target.courseName(), target.family()),
				completion.status(),
				row.certificateNo(),
				row.approvedAt(),
				row.verifiedLoanCount(),
				row.approvalNote(),
				row.enrolledAt(),
				new ProgressInfo(
						GoalInfo.from(completion.pages()),
						GoalInfo.from(completion.reviews()),
						GoalInfo.from(completion.books()),
						participant.progress().readKm(),
						participant.progress().completedBooks()),
				books.stream()
						.map(book -> BookDetail.of(book,
								bookProgress.getOrDefault(book.enrollmentBookId(), BookProgress.empty(book.enrollmentBookId())),
								logsByBook.getOrDefault(book.enrollmentBookId(), List.of())))
						.toList());
	}

	/**
	 * @param phone        숫자만 (01012345678)
	 * @param loanMemberNo 대출회원번호. 없으면 null
	 */
	public record UserInfo(long id, String name, String phone, String loanMemberNo) {
	}

	/**
	 * @param pages   누적 쪽수 / 코스 완주 쪽수. 판정에 씀
	 * @param reviews 서평 편수 / 코스 서평 편수. 판정에 씀
	 * @param books   내서재 소장도서 수 / 코스 대출 권수. 참고용이라 판정에 쓰지 않음
	 */
	public record ProgressInfo(GoalInfo pages, GoalInfo reviews, GoalInfo books, double readKm, int completedBooks) {
	}

	/**
	 * 등록 도서 한 권. readPages, completed, reviewCounted는 집계에 들어가는 기록(counted)만으로 계산한다.
	 * 그래서 모든 책의 readPages 합은 progress.pages.current와 같다.
	 *
	 * @param registrationNo 소장도서 등록번호. 없으면 null
	 * @param reviewCounted  이 책의 서평이 서평 편수에 들어가면 true
	 * @param logs           읽은 날짜 순서, 같은 날이면 시작 쪽 순서
	 */
	public record BookDetail(
			long enrollmentBookId,
			long bookId,
			String title,
			String author,
			int totalPages,
			boolean libraryOwned,
			boolean children,
			String registrationNo,
			LocalDate loanDate,
			LocalDate returnDate,
			int readPages,
			boolean completed,
			boolean reviewCounted,
			List<LogDetail> logs) {

		static BookDetail of(EnrollmentBookRow book, BookProgress progress, List<LogDetail> logs) {
			return new BookDetail(book.enrollmentBookId(), book.bookId(), book.title(), book.author(), book.totalPages(),
					book.libraryOwned(), book.children(), book.registrationNo(), book.loanDate(), book.returnDate(),
					progress.readPages(), progress.completed(), progress.reviewCounted(), logs);
		}
	}

	/**
	 * 독서기록 한 건
	 *
	 * @param status       DRAFT / SUBMITTED / APPROVED / REVISION_REQUESTED / SAVED
	 * @param reviewLength 서평 글자 수. 서평이 없으면 0
	 * @param counted      공통 조건(소장도서, 끝 쪽 ≤ 전체 쪽수, 회차 기간)을 만족해 집계에 들어가면 true
	 */
	public record LogDetail(
			long readingLogId,
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

		static LogDetail from(ReadingLogDetailRow row) {
			return new LogDetail(row.readingLogId(), row.readDate(), row.startPage(), row.endPage(), row.status(),
					row.quote(), row.review(), row.reviewLength(), row.librarianComment(), row.submittedAt(),
					row.reviewedAt(), row.counted());
		}
	}
}
