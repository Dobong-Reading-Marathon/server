package com.dobong.dobong.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오류가 공통 형식으로 바뀌는지, 내부 정보와 개인정보가 응답에 새지 않는지 확인한다.
 * 서버 전체를 띄우지 않고 테스트용 컨트롤러 하나와 GlobalExceptionHandler만 붙여서 빠르게 돌린다.
 */
class GlobalExceptionHandlerTest {

	@RestController
	static class TestController {

		record Body(@NotBlank(message = "이름을 입력해 주세요.") String name) {
		}

		@GetMapping("/t/api-exception")
		String apiException() {
			throw ApiException.conflict("DUPLICATE", "이미 있습니다.");
		}

		@PostMapping("/t/validate")
		String validate(@Valid @RequestBody Body body) {
			return "ok";
		}

		@GetMapping("/t/number/{id}")
		String number(@PathVariable Long id) {
			return "ok";
		}

		@GetMapping("/t/integrity")
		String integrity() {
			throw new DataIntegrityViolationException("Key (phone)=(01012345678) already exists.");
		}

		@GetMapping("/t/boom")
		String boom() {
			throw new IllegalStateException("내부 비밀 정보 SELECT * FROM users");
		}
	}

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	@DisplayName("ApiException은 지정한 상태 코드, code, message로 응답한다")
	void apiException() throws Exception {
		mvc.perform(get("/t/api-exception"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE"))
				.andExpect(jsonPath("$.message").value("이미 있습니다."))
				.andExpect(jsonPath("$.fieldErrors").isEmpty());
	}

	@Test
	@DisplayName("입력값 검사 실패는 400과 칸별 오류를 주고, 보낸 값은 되돌려 보내지 않는다")
	void validation() throws Exception {
		mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("이름을 입력해 주세요."))
				.andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
	}

	@Test
	@DisplayName("JSON 문법 오류, 숫자 칸에 글자는 400")
	void unreadable() throws Exception {
		mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{broken"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		mvc.perform(get("/t/number/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("Spring이 만든 4xx(405, 415)는 500으로 바뀌지 않고 원래 상태 코드를 유지한다")
	void keepsSpringClientErrors() throws Exception {
		mvc.perform(delete("/t/api-exception"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(post("/t/validate").contentType(MediaType.TEXT_PLAIN).content("hello"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("DB 제약조건 위반은 409이고, DB 메시지 속 전화번호가 응답에 나가지 않는다")
	void integrity() throws Exception {
		mvc.perform(get("/t/integrity"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"))
				.andExpect(content().string(not(containsString("01012345678"))));
	}

	@Test
	@DisplayName("예상하지 못한 오류는 500과 일반 문구만 보내고 내부 정보를 숨긴다")
	void unexpected() throws Exception {
		mvc.perform(get("/t/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("SELECT"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}
}
