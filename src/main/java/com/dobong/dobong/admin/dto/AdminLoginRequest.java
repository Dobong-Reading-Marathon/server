package com.dobong.dobong.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 로그인 요청 (F-0104)
 * toString은 아이디, 비밀번호가 로그에 찍히지 않게 가림.
 */
public record AdminLoginRequest(
		@NotBlank(message = "아이디를 입력해 주세요.")
		@Size(max = 50, message = "아이디가 너무 깁니다.")
		String loginId,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 100, message = "비밀번호가 너무 깁니다.")
		String password) {

	@Override
	public String toString() {
		return "AdminLoginRequest[loginId=***, password=***]";
	}
}
