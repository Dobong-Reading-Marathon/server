package com.dobong.dobong.progress;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 집계 대상 독서기록을 조회한다.
 *
 * 쪽수와 완독은 승인 여부와 상관없이 등록된 모든 기록으로 센다. 서평만 사서 승인 조건을 건다.
 * 공통 조건({@link CountedCondition}): 소장도서로 체크된 책, 끝 쪽 ≤ 책 전체 쪽수, 읽은 날짜가 회차 기간 안.
 */
@Repository
@RequiredArgsConstructor
public class ReadingProgressRepository {

	private static final String SELECT_TARGET_LOGS = """
			SELECT eb.enrollment_id,
			       eb.id AS enrollment_book_id,
			       b.total_pages,
			       rl.start_page,
			       rl.end_page,
			       rl.status = 'APPROVED'
			         AND COALESCE(char_length(rl.review), 0) >= 500 AS approved_long_review
			FROM reading_logs rl
			JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
			JOIN books b             ON b.id = eb.book_id
			JOIN enrollments e       ON e.id = eb.enrollment_id
			JOIN rounds r            ON r.id = e.round_id
			WHERE
			""" + CountedCondition.SQL + "\n";

	private static final String ORDER = " ORDER BY eb.id, rl.start_page";

	private static final RowMapper<ReadingLogRow> ROW_MAPPER = (rs, rowNum) -> new ReadingLogRow(
			rs.getLong("enrollment_id"),
			rs.getLong("enrollment_book_id"),
			rs.getInt("total_pages"),
			rs.getInt("start_page"),
			rs.getInt("end_page"),
			rs.getBoolean("approved_long_review"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public List<ReadingLogRow> findByEnrollment(long enrollmentId) {
		return jdbcTemplate.query(SELECT_TARGET_LOGS + " AND e.id = :enrollmentId" + ORDER,
				Map.of("enrollmentId", enrollmentId), ROW_MAPPER);
	}

	public List<ReadingLogRow> findByRound(long roundId) {
		return jdbcTemplate.query(SELECT_TARGET_LOGS + " AND e.round_id = :roundId" + ORDER,
				Map.of("roundId", roundId), ROW_MAPPER);
	}
}
