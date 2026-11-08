package com.dobong.dobong.demo;

import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 *
 * 관리자 화면(참가자 목록, 사용자별 기록 조회, 명단 다운로드)용으로 다른 참가자 12명도 넣는다 ({@link #PARTICIPANTS}).
 * 코스와 완주 상태(진행 중, 완주 승인 대기, 완주)가 골고루 나오게 했다. 비밀번호는 시연 참가자와 같다.
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

	/**
	 * 다른 시연 참가자. 쪽수와 서평 편수는 코스 기준의 비율(%)로 정해서, 코스 기준이 바뀌어도 완주 상태가 그대로 나온다.
	 *
	 * @param course        코스 순서 (0부터, courses.sort_order 순). 코스가 그보다 적으면 앞에서부터 다시 센다
	 * @param pagePercent   코스 완주 쪽수의 몇 %를 읽었는지
	 * @param reviewPercent 코스 서평 편수의 몇 %를 승인받았는지 (내림)
	 * @param completed     사서가 완주를 승인했는지 (인증서 번호를 붙인다)
	 */
	record DemoParticipant(String name, String phone, int course, int pagePercent, int reviewPercent,
			boolean completed, String loanMemberNo) {
	}

	static final List<DemoParticipant> PARTICIPANTS = List.of(
			new DemoParticipant("김하늘", "01099990001", 0, 103, 100, false, "LM-1001"), // 쪽수, 서평 모두 채움 → 완주 승인 대기
			new DemoParticipant("이도윤", "01099990002", 0, 60, 34, false, null),
			new DemoParticipant("박서연", "01099990003", 1, 104, 100, true, "LM-1003"),  // 사서가 완주 승인 → 완주
			new DemoParticipant("최지우", "01099990004", 1, 30, 20, false, null),
			new DemoParticipant("정민준", "01099990005", 2, 45, 40, false, "LM-1005"),
			new DemoParticipant("강수아", "01099990006", 2, 0, 0, false, null),          // 막 신청해서 기록 없음
			new DemoParticipant("조은우", "01099990007", 3, 20, 14, false, null),
			new DemoParticipant("윤채원", "01099990008", 4, 15, 7, false, "LM-1008"),
			new DemoParticipant("장예준", "01099990009", 5, 10, 5, false, null),
			new DemoParticipant("임하은", "01099990010", 0, 105, 67, false, null),       // 쪽수는 채웠지만 서평이 모자람 → 진행 중
			new DemoParticipant("한서준", "01099990011", 1, 70, 60, false, "LM-1011"),
			new DemoParticipant("오지민", "01099990012", 2, 50, 47, false, null));

	/**
	 * 다른 시연 참가자의 책장. 참가자마다 다른 곳부터 차례로 꺼내 읽는다.
	 * 소장도서를 한 바퀴 다 읽으면 약 9,000쪽이다. 이보다 많이 읽는 참가자를 넣으면 같은 책이 두 번 등록된다
	 */
	private static final List<DemoBook> SHELF = List.of(
			new DemoBook("채식주의자", "한강", 247, true),
			new DemoBook("소년이 온다", "한강", 216, true),
			new DemoBook("작별하지 않는다", "한강", 332, true),
			new DemoBook("82년생 김지영", "조남주", 192, true),
			new DemoBook("불편한 편의점", "김호연", 268, true),
			new DemoBook("달러구트 꿈 백화점", "이미예", 300, true),
			new DemoBook("사피엔스", "유발 하라리", 636, true),
			new DemoBook("총, 균, 쇠", "재레드 다이아몬드", 752, true),
			new DemoBook("이기적 유전자", "리처드 도킨스", 632, true),
			new DemoBook("파친코", "이민진", 528, true),
			new DemoBook("토지 1", "박경리", 460, true),
			new DemoBook("태백산맥 1", "조정래", 400, true),
			new DemoBook("난장이가 쏘아올린 작은 공", "조세희", 340, true),
			new DemoBook("나미야 잡화점의 기적", "히가시노 게이고", 456, true),
			new DemoBook("연금술사", "파울로 코엘료", 278, true),
			new DemoBook("노르웨이의 숲", "무라카미 하루키", 544, true),
			new DemoBook("1984", "조지 오웰", 440, true),
			new DemoBook("동물농장", "조지 오웰", 160, true),
			new DemoBook("위대한 개츠비", "F. 스콧 피츠제럴드", 256, true),
			new DemoBook("정의란 무엇인가", "마이클 샌델", 444, true),
			new DemoBook("미움받을 용기", "기시미 이치로", 336, true),
			new DemoBook("침묵의 봄", "레이첼 카슨", 400, true),
			new DemoBook("멋진 신세계", "올더스 헉슬리", 380, true),
			new DemoBook("마당을 나온 암탉", "황선미", 200, false)); // 소장도서 아님: counted = false

	/** 승인된 서평이 없는 기록의 상태. 차례로 돌아가며 붙인다 */
	private static final List<String> PLAIN_STATUSES = List.of("APPROVED", "APPROVED", "SUBMITTED", "DRAFT");

	/** 코스 순서와 기준 */
	private record CourseTarget(long id, int targetPages, int targetReviews) {
	}

	/** 넣을 기록 한 건 (날짜는 나중에 붙인다) */
	private record PlannedLog(long enrollmentBookId, int start, int end, String status, String review) {
	}

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

	/** 현재 회차에 시연 참가자와 다른 참가자들, 그 기록을 새로 넣는다. 시연 참가자의 참가 신청 id를 돌려준다. 테스트에서도 부른다 */
	@Transactional
	public long load(Round round) {
		long userId = upsertUser(DEMO_NAME, DEMO_PHONE, null);
		List<Long> otherUserIds = PARTICIPANTS.stream()
				.map(p -> upsertUser(p.name(), p.phone(), p.loanMemberNo()))
				.toList();
		deleteOldData(userId);
		otherUserIds.forEach(this::deleteOldData);
		deleteDemoBooks();

		List<CourseTarget> courses = jdbcTemplate.query(
				"SELECT id, target_pages, target_reviews FROM courses WHERE round_id = ? ORDER BY sort_order, id",
				(rs, rowNum) -> new CourseTarget(rs.getLong("id"), rs.getInt("target_pages"), rs.getInt("target_reviews")),
				round.getId());
		long enrollmentId = loadDemoUser(round, userId, courses.get(0).id());
		for (int i = 0; i < PARTICIPANTS.size(); i++) {
			loadParticipant(round, otherUserIds.get(i), PARTICIPANTS.get(i), i, courses);
		}

		log.info("[시연 데이터] 다른 참가자 {}명도 넣었습니다. 전화번호 {} ~ {}, 비밀번호는 같습니다.",
				PARTICIPANTS.size(), PARTICIPANTS.get(0).phone(), PARTICIPANTS.get(PARTICIPANTS.size() - 1).phone());
		return enrollmentId;
	}

	/** 시연 참가자: 정해 둔 책과 기록(LOGS)을 넣는다. 집계에 들어가지 않는 기록도 섞여 있다 */
	private long loadDemoUser(Round round, long userId, long courseId) {
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

	/**
	 * 다른 참가자: 코스 기준의 pagePercent만큼 책장(SHELF)의 책을 차례로 읽는다.
	 * 앞의 책부터 승인된 500자 서평을 붙이고, 그다음 책 하나에는 심사 대기 서평을 붙인다.
	 * 소장도서가 아닌 책은 읽어도 쪽수에 들어가지 않으므로 목표 쪽수에서 빼지 않는다.
	 */
	private void loadParticipant(Round round, long userId, DemoParticipant p, int order, List<CourseTarget> courses) {
		CourseTarget course = courses.get(p.course() % courses.size());
		long enrollmentId = jdbcTemplate.queryForObject("""
				INSERT INTO enrollments (user_id, round_id, course_id, status, certificate_no, approved_at)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
				Long.class, userId, round.getId(), course.id(),
				p.completed() ? "COMPLETED" : "ACTIVE",
				p.completed() ? "DEMO-%d-%d-%02d".formatted(round.getYear(), round.getRoundNo(), order + 1) : null,
				p.completed() ? Timestamp.from(clock.instant()) : null);

		int remainingPages = (int) ((long) course.targetPages() * p.pagePercent() / 100);
		int approvedReviews = course.targetReviews() * p.reviewPercent() / 100;
		int reviewedBooks = 0;
		List<PlannedLog> logs = new ArrayList<>();

		for (int i = 0; remainingPages > 0; i++) {
			DemoBook book = SHELF.get((order * 5 + i) % SHELF.size());
			long enrollmentBookId = addBook(enrollmentId, book);
			int readPages = Math.min(remainingPages, book.totalPages());

			String lastStatus = PLAIN_STATUSES.get(i % PLAIN_STATUSES.size());
			String lastReview = null;
			if (book.libraryOwned()) {
				remainingPages -= readPages;
				if (reviewedBooks < approvedReviews) {
					lastStatus = "APPROVED";
					lastReview = REVIEW_LONG;
				} else if (reviewedBooks == approvedReviews) {
					lastStatus = "SUBMITTED"; // 사서 심사 대기 서평. 승인 전이라 서평 편수에 안 들어감
					lastReview = REVIEW_LONG;
				}
				reviewedBooks++;
			}

			// 300쪽이 넘으면 두 번에 나눠 읽은 것으로 넣는다
			if (readPages > 300) {
				int half = readPages / 2;
				logs.add(new PlannedLog(enrollmentBookId, 1, half, PLAIN_STATUSES.get((i + 1) % PLAIN_STATUSES.size()), null));
				logs.add(new PlannedLog(enrollmentBookId, half + 1, readPages, lastStatus, lastReview));
			} else {
				logs.add(new PlannedLog(enrollmentBookId, 1, readPages, lastStatus, lastReview));
			}
		}

		List<LocalDate> dates = spreadDates(round, Math.max(1, logs.size()));
		for (int i = 0; i < logs.size(); i++) {
			PlannedLog planned = logs.get(i);
			jdbcTemplate.update("""
					INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status)
					VALUES (?, ?, ?, ?, ?, ?)""",
					planned.enrollmentBookId(), planned.start(), planned.end(), dates.get(i), planned.review(), planned.status());
		}
	}

	private long upsertUser(String name, String phone, String loanMemberNo) {
		String hash = passwordEncoder.encode(DEMO_PASSWORD);
		List<Long> existing = jdbcTemplate.queryForList("SELECT id FROM users WHERE phone = ?", Long.class, phone);
		if (!existing.isEmpty()) {
			// 비밀번호를 바꿨거나 잠겼어도 다시 로그인할 수 있게 되돌린다
			jdbcTemplate.update("""
					UPDATE users SET name = ?, loan_member_no = ?, password_hash = ?, failed_login_count = 0,
					       locked_until = NULL, updated_at = now()
					WHERE id = ?""", name, loanMemberNo, hash, existing.get(0));
			return existing.get(0);
		}
		Integer policyVersion = jdbcTemplate.queryForObject("SELECT max(version) FROM privacy_policies", Integer.class);
		return jdbcTemplate.queryForObject("""
				INSERT INTO users (name, phone, password_hash, loan_member_no, privacy_agreed_at, privacy_agreed_version)
				VALUES (?, ?, ?, ?, now(), ?) RETURNING id""",
				Long.class, name, phone, hash, loanMemberNo, policyVersion);
	}

	/** 시연 참가자 한 명의 모든 회차 기록을 지운다 */
	private void deleteOldData(long userId) {
		jdbcTemplate.update("""
				DELETE FROM reading_logs WHERE enrollment_book_id IN (
				  SELECT eb.id FROM enrollment_books eb JOIN enrollments e ON e.id = eb.enrollment_id WHERE e.user_id = ?)""",
				userId);
		jdbcTemplate.update(
				"DELETE FROM enrollment_books WHERE enrollment_id IN (SELECT id FROM enrollments WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM enrollments WHERE user_id = ?", userId);
	}

	/** 아무도 등록하지 않은 시연용 책을 지운다 */
	private void deleteDemoBooks() {
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
