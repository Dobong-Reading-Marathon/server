package com.dobong.dobong.participant.dto;

import com.dobong.dobong.participant.Participant;
import com.dobong.dobong.participant.ParticipantRow;
import com.dobong.dobong.progress.Completion;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.EnrollmentTarget;
import com.dobong.dobong.progress.dto.ProgressResponse.CourseInfo;
import com.dobong.dobong.progress.dto.ProgressResponse.GoalInfo;
import java.time.Instant;

/**
 * 참가자 목록의 한 명 (F-0801). 집계 값의 모양은 내 진행 현황(F-0601)과 같다.
 *
 * @param phone          전화번호 (숫자만). 관리자 화면이라 가리지 않는다
 * @param status         IN_PROGRESS(진행 중) / PENDING_APPROVAL(완주 승인 대기) / COMPLETED(완주)
 * @param pages          누적 쪽수 / 코스 완주 쪽수. 판정에 씀
 * @param reviews        서평 편수 / 코스 서평 편수. 판정에 씀
 * @param books          내서재 소장도서 수 / 코스 대출 권수. 참고용이라 판정에 쓰지 않음
 * @param certificateNo  인증서 번호. 완주 승인 전에는 null
 * @param enrolledAt     참가 신청 시각
 */
public record ParticipantResponse(
		long enrollmentId,
		long userId,
		String name,
		String phone,
		CourseInfo course,
		CompletionStatus status,
		GoalInfo pages,
		GoalInfo reviews,
		GoalInfo books,
		double readKm,
		int completedBooks,
		String certificateNo,
		Instant enrolledAt) {

	public static ParticipantResponse from(Participant participant) {
		ParticipantRow row = participant.row();
		EnrollmentTarget target = row.target();
		Completion completion = participant.completion();
		return new ParticipantResponse(
				target.enrollmentId(),
				row.userId(),
				row.name(),
				row.phone(),
				new CourseInfo(target.courseId(), target.courseName(), target.family()),
				completion.status(),
				GoalInfo.from(completion.pages()),
				GoalInfo.from(completion.reviews()),
				GoalInfo.from(completion.books()),
				participant.progress().readKm(),
				participant.progress().completedBooks(),
				row.certificateNo(),
				row.enrolledAt());
	}
}
