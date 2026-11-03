package com.dobong.dobong.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 설정 (application.yml 의 app.auth)
 *
 * @param rememberIdle      로그인 상태 유지: 이 기간 동안 안 쓰면 로그아웃
 * @param rememberMax       로그인 상태 유지: 로그인 후 최대 유지 기간 (연장해도 못 넘음)
 * @param sessionMax        유지 안 함: 로그인 후 최대 유지 기간 (연장 없음)
 * @param maxFailedAttempts 같은 계정 연속 실패 허용 횟수. 넘으면 잠금
 * @param lockDuration      잠금 시간
 * @param ipFailureLimit    같은 IP에서 ipFailureWindow 동안 허용하는 로그인 실패 수
 * @param ipFailureWindow   IP별 실패를 세는 시간
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		Duration rememberIdle,
		Duration rememberMax,
		Duration sessionMax,
		int maxFailedAttempts,
		Duration lockDuration,
		int ipFailureLimit,
		Duration ipFailureWindow) {

	public AuthProperties {
		if (!positive(rememberIdle) || !positive(rememberMax) || !positive(sessionMax) || !positive(lockDuration)
				|| !positive(ipFailureWindow) || maxFailedAttempts <= 0 || ipFailureLimit <= 0) {
			throw new IllegalStateException("app.auth.* 값을 모두 0보다 크게 설정하세요.");
		}
		if (rememberIdle.compareTo(rememberMax) > 0) {
			throw new IllegalStateException("app.auth.remember-idle 은 remember-max 보다 길 수 없습니다.");
		}
	}

	private static boolean positive(Duration d) {
		return d != null && !d.isZero() && !d.isNegative();
	}
}
