package com.dobong.dobong.common;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(IP 등)마다 정해진 시간 동안의 요청 횟수를 카운트
 *
 * 서버 메모리에만 저장하므로 서버가 다시 켜지면 초기화됨.
 * 서버 1대 기준, 서버를 여러 대로 늘리면 DB 등 공용 저장소 방식으로 바꿔야 함.
 */
public class FixedWindowRateLimiter {

	// 기록이 이만큼 쌓이면 지난 기록을 정리 (IP를 바꿔 가며 요청해도 메모리가 계속 늘지 않게)
	private static final int CLEANUP_THRESHOLD = 10_000;

	private final int limit;
	private final long windowMillis;
	private final Clock clock;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	private record Window(long startMillis, int count) {
	}

	public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
		if (limit <= 0 || window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("limit과 window는 0보다 커야 합니다.");
		}
		this.limit = limit;
		this.windowMillis = window.toMillis();
		this.clock = clock;
	}

	// 한도 안이면 횟수를 1 늘리고 true, 이미 한도에 닿았으면 늘리지 않고 false
	public boolean tryAcquire(String key) {
		long now = clock.millis();
		cleanupIfLarge(now);
		boolean[] allowed = {false};
		// compute는 같은 키에 대해 한 번에 하나씩만 실행되므로, 동시에 요청이 와도 한도를 넘겨 허용하지 않음
		windows.compute(key, (k, w) -> {
			if (w == null || isExpired(w, now)) {
				allowed[0] = true;
				return new Window(now, 1);
			}
			if (w.count() < limit) {
				allowed[0] = true;
				return new Window(w.startMillis(), w.count() + 1);
			}
			return w;
		});
		return allowed[0];
	}

	// 이미 한도에 닿았는지만 확인 (횟수는 늘리지 않음)
	public boolean isBlocked(String key) {
		Window w = windows.get(key);
		return w != null && !isExpired(w, clock.millis()) && w.count() >= limit;
	}

	// 횟수를 1 늘림 (한도 확인 없이) 로그인 실패처럼 결과를 보고 나서 셀 때 사용
	public void record(String key) {
		long now = clock.millis();
		cleanupIfLarge(now);
		windows.compute(key, (k, w) -> (w == null || isExpired(w, now))
				? new Window(now, 1)
				: new Window(w.startMillis(), w.count() + 1));
	}

	private boolean isExpired(Window w, long now) {
		return now - w.startMillis() >= windowMillis;
	}

	private void cleanupIfLarge(long now) {
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.entrySet().removeIf(e -> isExpired(e.getValue(), now));
		}
	}
}
