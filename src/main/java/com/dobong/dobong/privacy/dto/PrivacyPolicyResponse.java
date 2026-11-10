package com.dobong.dobong.privacy.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 현재 개인정보 처리방침 전문
 *
 * @param version       회원가입 요청의 privacyPolicyVersion으로 그대로 보냄
 * @param effectiveFrom 시행일
 * @param sections      순서대로. body는 일반 텍스트 (HTML로 넣지 말고 글자로 표시)
 */
public record PrivacyPolicyResponse(int version, LocalDate effectiveFrom, List<Section> sections) {

	public record Section(String title, String body) {
	}
}
