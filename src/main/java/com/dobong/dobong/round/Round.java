package com.dobong.dobong.round;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회차 (rounds 테이블)
 *
 * 지금은 V6 마이그레이션으로만 만들어짐. 관리자 생성·수정은 F-0806에서 추가.
 */
@Entity
@Table(name = "rounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Round {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "round_no", nullable = false)
	private Integer roundNo;

	@Column(nullable = false)
	private Integer year;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	// 비어 있으면 운영 시작일을 신청 시작일로 봄
	@Column(name = "apply_start")
	private LocalDate applyStart;

	// 비어 있으면 운영 종료일을 신청 마감일로 봄
	@Column(name = "apply_end")
	private LocalDate applyEnd;

	public static Round create(int year, int roundNo, LocalDate startDate, LocalDate endDate,
			LocalDate applyStart, LocalDate applyEnd) {
		Round round = new Round();
		round.year = year;
		round.roundNo = roundNo;
		round.startDate = startDate;
		round.endDate = endDate;
		round.applyStart = applyStart;
		round.applyEnd = applyEnd;
		return round;
	}

	// 오늘 참가 신청을 받는지 (시작일·마감일 당일 포함)
	public boolean isApplyOpen(LocalDate today) {
		LocalDate from = applyStart != null ? applyStart : startDate;
		LocalDate to = applyEnd != null ? applyEnd : endDate;
		return !today.isBefore(from) && !today.isAfter(to);
	}

	// 오늘이 운영 기간인지 (시작일·종료일 당일 포함)
	public boolean isInOperation(LocalDate today) {
		return !today.isBefore(startDate) && !today.isAfter(endDate);
	}
}
