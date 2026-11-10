package com.dobong.dobong.calendar;

import com.dobong.dobong.progress.CountedCondition;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 참가 신청 하나의 기간 안 독서기록을 모두 조회한다. 집계에 들어가지 않는 기록도 counted = false로 함께 돌려준다.
 *
 * counted 조건은 누적 집계(F-0502)와 같은 {@link CountedCondition}을 쓴다.
 * (CalendarServiceTest가 두 결과가 같은지 확인한다)
 */
@Repository
@RequiredArgsConstructor
public class CalendarRepository {

	private static final String SELECT_LOGS = """
			SELECT rl.id AS reading_log_id,
			       eb.id AS enrollment_book_id,
			       b.title AS book_title,
			       rl.start_page,
			       rl.end_page,
			       rl.read_date,
			       rl.status,
			       (""" + CountedCondition.SQL + """
			) AS counted
			FROM reading_logs rl
			JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
			JOIN books b             ON b.id = eb.book_id
			JOIN enrollments e       ON e.id = eb.enrollment_id
			JOIN rounds r            ON r.id = e.round_id
			WHERE e.id = :enrollmentId
			  AND rl.read_date BETWEEN :from AND :to
			ORDER BY rl.read_date, rl.id
			""";

	private static final RowMapper<CalendarLogRow> ROW_MAPPER = (rs, rowNum) -> new CalendarLogRow(
			rs.getLong("reading_log_id"),
			rs.getLong("enrollment_book_id"),
			rs.getString("book_title"),
			rs.getInt("start_page"),
			rs.getInt("end_page"),
			rs.getObject("read_date", LocalDate.class),
			rs.getString("status"),
			rs.getBoolean("counted"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** 읽은 날짜가 from ~ to(둘 다 포함)인 기록. 읽은 날짜, 작성 순서(id)로 정렬 */
	public List<CalendarLogRow> findLogs(long enrollmentId, LocalDate from, LocalDate to) {
		return jdbcTemplate.query(SELECT_LOGS,
				Map.of("enrollmentId", enrollmentId, "from", from, "to", to), ROW_MAPPER);
	}
}
