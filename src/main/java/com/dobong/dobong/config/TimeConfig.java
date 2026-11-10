package com.dobong.dobong.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버의 "오늘"은 항상 한국 시간 기준이다. 클라우드 서버는 보통 UTC로 돌아가므로,
 * LocalDate.now() 대신 이 Clock을 주입받아 LocalDate.now(clock)으로 쓴다. 테스트에서 시간을 고정할 수도 있다.
 */
@Configuration
public class TimeConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}
}
