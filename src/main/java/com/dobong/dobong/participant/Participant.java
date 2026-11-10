package com.dobong.dobong.participant;

import com.dobong.dobong.progress.Completion;
import com.dobong.dobong.progress.ReadingProgress;

/**
 * 참가자 한 명의 회원 정보, 누적 집계(F-0502), 완주 판정(F-0601). 참가자 목록과 명단 다운로드가 같이 쓴다.
 */
public record Participant(ParticipantRow row, ReadingProgress progress, Completion completion) {

	public static Participant of(ParticipantRow row, ReadingProgress progress) {
		return new Participant(row, progress, Completion.of(row.target(), progress));
	}
}
