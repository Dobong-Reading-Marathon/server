package com.dobong.dobong.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dobong.dobong.auth.SessionService.IssuedSession;
import com.dobong.dobong.auth.SessionTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminSessionServiceTest {

	private static final Instant START = Instant.parse("2027-01-05T00:00:00Z");

	private final AtomicReference<Instant> now = new AtomicReference<>(START);
	private final AdminSessionRepository repository = mock(AdminSessionRepository.class);
	private final AdminSessionService service = new AdminSessionService(repository,
			new AdminProperties(Duration.ofHours(8)), movableClock());

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

	// 세션을 발급하고 DB에 저장된 세션을 돌려줌. 원본 토큰은 tokenOut[0]
	private AdminSession issue(String[] tokenOut) {
		IssuedSession issued = service.issue(1L);
		ArgumentCaptor<AdminSession> saved = ArgumentCaptor.forClass(AdminSession.class);
		verify(repository).save(saved.capture());
		tokenOut[0] = issued.token();
		AdminSession session = saved.getValue();
		when(repository.findByTokenHash(SessionTokens.hash(issued.token()))).thenReturn(Optional.of(session));
		return session;
	}

	@Test
	@DisplayName("발급하면 DB에는 해시만 저장하고, 만료는 8시간 뒤, 만료된 세션을 먼저 정리한다")
	void issueStoresHashOnly() {
		String[] token = new String[1];
		AdminSession session = issue(token);

		verify(repository).deleteAllExpired(START);
		assertThat(session.getTokenHash()).isEqualTo(SessionTokens.hash(token[0])).isNotEqualTo(token[0]);
		assertThat(session.getAdminId()).isEqualTo(1L);
		assertThat(session.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(8)));
	}

	@Test
	@DisplayName("유효한 토큰이면 관리자 id를 돌려준다")
	void authenticateValid() {
		String[] token = new String[1];
		issue(token);

		Optional<AdminPrincipal> principal = service.authenticate(token[0]);

		assertThat(principal).isPresent();
		assertThat(principal.get().adminId()).isEqualTo(1L);
	}

	@Test
	@DisplayName("계속 써도 연장되지 않고, 로그인 8시간이 되면 거부하고 세션을 지운다")
	void expiresAfterEightHoursWithoutExtension() {
		String[] token = new String[1];
		AdminSession session = issue(token);

		now.set(START.plus(Duration.ofHours(7)));
		assertThat(service.authenticate(token[0])).isPresent();
		assertThat(session.getExpiresAt()).isEqualTo(START.plus(Duration.ofHours(8)));

		now.set(START.plus(Duration.ofHours(8)));
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

		String tooLong = "a".repeat(AdminSessionService.MAX_TOKEN_LENGTH + 1);
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
		IssuedSession issued = service.issue(1L);

		assertThat(issued.toString()).doesNotContain(issued.token());
	}
}
