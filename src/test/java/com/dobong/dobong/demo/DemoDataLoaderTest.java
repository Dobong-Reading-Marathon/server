package com.dobong.dobong.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.dobong.dobong.progress.ReadingProgress;
import com.dobong.dobong.progress.ReadingProgressService;
import com.dobong.dobong.round.Round;
import com.dobong.dobong.round.RoundRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연 데이터가 의도한 값(집계, 집계 제외 기록)으로 들어가는지 확인한다.
 * 테스트용 회차에 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class DemoDataLoaderTest {

	@Autowired
	private DemoDataLoader loader;

	@Autowired
	private RoundRepository roundRepository;

	@Autowired
	private ReadingProgressService readingProgressService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("시연 참가자로 로그인할 수 있고, 집계 값이 의도한 대로 나온다")
	void loads() {
		long enrollmentId = loader.load(testRound());

		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE phone = ?", String.class,
				DemoDataLoader.DEMO_PHONE);
		assertThat(passwordEncoder.matches(DemoDataLoader.DEMO_PASSWORD, hash)).isTrue();

		// 어린 왕자 120 + 데미안 248(겹침은 한 번) + 코스모스 1–600 = 968쪽. 완독 2권, 승인된 500자 서평 1편
		// 아몬드(소장도서 아님)와 코스모스 321–800(전체 쪽수 초과)은 집계에서 빠짐
		assertThat(readingProgressService.getProgress(enrollmentId))
				.isEqualTo(new ReadingProgress(enrollmentId, 968, 2, 1));
	}

	@Test
	@DisplayName("다시 넣어도 시연 참가자의 기록이 쌓이지 않고 새로 바뀐다")
	void reloadReplaces() {
		Round round = testRound();
		loader.load(round);
		loader.load(round);

		Integer enrollments = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM enrollments e JOIN users u ON u.id = e.user_id WHERE u.phone = ?",
				Integer.class, DemoDataLoader.DEMO_PHONE);
		Integer logs = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM reading_logs rl
				JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
				JOIN enrollments e ON e.id = eb.enrollment_id
				JOIN users u ON u.id = e.user_id
				WHERE u.phone = ?""", Integer.class, DemoDataLoader.DEMO_PHONE);
		Integer users = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE phone = ?", Integer.class,
				DemoDataLoader.DEMO_PHONE);

		assertThat(users).isEqualTo(1);
		assertThat(enrollments).isEqualTo(1);
		assertThat(logs).isEqualTo(14);
	}

	/** 다른 테스트나 시연용 데이터와 겹치지 않는 연도. 운영 기간은 2027-01-05 ~ 2027-11-30 */
	private Round testRound() {
		Long roundId = jdbcTemplate.queryForObject(
				"INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2995, '2027-01-05', '2027-11-30') RETURNING id",
				Long.class);
		jdbcTemplate.update(
				"INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 2, 3)",
				roundId);
		return roundRepository.findById(roundId).orElseThrow();
	}
}
