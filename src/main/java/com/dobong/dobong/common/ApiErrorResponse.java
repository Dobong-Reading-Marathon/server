package com.dobong.dobong.common;

import java.util.List;

/**
 * 모든 오류 응답의 형식.
 *
 */
public record ApiErrorResponse(String code, String message, List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(code, message, List.of());
	}
}
