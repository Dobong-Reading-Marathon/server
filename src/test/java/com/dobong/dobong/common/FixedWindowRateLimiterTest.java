package com.dobong.dobong.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

	// 테스트에서 시간을 직접 움직이는 시계
	private static Clock movableClock(AtomicLong millis) {
		return new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneId.of("UTC");
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return Instant.ofEpochMilli(millis.get());
			}
		};
	}

	@Test
	@DisplayName("한도까지는 허용하고, 넘으면 막고, 시간이 지나면 다시 허용한다")
	void limitsWithinWindow() {
		AtomicLong now = new AtomicLong(0);
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(10), movableClock(now));

		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
		assertThat(limiter.isBlocked("1.1.1.1")).isTrue();

		// 다른 IP는 영향 없음
		assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();

		now.addAndGet(Duration.ofMinutes(10).toMillis());
		assertThat(limiter.isBlocked("1.1.1.1")).isFalse();
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
	}

	@Test
	@DisplayName("record는 한도와 상관없이 횟수를 늘린다")
	void recordCounts() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(10), movableClock(new AtomicLong()));

		limiter.record("1.1.1.1");
		assertThat(limiter.isBlocked("1.1.1.1")).isFalse();
		limiter.record("1.1.1.1");
		assertThat(limiter.isBlocked("1.1.1.1")).isTrue();
	}

	@Test
	@DisplayName("동시에 요청이 몰려도 한도보다 많이 허용하지 않는다")
	void concurrentRequestsDoNotExceedLimit() throws Exception {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(10, Duration.ofMinutes(10), movableClock(new AtomicLong()));
		ExecutorService pool = Executors.newFixedThreadPool(16);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			results.add(pool.submit(() -> {
				start.await();
				return limiter.tryAcquire("1.1.1.1");
			}));
		}
		start.countDown();

		int allowed = 0;
		for (Future<Boolean> r : results) {
			if (r.get(5, TimeUnit.SECONDS)) {
				allowed++;
			}
		}
		pool.shutdown();
		assertThat(allowed).isEqualTo(10);
	}
}
