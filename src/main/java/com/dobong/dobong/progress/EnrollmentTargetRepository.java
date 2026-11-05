package com.dobong.dobong.progress;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 완주 판정에 필요한 참가 신청, 회차, 코스 기준을 조회한다.
 *
 * Enrollment, Round, Course 엔티티가 있지만, 코스 기준과 소장도서 수를 쿼리 한 번에 읽으려고 SQL로 직접 조회한다.
 */
@Repository
@RequiredArgsConstructor
public class EnrollmentTargetRepository {

	private static final String SELECT = """
			SELECT e.id AS enrollment_id,
			       r.id AS round_id,
			       r.year,
			       r.round_no,
			       c.id AS course_id,
			       c.name AS course_name,
			       c.is_family,
			       c.target_pages,
			       c.target_books,
			       c.target_reviews,
			       e.status = 'COMPLETED' AS completed,
			       (SELECT count(*)
			          FROM enrollment_books eb
			         WHERE eb.enrollment_id = e.id
			           AND eb.is_library_owned) AS library_books
			FROM enrollments e
			JOIN rounds r  ON r.id = e.round_id
			JOIN courses c ON c.id = e.course_id
			""";

	private static final RowMapper<EnrollmentTarget> ROW_MAPPER = (rs, rowNum) -> new EnrollmentTarget(
			rs.getLong("enrollment_id"),
			rs.getLong("round_id"),
			rs.getInt("year"),
			rs.getInt("round_no"),
			rs.getLong("course_id"),
			rs.getString("course_name"),
			rs.getBoolean("is_family"),
			rs.getInt("target_pages"),
			rs.getInt("target_books"),
			rs.getInt("target_reviews"),
			rs.getBoolean("completed"),
			rs.getInt("library_books"));

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/** 본인의 참가 신청만 찾는다. 남의 참가 신청이면 없는 것과 같이 빈 값 */
	public Optional<EnrollmentTarget> findByIdAndUserId(long enrollmentId, long userId) {
		return jdbcTemplate.query(SELECT + " WHERE e.id = :enrollmentId AND e.user_id = :userId",
				Map.of("enrollmentId", enrollmentId, "userId", userId), ROW_MAPPER).stream().findFirst();
	}
}
