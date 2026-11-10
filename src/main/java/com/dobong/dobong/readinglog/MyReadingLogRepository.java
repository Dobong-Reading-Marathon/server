package com.dobong.dobong.readinglog;

import com.dobong.dobong.progress.CountedCondition;
import com.dobong.dobong.readinglog.dto.MyReadingLogsResponse.Item;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 참가 신청 하나의 독서기록을 책 정보, 서평 본문과 함께 모두 조회한다.
 * counted 조건은 누적 집계(F-0502)와 같은 {@link CountedCondition}을 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class MyReadingLogRepository {

	private static final String SELECT_LOGS = """
			SELECT rl.id AS reading_log_id,
			       eb.id AS enrollment_book_id,
			       b.title AS book_title,
			       b.author,
			       b.publisher,
			       b.publish_year,
			       b.total_pages,
			       eb.is_library_owned,
			       rl.start_page,
			       rl.end_page,
			       rl.read_date,
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
			ORDER BY rl.read_date, rl.id
			""";

	private static final RowMapper<Item> ROW_MAPPER = (rs, rowNum) -> new Item(
			rs.getLong("reading_log_id"),
			rs.getLong("enrollment_book_id"),
			rs.getString("book_title"),
			rs.getString("author"),
			rs.getString("publisher"),
			rs.getObject("publish_year", Integer.class),
			rs.getInt("total_pages"),
			rs.getBoolean("is_library_owned"),
			rs.getInt("start_page"),
			rs.getInt("end_page"),
			rs.getObject("read_date", LocalDate.class),
			rs.getString("status"),
			rs.getString("quote"),
			rs.getString("review"),
			rs.getInt("review_length"),
			rs.getString("librarian_comment"),
			rs.getObject("submitted_at", OffsetDateTime.class),
			rs.getObject("reviewed_at", OffsetDateTime.class),
			rs.getBoolean("counted"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** 참가 신청의 모든 기록. 읽은 날짜, 작성 순서(id)로 정렬 */
	public List<Item> findAll(long enrollmentId) {
		return jdbcTemplate.query(SELECT_LOGS, Map.of("enrollmentId", enrollmentId), ROW_MAPPER);
	}
}
