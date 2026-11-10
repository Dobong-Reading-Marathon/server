package com.dobong.dobong.calendar;

import com.dobong.dobong.calendar.dto.CalendarResponse;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.Enrollment;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 독서달력 (F-0504). 값은 저장하지 않고 조회할 때마다 계산한다.
 *
 * 참가 신청은 로그인한 회원(userId)과 회차로 찾는다. 요청으로 참가 신청 id를 받지 않으므로 남의 달력을 볼 수 없다.
 * "이번 달"은 한국 날짜 기준 (TimeConfig의 Clock).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

	private static final int MIN_YEAR = 2000;
	private static final int MAX_YEAR = 2999;

	private final CalendarRepository calendarRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final RoundService roundService;
	private final Clock clock;

	/**
	 * @param year    연도. month와 함께 보내거나 둘 다 비움
	 * @param month   월 (1~12)
	 * @param roundId 회차. 비우면 현재 회차 (F-0201 규칙)
	 */
	public CalendarResponse getMyCalendar(long userId, Integer year, Integer month, Long roundId) {
		YearMonth yearMonth = resolveMonth(year, month);
		Enrollment enrollment = findEnrollment(userId, roundId)
				.orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다."));

		List<CalendarLogRow> rows = calendarRepository.findLogs(
				enrollment.getId(), yearMonth.atDay(1), yearMonth.atEndOfMonth());

		return new CalendarResponse(
				enrollment.getId(),
				enrollment.getRound().getId(),
				yearMonth.getYear(),
				yearMonth.getMonthValue(),
				CalendarCalculator.summarize(rows),
				CalendarCalculator.days(rows));
	}

	private YearMonth resolveMonth(Integer year, Integer month) {
		if (year == null && month == null) {
			return YearMonth.now(clock);
		}
		if (year == null || month == null || month < 1 || month > 12 || year < MIN_YEAR || year > MAX_YEAR) {
			throw ApiException.badRequest("INVALID_INPUT", "입력값을 확인해 주세요.");
		}
		return YearMonth.of(year, month);
	}

	private Optional<Enrollment> findEnrollment(long userId, Long roundId) {
		if (roundId != null) {
			return enrollmentRepository.findByUserIdAndRoundId(userId, roundId);
		}
		return roundService.findCurrentRound()
				.map(Round::getId)
				.flatMap(currentRoundId -> enrollmentRepository.findByUserIdAndRoundId(userId, currentRoundId));
	}
}
