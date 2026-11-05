package com.dobong.dobong.demo;

import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프론트 개발용 시연 데이터. 로컬 서버를 켤 때 DEMO_DATA=true이면 시연용 참가자와 독서기록을 넣는다.
 *
 * - local 프로파일에서만 만들어진다. dev, prod 서버에는 들어가지 않는다.
 * - 켜는 방법: ./gradlew bootRun --args='--DEMO_DATA=true' 또는 .env에 DEMO_DATA=true
 * - 로그인: 전화번호 DEMO_PHONE, 비밀번호 DEMO_PASSWORD (아래 상수). 로컬 DB 전용 계정이라 비밀값이 아니다
 * - 서버를 켤 때마다 시연 참가자의 기록을 지우고 새로 넣는다. 다른 회원의 데이터는 건드리지 않는다
 *
 * 기록은 현재 회차(F-0201 규칙)에 넣는다. 오늘이 운영 기간 안이면 오늘까지의 최근 35일, 아니면 운영 시작일부터 35일에 흩어 넣는다.
 * 소장도서가 아닌 책, 전체 쪽수를 넘는 기록처럼 집계에 들어가지 않는 기록(counted = false)도 함께 넣는다.
 */
@Slf4j
@Component
@Profile("local")
public class DemoDataLoader implements ApplicationRunner {

	static final String DEMO_PHONE = "01099990000";
	static final String DEMO_PASSWORD = "demo-reading-2027";
	static final String DEMO_NAME = "시연 참가자";

	/** 시연용 책을 구분하는 표시. 다시 넣을 때 이 표시가 있는 책만 지운다 */
	static final String DEMO_PUBLISHER = "시연용 데이터";

	private static final int WINDOW_DAYS = 35;
	/** 540자 (18자 × 30) */
	private static final String REVIEW_LONG = "이 책을 읽으며 오래 생각했다. ".repeat(30);
	private static final String REVIEW_SHORT = "짧은 감상입니다. 500자가 안 됩니다.";

	/** 책: 제목, 저자, 전체 쪽수, 소장도서 여부 */
	private record DemoBook(String title, String author, int totalPages, boolean libraryOwned) {
	}

	/** 기록: 책 번호(BOOKS 순서), 시작 쪽, 끝 쪽, 상태, 서평 */
	private record DemoLog(int book, int start, int end, String status, String review) {
	}

	private static final List<DemoBook> BOOKS = List.of(
			new DemoBook("어린 왕자", "앙투안 드 생텍쥐페리", 120, true),
			new DemoBook("데미안", "헤르만 헤세", 248, true),
			new DemoBook("코스모스", "칼 세이건", 719, true),
			new DemoBook("아몬드", "손원평", 264, false));

	private static final List<DemoLog> LOGS = List.of(
			new DemoLog(0, 1, 60, "APPROVED", null),
			new DemoLog(0, 61, 120, "APPROVED", REVIEW_LONG),   // 완독 + 승인된 500자 서평
			new DemoLog(1, 1, 80, "SUBMITTED", null),
			new DemoLog(1, 70, 150, "SUBMITTED", null),         // 앞 기록과 겹침: 겹치는 쪽은 한 번만 셈
			new DemoLog(3, 1, 100, "DRAFT", null),              // 소장도서 아님: counted = false
			new DemoLog(1, 151, 248, "SUBMITTED", REVIEW_SHORT), // 완독, 500자 미만 서평
			new DemoLog(2, 1, 90, "APPROVED", null),
			new DemoLog(2, 91, 200, "REVISION_REQUESTED", REVIEW_SHORT),
			new DemoLog(2, 201, 320, "DRAFT", null),
			new DemoLog(3, 101, 264, "DRAFT", null),            // 소장도서 아님: counted = false
			new DemoLog(2, 321, 800, "DRAFT", null),            // 전체 쪽수(719) 초과: counted = false
			new DemoLog(2, 321, 450, "SUBMITTED", null),
			new DemoLog(2, 451, 560, "DRAFT", null),
			new DemoLog(2, 561, 600, "DRAFT", null));

	private final JdbcTemplate jdbcTemplate;
	private final PasswordEncoder passwordEncoder;
	private final RoundService roundService;
	private final Clock clock;
	private final boolean enabled;

	public DemoDataLoader(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, RoundService roundService,
			Clock clock, @Value("${DEMO_DATA:false}") boolean enabled) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.roundService = roundService;
		this.clock = clock;
		this.enabled = enabled;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		Round round = roundService.findCurrentRound().orElse(null);
		if (round == null) {
			log.warn("[시연 데이터] 현재 회차가 없어서 넣지 않았습니다.");
			return;
		}
		load(round);
	}

	/** 현재 회차에 시연 참가자와 기록을 새로 넣는다. 테스트에서도 부른다 */
	@Transactional
	public long load(Round round) {
		long userId = upsertUser();
		deleteOldData(userId);

		Long courseId = jdbcTemplate.queryForObject(
				"SELECT id FROM courses WHERE round_id = ? ORDER BY sort_order, id LIMIT 1", Long.class, round.getId());
		long enrollmentId = jdbcTemplate.queryForObject(
				"INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id",
				Long.class, userId, round.getId(), courseId);

		long[] enrollmentBookIds = BOOKS.stream().mapToLong(book -> addBook(enrollmentId, book)).toArray();

		List<LocalDate> dates = spreadDates(round, LOGS.size());
		for (int i = 0; i < LOGS.size(); i++) {
			DemoLog demoLog = LOGS.get(i);
			jdbcTemplate.update("""
					INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status)
					VALUES (?, ?, ?, ?, ?, ?)""",
					enrollmentBookIds[demoLog.book()], demoLog.start(), demoLog.end(), dates.get(i),
					demoLog.review(), demoLog.status());
		}

		log.info("[시연 데이터] 넣었습니다. 로그인: 전화번호 {} / 비밀번호 {} / 회차 {}년 {}회차 / 기록 날짜 {} ~ {}",
				DEMO_PHONE, DEMO_PASSWORD, round.getYear(), round.getRoundNo(),
				dates.get(0), dates.get(dates.size() - 1));
		return enrollmentId;
	}

	private long upsertUser() {
		String hash = passwordEncoder.encode(DEMO_PASSWORD);
		List<Long> existing = jdbcTemplate.queryForList("SELECT id FROM users WHERE phone = ?", Long.class, DEMO_PHONE);
		if (!existing.isEmpty()) {
			// 비밀번호를 바꿨거나 잠겼어도 다시 로그인할 수 있게 되돌린다
			jdbcTemplate.update("""
					UPDATE users SET password_hash = ?, failed_login_count = 0, locked_until = NULL, updated_at = now()
					WHERE id = ?""", hash, existing.get(0));
			return existing.get(0);
		}
		Integer policyVersion = jdbcTemplate.queryForObject("SELECT max(version) FROM privacy_policies", Integer.class);
		return jdbcTemplate.queryForObject("""
				INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version)
				VALUES (?, ?, ?, now(), ?) RETURNING id""",
				Long.class, DEMO_NAME, DEMO_PHONE, hash, policyVersion);
	}

	/** 시연 참가자의 모든 회차 기록과 시연용 책을 지운다 */
	private void deleteOldData(long userId) {
		jdbcTemplate.update("""
				DELETE FROM reading_logs WHERE enrollment_book_id IN (
				  SELECT eb.id FROM enrollment_books eb JOIN enrollments e ON e.id = eb.enrollment_id WHERE e.user_id = ?)""",
				userId);
		jdbcTemplate.update(
				"DELETE FROM enrollment_books WHERE enrollment_id IN (SELECT id FROM enrollments WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM enrollments WHERE user_id = ?", userId);
		jdbcTemplate.update("""
				DELETE FROM books b WHERE b.publisher = ?
				  AND NOT EXISTS (SELECT 1 FROM enrollment_books eb WHERE eb.book_id = b.id)""", DEMO_PUBLISHER);
	}

	private long addBook(long enrollmentId, DemoBook book) {
		long bookId = jdbcTemplate.queryForObject(
				"INSERT INTO books (title, author, publisher, total_pages) VALUES (?, ?, ?, ?) RETURNING id",
				Long.class, book.title(), book.author(), DEMO_PUBLISHER, book.totalPages());
		return jdbcTemplate.queryForObject(
				"INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, ?) RETURNING id",
				Long.class, enrollmentId, bookId, book.libraryOwned());
	}

	/** 운영 기간 안의 35일에 날짜를 고르게 나눈다. 오늘이 운영 기간 안이면 오늘까지, 아니면 운영 시작일부터 */
	private List<LocalDate> spreadDates(Round round, int count) {
		LocalDate today = LocalDate.now(clock);
		LocalDate from;
		LocalDate to;
		if (round.isInOperation(today)) {
			to = today;
			from = maxDate(round.getStartDate(), today.minusDays(WINDOW_DAYS - 1));
		} else {
			from = round.getStartDate();
			to = minDate(round.getEndDate(), from.plusDays(WINDOW_DAYS - 1));
		}
		long span = ChronoUnit.DAYS.between(from, to);
		return IntStream.range(0, count)
				.mapToObj(i -> from.plusDays(count == 1 ? 0 : span * i / (count - 1)))
				.toList();
	}

	private static LocalDate maxDate(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private static LocalDate minDate(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}
}
