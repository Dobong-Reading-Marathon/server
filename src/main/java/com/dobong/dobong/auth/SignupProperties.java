package com.dobong.dobong.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원가입 설정 (application.yml 의 app.signup)
 *
 * @param ipLimit  같은 IP에서 ipWindow 동안 허용하는 가입 요청 수
 * @param ipWindow 가입 요청을 세는 시간
 */
@ConfigurationProperties(prefix = "app.signup")
public record SignupProperties(int ipLimit, Duration ipWindow) {

	public SignupProperties {
		if (ipLimit <= 0 || ipWindow == null || ipWindow.isZero() || ipWindow.isNegative()) {
			throw new IllegalStateException("app.signup.ip-limit, app.signup.ip-window 를 0보다 크게 설정하세요.");
		}
	}
}
