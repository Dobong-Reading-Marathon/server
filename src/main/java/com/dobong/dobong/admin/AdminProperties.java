package com.dobong.dobong.admin;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 로그인 설정 (application.yml 의 app.admin)
 *
 * 잠금/IP 제한은 참가자 설정(app.auth) 값을 그대로 사용.
 *
 * @param sessionMax 로그인 후 세션 유지 시간 (연장 없음)
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(Duration sessionMax) {

	public AdminProperties {
		if (sessionMax == null || sessionMax.isZero() || sessionMax.isNegative()) {
			throw new IllegalStateException("app.admin.session-max 를 0보다 크게 설정하세요.");
		}
	}
}
