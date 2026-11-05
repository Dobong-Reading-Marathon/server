package com.dobong.dobong.progress;

/**
 * 참가자 한 명의 완주 판정 결과 (F-0601). DB에 접근하지 않는 순수 계산이다.
 *
 * 판정 기준은 쪽수와 서평 편수다. 대출 권수는 앱이 셀 수 없어서 판정에 넣지 않고, 사서가 완주를 승인할 때 확인한다
 * (도서관 완주 기준 문서). 그래서 books는 화면에 보여 주는 참고값이다.
 *
 * @param pages   누적 쪽수 / 코스 완주 쪽수
 * @param reviews 서평 편수 / 코스 서평 편수
 * @param books   내서재 소장도서 수 / 코스 대출 권수. 판정에 쓰지 않음
 */
public record Completion(CompletionStatus status, Goal pages, Goal reviews, Goal books) {

	public static Completion of(EnrollmentTarget target, ReadingProgress progress) {
		Goal pages = new Goal(progress.readPages(), target.targetPages());
		Goal reviews = new Goal(progress.reviewCount(), target.targetReviews());
		Goal books = new Goal(target.libraryBooks(), target.targetBooks());
		return new Completion(judge(target.completed(), pages, reviews), pages, reviews, books);
	}

	private static CompletionStatus judge(boolean approvedByLibrarian, Goal pages, Goal reviews) {
		if (approvedByLibrarian) {
			return CompletionStatus.COMPLETED;
		}
		return pages.met() && reviews.met() ? CompletionStatus.PENDING_APPROVAL : CompletionStatus.IN_PROGRESS;
	}
}
