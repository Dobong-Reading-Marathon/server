package com.dobong.dobong.participant;

import com.dobong.dobong.progress.CountedCondition;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 참가 신청 하나의 등록 도서와 독서기록 전체를 조회한다 (사용자별 기록 조회 F-0802).
 * 집계에 들어가지 않는 책과 기록도 모두 돌려준다. 사서가 무엇이 빠졌는지 확인할 수 있게 하기 위해서다.
 */
@Repository
@RequiredArgsConstructor
public class EnrollmentBookDetailRepository {

	private static final String SELECT_BOOKS = """
			SELECT eb.id AS enrollment_book_id,
			       b.id AS book_id,
			       b.title,
			       b.author,
			       b.total_pages,
			       eb.is_library_owned,
			       b.is_children,
			       eb.registration_no,
			       eb.loan_date,
			       eb.return_date
			FROM enrollment_books eb
			JOIN books b ON b.id = eb.book_id
			WHERE eb.enrollment_id = :enrollmentId
			ORDER BY eb.id
			""";

	private static final String SELECT_LOGS = """
			SELECT rl.id AS reading_log_id,
			       rl.enrollment_book_id,
			       rl.read_date,
			       rl.start_page,
			       rl.end_page,
			       rl.status,
			       rl.quote,
			       rl.review,
			       COALESCE(char_length(rl.review), 0) AS review_length,
			       rl.librarian_comment,
			       rl.submitted_at,
			       rl.reviewed_at,
			       (""" + CountedCondition.SQL + """
			) AS counted
			FROM reading_logs rl
			JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
			JOIN books b             ON b.id = eb.book_id
			JOIN enrollments e       ON e.id = eb.enrollment_id
			JOIN rounds r            ON r.id = e.round_id
			WHERE e.id = :enrollmentId
			ORDER BY rl.read_date, rl.start_page, rl.id
			""";

	private static final RowMapper<EnrollmentBookRow> BOOK_MAPPER = (rs, rowNum) -> new EnrollmentBookRow(
			rs.getLong("enrollment_book_id"),
			rs.getLong("book_id"),
			rs.getString("title"),
			rs.getString("author"),
			rs.getInt("total_pages"),
			rs.getBoolean("is_library_owned"),
			rs.getBoolean("is_children"),
			rs.getString("registration_no"),
			rs.getObject("loan_date", LocalDate.class),
			rs.getObject("return_date", LocalDate.class));

	private static final RowMapper<ReadingLogDetailRow> LOG_MAPPER = (rs, rowNum) -> new ReadingLogDetailRow(
			rs.getLong("reading_log_id"),
			rs.getLong("enrollment_book_id"),
			rs.getObject("read_date", LocalDate.class),
			rs.getInt("start_page"),
			rs.getInt("end_page"),
			rs.getString("status"),
			rs.getString("quote"),
			rs.getString("review"),
			rs.getInt("review_length"),
			rs.getString("librarian_comment"),
			toInstant(rs.getTimestamp("submitted_at")),
			toInstant(rs.getTimestamp("reviewed_at")),
			rs.getBoolean("counted"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** 등록 순서 */
	public List<EnrollmentBookRow> findBooks(long enrollmentId) {
		return jdbcTemplate.query(SELECT_BOOKS, Map.of("enrollmentId", enrollmentId), BOOK_MAPPER);
	}

	/** 읽은 날짜 순서, 같은 날이면 시작 쪽 순서 */
	public List<ReadingLogDetailRow> findLogs(long enrollmentId) {
		return jdbcTemplate.query(SELECT_LOGS, Map.of("enrollmentId", enrollmentId), LOG_MAPPER);
	}

	private static Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}
}
