package com.dobong.dobong.auth.dto;

/**
 * 회원가입 성공 응답
 * 전화번호, 비밀번호는 넣지 않음.
 */
public record SignupResponse(Long id, String name) {
}
