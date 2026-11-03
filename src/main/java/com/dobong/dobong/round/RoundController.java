package com.dobong.dobong.round;

import com.dobong.dobong.round.dto.RoundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoundController {

	private final RoundService roundService;

	// [F-0201] 현재 회차와 코스. 로그인 없이 호출 가능 (개인정보 없는 공개 정보)
	@GetMapping("/api/rounds/current")
	public RoundResponse current() {
		return roundService.current();
	}
}
