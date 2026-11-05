package com.dobong.dobong.dashboard;

import com.dobong.dobong.dashboard.dto.RecentLogsResponse.RecentLog;
import com.dobong.dobong.progress.CountedCondition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 참가 신청 하나의 최근 독서기록. 읽은 날짜가 늦은 순서, 같은 날이면 나중에 쓴 순서.
 *
 * 서평 본문은 길어서 넣지 않고 글자 수만 돌려준다. 글자 수는 서평 작성(F-0402), 집계와 같이 DB char_length로 센다.
 */
@Repository
@RequiredArgsConstructor
public class RecentLogRepository {

	private static final String SELECT_RECENT = """
			SELECT rl.id AS reading_log_id,
			       eb.id AS enrollment_book_id,
			       b.title AS book_title,
			       rl.start_page,
			       rl.end_page,
			       rl.read_date,
			       rl.status,
			       rl.quote,
			       COALESCE(char_length(rl.review), 0) AS review_length,
			       (""" + CountedCondition.SQL + """
			) AS counted
			FROM reading_logs rl
			JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
			JOIN books b             ON b.id = eb.book_id
			JOIN enrollments e       ON e.id = eb.enrollment_id
			JOIN rounds r            ON r.id = e.round_id
			WHERE e.id = :enrollmentId
			ORDER BY rl.read_date DESC, rl.id DESC
			LIMIT :size
			""";

	private static final RowMapper<RecentLog> ROW_MAPPER = (rs, rowNum) -> new RecentLog(
			rs.getLong("reading_log_id"),
			rs.getLong("enrollment_book_id"),
			rs.getString("book_title"),
			rs.getInt("start_page"),
			rs.getInt("end_page"),
			rs.getObject("read_date", LocalDate.class),
			rs.getString("status"),
			rs.getString("quote"),
			rs.getInt("review_length"),
			rs.getBoolean("counted"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public List<RecentLog> findRecent(long enrollmentId, int size) {
		return jdbcTemplate.query(SELECT_RECENT, Map.of("enrollmentId", enrollmentId, "size", size), ROW_MAPPER);
	}
}
