package com.dobong.dobong.admin.dto;

import com.dobong.dobong.admin.Admin;

/**
 * 로그인한 관리자 정보
 * 로그인 아이디는 화면에 필요 없어서 뺌.
 */
public record AdminMeResponse(Long id, String name) {

	public static AdminMeResponse from(Admin admin) {
		return new AdminMeResponse(admin.getId(), admin.getName());
	}
}
