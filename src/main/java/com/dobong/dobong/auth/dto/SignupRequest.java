package com.dobong.dobong.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 (F-0101)
 *
 * 여기서는 빈 값, 길이, 글자 종류만 검사. 전화번호 형식과 비밀번호 규칙은 서비스에서 검사.
 * toString은 개인정보가 로그에 찍히지 않게 가림.
 */
public record SignupRequest(
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
		@Pattern(regexp = "^[^\\p{Cntrl}<>]*$", message = "이름에 사용할 수 없는 문자가 있습니다.")
		String name,

		@NotBlank(message = "전화번호를 입력해 주세요.")
		@Size(max = 20, message = "전화번호 형식이 올바르지 않습니다.")
		String phone,

		@NotNull(message = "비밀번호를 입력해 주세요.")
		@Size(max = 100, message = "비밀번호가 너무 깁니다.")
		String password,

		@Size(max = 30, message = "대출회원번호는 30자 이하로 입력해 주세요.")
		@Pattern(regexp = "^[0-9A-Za-z\\-]*$", message = "대출회원번호는 숫자, 영문, 하이픈만 쓸 수 있습니다.")
		String loanMemberNo,

		@NotNull(message = "개인정보 수집·이용에 동의해 주세요.")
		@AssertTrue(message = "개인정보 수집·이용에 동의해야 가입할 수 있습니다.")
		Boolean privacyAgreed,

		// 독서 독려 알림 수신 동의 (선택). 보내지 않으면 동의 안 함으로 본다
		Boolean reminderAgreed,

		// 화면에 보여 준 처리방침 버전 (GET /api/privacy-policy 의 version). 현재 버전과 다르면 409 (F-0103)
		@NotNull(message = "개인정보 처리방침 버전이 필요합니다.")
		@Positive(message = "개인정보 처리방침 버전이 올바르지 않습니다.")
		Integer privacyPolicyVersion) {

	@Override
	public String toString() {
		return "SignupRequest[name=***, phone=***, password=***, loanMemberNo=***, privacyAgreed=" + privacyAgreed
				+ ", reminderAgreed=" + reminderAgreed + ", privacyPolicyVersion=" + privacyPolicyVersion + "]";
	}
}
