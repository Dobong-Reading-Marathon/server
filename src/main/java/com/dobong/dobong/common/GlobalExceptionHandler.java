package com.dobong.dobong.common;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 컨트롤러에서 생긴 오류를 ApiErrorResponse 형식으로 바꾼다.
 *
 */
@Slf4j
@RestControllerAdvice
public class  GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApi(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		// 사용자가 보낸 값(rejectedValue)은 넣지 않는다. 비밀번호 같은 값이 응답으로 되돌아가지 않게 한다.
		List<ApiErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> new ApiErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
				.toList();
		return ResponseEntity.badRequest()
				.body(new ApiErrorResponse("INVALID_INPUT", "입력값을 확인해 주세요.", fields));
	}

	/** JSON 문법이 틀렸거나, 숫자 칸에 글자를 넣는 등 형식이 맞지 않는 요청 */
	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiErrorResponse> handleUnreadable(Exception e) {
		return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_INPUT", "요청 형식이 올바르지 않습니다."));
	}

	/**
	 * DB 제약조건(UNIQUE 등)이 막은 경우. 같은 요청이 동시에 두 번 들어왔을 때 주로 생긴다.
	 * PostgreSQL 오류 메시지에는 "Key (phone)=(01012345678) already exists"처럼 실제 값이 들어 있으므로 응답은 물론 로그에도 메시지를 남기지 않고 예외 종류만 남긴다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
		log.warn("DB 제약조건 위반: {}", e.getMostSpecificCause().getClass().getSimpleName());
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiErrorResponse.of("CONFLICT", "이미 처리된 요청이거나 다른 데이터와 충돌합니다."));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleOthers(Exception e) {
		// Spring MVC가 만드는 오류(404, 405, 415, 필수 파라미터 누락 등)는 ErrorResponse를 구현하고 상태 코드를 갖고 있다.
		// 이걸 500으로 바꾸면 프론트가 서버 장애로 오해하므로 원래 상태 코드를 지킨다.
		if (e instanceof org.springframework.web.ErrorResponse springError) {
			HttpStatusCode status = springError.getStatusCode();
			if (status.is4xxClientError()) {
				return ResponseEntity.status(status).body(clientError(status));
			}
		}
		log.error("처리하지 못한 오류", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.of("INTERNAL_ERROR", "일시적인 오류입니다. 잠시 후 다시 시도해 주세요."));
	}

	private static ApiErrorResponse clientError(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> ApiErrorResponse.of("NOT_FOUND", "찾을 수 없습니다.");
			case 405 -> ApiErrorResponse.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
			case 415 -> ApiErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "JSON 형식으로 보내 주세요.");
			default -> ApiErrorResponse.of("BAD_REQUEST", "요청을 처리할 수 없습니다.");
		};
	}
}
