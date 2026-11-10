package com.dobong.dobong.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 참가 신청 요청
 *
 * 회원은 로그인 토큰으로 정함. 본문으로 회원 id를 받지 않음.
 * 회차는 코스에서 정해지므로 코스 id만 받음.
 */
public record EnrollRequest(
		@NotNull(message = "코스를 선택해 주세요.")
		@Positive(message = "코스를 선택해 주세요.")
		Long courseId) {
}
