package com.dobong.dobong.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 (F-0102)
 * toString은 전화번호, 비밀번호가 로그에 찍히지 않게 가림.
 *
 * @param rememberMe 로그인 상태 유지. 보내지 않으면 false (12시간)
 */
public record LoginRequest(
		@NotBlank(message = "전화번호를 입력해 주세요.")
		@Size(max = 20, message = "전화번호 형식이 올바르지 않습니다.")
		String phone,

		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(max = 100, message = "비밀번호가 너무 깁니다.")
		String password,

		Boolean rememberMe) {

	@Override
	public String toString() {
		return "LoginRequest[phone=***, password=***, rememberMe=" + rememberMe + "]";
	}
}
