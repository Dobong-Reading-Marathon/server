package com.dobong.dobong.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionServiceTest {

	private static final Instant START = Instant.parse("2027-01-05T01:00:00Z");

	private final AtomicReference<Instant> now = new AtomicReference<>(START);
	private final AuthSessionRepository repository = mock(AuthSessionRepository.class);
	private final AuthProperties props = new AuthProperties(Duration.ofDays(30), Duration.ofDays(90),
			Duration.ofHours(12), 5, Duration.ofMinutes(15), 30, Duration.ofMinutes(10));
	private final SessionService service = new SessionService(repository, props, movableClock());

	// 테스트에서 시간을 직접 움직이는 시계
	private Clock movableClock() {
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
				return now.get();
			}
		};
	}

	// 세션을 발급하고, DB에 저장된 세션(해시)과 원본 토큰을 함께 돌려줌
	private AuthSession issue(boolean rememberMe, String[] tokenOut) {
		SessionService.IssuedSession issued = service.issue(1L, rememberMe);
		ArgumentCaptor<AuthSession> saved = ArgumentCaptor.forClass(AuthSession.class);
		verify(repository).save(saved.capture());
		tokenOut[0] = issued.token();
		AuthSession session = saved.getValue();
		when(repository.findByTokenHash(SessionTokens.hash(issued.token()))).thenReturn(Optional.of(session));
		return session;
	}

	@Test
	@DisplayName("발급하면 DB에는 해시만 저장하고, 만료된 세션을 먼저 정리한다")
	void issueStoresHashOnly() {
		String[] token = new String[1];
		AuthSession session = issue(true, token);

		verify(repository).deleteAllExpired(START);
		assertThat(session.getTokenHash()).isEqualTo(SessionTokens.hash(token[0])).isNotEqualTo(token[0]);
		assertThat(session.getUserId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("유지(체크): 만료 30일, 최대 90일 / 유지 안 함: 만료 12시간")
	void expiryByRememberMe() {
		String[] token = new String[1];
		AuthSession remember = issue(true, token);
		assertThat(remember.getExpiresAt()).isEqualTo(START.plus(Duration.ofDays(30)));
		assertThat(remember.getAbsoluteExpiresAt()).isEqualTo(START.plus(Duration.ofDays(90)));

		AuthSession shortSession = AuthSession.create("hash", 1L, false, START, props);
		assertThat(shortSession.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(12)));
		assertThat(shortSession.getAbsoluteExpiresAt()).isEqualTo(START.plus(Duration.ofHours(12)));
	}

	@Test
	@DisplayName("유효한 토큰이면 회원 id와 세션 id를 돌려준다")
	void authenticateValid() {
		String[] token = new String[1];
		issue(true, token);

		Optional<AuthPrincipal> principal = service.authenticate(token[0]);

		assertThat(principal).isPresent();
		assertThat(principal.get().userId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("5분 안에 다시 쓰면 연장하지 않고, 5분이 지나면 30일 연장한다")
	void touchesAfterInterval() {
		String[] token = new String[1];
		AuthSession session = issue(true, token);

		now.set(START.plus(Duration.ofMinutes(4)));
		service.authenticate(token[0]);
		assertThat(session.getLastUsedAt()).isEqualTo(START);

		Instant later = START.plus(Duration.ofDays(10));
		now.set(later);
		service.authenticate(token[0]);
		assertThat(session.getLastUsedAt()).isEqualTo(later);
		assertThat(session.getExpiresAt()).isEqualTo(later.plus(Duration.ofDays(30)));
	}

	@Test
	@DisplayName("계속 써도 최대 90일을 넘겨 연장하지 않는다")
	void neverExceedsMax() {
		String[] token = new String[1];
		AuthSession session = issue(true, token);

		now.set(START.plus(Duration.ofDays(80)).minusSeconds(1));
		session.touch(now.get(), props.rememberIdle()); // 80일 동안 계속 썼다고 가정
		assertThat(session.getExpiresAt()).isEqualTo(START.plus(Duration.ofDays(90)));
	}

	@Test
	@DisplayName("유지 안 함 세션은 써도 연장되지 않는다")
	void shortSessionNotExtended() {
		AuthSession session = AuthSession.create("hash", 1L, false, START, props);

		session.touch(START.plus(Duration.ofHours(6)), props.rememberIdle());

		assertThat(session.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(12)));
	}

	@Test
	@DisplayName("만료된 토큰은 거부하고 그 세션을 지운다")
	void expiredRejected() {
		String[] token = new String[1];
		AuthSession session = issue(false, token);

		now.set(START.plus(Duration.ofHours(12)));

		assertThat(service.authenticate(token[0])).isEmpty();
		verify(repository).delete(session);
	}

	@Test
	@DisplayName("없는 토큰, 빈 토큰, 너무 긴 토큰은 거부한다 (긴 토큰은 DB 조회도 안 함)")
	void invalidTokens() {
		when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThat(service.authenticate("not-a-token")).isEmpty();
		assertThat(service.authenticate("")).isEmpty();
		assertThat(service.authenticate(null)).isEmpty();

		String tooLong = "a".repeat(SessionService.MAX_TOKEN_LENGTH + 1);
		assertThat(service.authenticate(tooLong)).isEmpty();
		verify(repository, never()).findByTokenHash(SessionTokens.hash(tooLong));
	}

	@Test
	@DisplayName("로그아웃하면 그 세션을 지운다")
	void revoke() {
		service.revoke(5L);

		verify(repository).deleteById(5L);
	}

	@Test
	@DisplayName("발급 결과의 toString에 토큰이 찍히지 않는다")
	void issuedToStringHidesToken() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		SessionService.IssuedSession issued = service.issue(1L, true);

		assertThat(issued.toString()).doesNotContain(issued.token());
	}
}
