package com.dobong.dobong.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.dto.EnrollmentResponse;
import com.dobong.dobong.round.Course;
import com.dobong.dobong.round.CourseRepository;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class EnrollmentServiceTest {

	// 한국 시간 2027-03-01 00:30 (UTC로는 2월 28일). 날짜를 한국 기준으로 보는지 확인
	private static final Instant NOW = Instant.parse("2027-02-28T15:30:00Z");
	private static final Long USER_ID = 7L;

	private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
	private final CourseRepository courseRepository = mock(CourseRepository.class);
	private final RoundService roundService = mock(RoundService.class);
	private final EnrollmentService service = new EnrollmentService(enrollmentRepository, courseRepository,
			roundService, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));

	private Round round;
	private Course course;

	@BeforeEach
	void setUp() {
		// 신청 기간 2027-03-01 ~ 06-30
		round = Round.create(2027, 1, LocalDate.of(2027, 1, 5), LocalDate.of(2027, 11, 30),
				LocalDate.of(2027, 3, 1), LocalDate.of(2027, 6, 30));
		ReflectionTestUtils.setField(round, "id", 10L);
		course = Course.create(round, "걷기코스", false, 3500, 10, 3, 1);
		ReflectionTestUtils.setField(course, "id", 100L);
		when(courseRepository.findById(100L)).thenReturn(Optional.of(course));
		when(enrollmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	@DisplayName("신청하면 로그인한 회원·코스의 회차로 ACTIVE 참가가 저장된다 (한국 날짜 기준 신청 첫날)")
	void enroll() {
		EnrollmentResponse res = service.enroll(USER_ID, 100L);

		ArgumentCaptor<Enrollment> saved = ArgumentCaptor.forClass(Enrollment.class);
		verify(enrollmentRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getRound()).isSameAs(round);
		assertThat(saved.getValue().getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
		assertThat(res.roundId()).isEqualTo(10L);
		assertThat(res.course().name()).isEqualTo("걷기코스");
		assertThat(res.createdAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("없는 코스면 404")
	void courseNotFound() {
		when(courseRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.enroll(USER_ID, 999L))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "COURSE_NOT_FOUND");
	}

	@Test
	@DisplayName("신청 기간이 아니면 400이고 저장하지 않는다")
	void applyClosed() {
		Round closed = Round.create(2026, 1, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 11, 30),
				LocalDate.of(2026, 2, 2), LocalDate.of(2026, 6, 30));
		Course old = Course.create(closed, "걷기코스", false, 3500, 10, 3, 1);
		when(courseRepository.findById(200L)).thenReturn(Optional.of(old));

		assertThatThrownBy(() -> service.enroll(USER_ID, 200L))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "APPLY_CLOSED");
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("이미 이 회차에 참가했으면 409")
	void alreadyEnrolled() {
		when(enrollmentRepository.existsByUserIdAndRoundId(USER_ID, 10L)).thenReturn(true);

		assertThatThrownBy(() -> service.enroll(USER_ID, 100L))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "ALREADY_ENROLLED");
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("동시에 두 번 눌러 DB UNIQUE에 걸려도 409 (500이 아님)")
	void concurrentDuplicate() {
		when(enrollmentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

		assertThatThrownBy(() -> service.enroll(USER_ID, 100L))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "ALREADY_ENROLLED");
	}

	@Test
	@DisplayName("현재 회차의 내 참가를 돌려주고, 없으면 빈 값")
	void current() {
		when(roundService.findCurrentRound()).thenReturn(Optional.of(round));
		when(enrollmentRepository.findByUserIdAndRoundId(USER_ID, 10L))
				.thenReturn(Optional.of(Enrollment.create(USER_ID, course, NOW)));

		assertThat(service.current(USER_ID)).isPresent();
		assertThat(service.current(8L)).isEmpty();
	}

	@Test
	@DisplayName("회차가 하나도 없으면 404")
	void noRound() {
		when(roundService.findCurrentRound()).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.current(USER_ID))
				.isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "ROUND_NOT_FOUND");
	}
}
