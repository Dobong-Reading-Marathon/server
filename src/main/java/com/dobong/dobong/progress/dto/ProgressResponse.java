package com.dobong.dobong.progress.dto;

import com.dobong.dobong.progress.Completion;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.EnrollmentTarget;
import com.dobong.dobong.progress.Goal;
import com.dobong.dobong.progress.ReadingProgress;

/**
 * 내 완주 진행 현황 (F-0601). 홈 대시보드(F-0501)와 13 완주 인증 화면에서 쓴다.
 *
 * @param status         IN_PROGRESS(기록 중) / PENDING_APPROVAL(완주 승인 대기) / COMPLETED(완주)
 * @param pages          누적 쪽수. 판정에 씀
 * @param reviews        서평 편수 (사서 승인, 500자 이상, 책당 1편). 판정에 씀
 * @param books          내서재에 등록한 소장도서 수. 참고용이라 판정에 쓰지 않음 (공식 대출 권수는 도서관에서 확인)
 * @param readKm         누적 쪽수를 km로 환산한 값
 * @param completedBooks 완독 권수. 동기 부여용이라 판정에 쓰지 않음
 */
public record ProgressResponse(
		long enrollmentId,
		RoundInfo round,
		CourseInfo course,
		CompletionStatus status,
		GoalInfo pages,
		GoalInfo reviews,
		GoalInfo books,
		double readKm,
		int completedBooks) {

	public static ProgressResponse of(EnrollmentTarget target, ReadingProgress progress) {
		Completion completion = Completion.of(target, progress);
		return new ProgressResponse(
				target.enrollmentId(),
				new RoundInfo(target.roundId(), target.year(), target.roundNo()),
				new CourseInfo(target.courseId(), target.courseName(), target.family()),
				completion.status(),
				GoalInfo.from(completion.pages()),
				GoalInfo.from(completion.reviews()),
				GoalInfo.from(completion.books()),
				progress.readKm(),
				progress.completedBooks());
	}

	public record RoundInfo(long id, int year, int roundNo) {
	}

	public record CourseInfo(long id, String name, boolean family) {
	}

	/**
	 * @param remaining 남은 값. 기준을 넘겼으면 0
	 * @param percent   진행률(%). 내림, 최대 100
	 */
	public record GoalInfo(int current, int target, int remaining, int percent) {

		public static GoalInfo from(Goal goal) {
			return new GoalInfo(goal.current(), goal.target(), goal.remaining(), goal.percent());
		}
	}
}
