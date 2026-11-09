package com.dobong.dobong.admin.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접수함(F-0901)에 어떤 상태의 기록이 들어가는지 실제 DB에서 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class AdminSubmissionRepositoryTest {

	@Autowired
	private AdminSubmissionRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("작성중(DRAFT)과 저장(SAVED) 기록은 접수함에 나오지 않는다")
	void excludesDraftAndSaved() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도
		long roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2993, '2027-01-05', '2027-11-30') RETURNING id");
		long courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 1, 3) RETURNING id", roundId);
		long userId = insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES ('접수함시험', '01070000401', 'x', now(), 1) RETURNING id");
		long enrollmentId = insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id", userId, roundId, courseId);
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('접수함 시험 책', '저자', 300) RETURNING id");
		long enrollmentBookId = insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, true) RETURNING id", enrollmentId, bookId);

		long draft = addLog(enrollmentBookId, "DRAFT");
		long saved = addLog(enrollmentBookId, "SAVED");
		long submitted = addLog(enrollmentBookId, "SUBMITTED");
		long approved = addLog(enrollmentBookId, "APPROVED");
		long revision = addLog(enrollmentBookId, "REVISION_REQUESTED");

		List<Long> ids = repository.search(null, "%접수함시험%").stream()
				.map(AdminSubmissionRepository.Row::getId)
				.toList();

		assertThat(ids).containsExactlyInAnyOrder(submitted, approved, revision);
		assertThat(ids).doesNotContain(draft, saved);
	}

	private long addLog(long enrollmentBookId, String status) {
		return insert("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, status) VALUES (?, 1, 10, '2027-03-01', ?) RETURNING id",
				enrollmentBookId, status);
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
