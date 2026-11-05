package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompletionTest {

	/** 하프코스: 17,500쪽, 대출 50권, 서평 10편 */
	private static EnrollmentTarget half(boolean completed, int libraryBooks) {
		return new EnrollmentTarget(1, 1, 2027, 1, 3, "하프코스", false, 17500, 50, 10, completed, libraryBooks);
	}

	private static ReadingProgress progress(int readPages, int reviewCount) {
		return new ReadingProgress(1, readPages, 0, reviewCount);
	}

	@Test
	@DisplayName("쪽수와 서평 편수를 모두 채우면 완주 승인 대기")
	void pendingWhenPagesAndReviewsMet() {
		Completion completion = Completion.of(half(false, 50), progress(17500, 10));

		assertThat(completion.status()).isEqualTo(CompletionStatus.PENDING_APPROVAL);
	}

	@Test
	@DisplayName("쪽수만 채우거나 서평만 채우면 진행 중")
	void inProgressWhenOnlyOneMet() {
		assertThat(Completion.of(half(false, 50), progress(17500, 9)).status()).isEqualTo(CompletionStatus.IN_PROGRESS);
		assertThat(Completion.of(half(false, 50), progress(17499, 10)).status()).isEqualTo(CompletionStatus.IN_PROGRESS);
	}

	@Test
	@DisplayName("권수는 판정에 쓰지 않는다: 소장도서가 기준보다 적어도 승인 대기")
	void booksNotJudged() {
		Completion completion = Completion.of(half(false, 3), progress(17500, 10));

		assertThat(completion.status()).isEqualTo(CompletionStatus.PENDING_APPROVAL);
		assertThat(completion.books()).isEqualTo(new Goal(3, 50));
	}

	@Test
	@DisplayName("사서가 완주를 승인했으면 기준과 상관없이 완주")
	void completedWhenApproved() {
		Completion completion = Completion.of(half(true, 0), progress(100, 0));

		assertThat(completion.status()).isEqualTo(CompletionStatus.COMPLETED);
	}

	@Test
	@DisplayName("쪽수, 서평, 권수에 집계 값과 코스 기준이 들어간다")
	void goals() {
		Completion completion = Completion.of(half(false, 18), progress(12450, 7));

		assertThat(completion.pages()).isEqualTo(new Goal(12450, 17500));
		assertThat(completion.reviews()).isEqualTo(new Goal(7, 10));
		assertThat(completion.books()).isEqualTo(new Goal(18, 50));
	}
}
