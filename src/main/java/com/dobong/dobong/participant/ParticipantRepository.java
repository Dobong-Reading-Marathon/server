package com.dobong.dobong.participant;

import com.dobong.dobong.progress.EnrollmentTargetRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회차 참가자를 코스 기준, 회원 정보와 함께 조회한다. 검색어와 코스 조건은 SQL에서 거른다.
 * 완주 상태는 집계가 있어야 알 수 있어서 서비스에서 거른다.
 */
@Repository
@RequiredArgsConstructor
public class ParticipantRepository {

	private static final String SELECT = "SELECT " + EnrollmentTargetRepository.COLUMNS + """
			,
			       u.id AS user_id,
			       u.name,
			       u.phone,
			       u.loan_member_no,
			       e.certificate_no,
			       e.approved_at,
			       e.created_at AS enrolled_at
			FROM enrollments e
			JOIN rounds r  ON r.id = e.round_id
			JOIN courses c ON c.id = e.course_id
			JOIN users u   ON u.id = e.user_id
			""";

	private static final String ORDER = " ORDER BY e.id";

	// 숫자, 하이픈, 공백, 점으로만 된 검색어는 전화번호로도 찾는다 (예: 010-1234, 5678)
	private static final Pattern PHONE_LIKE = Pattern.compile("^[0-9\\s.\\-]+$");

	private static final RowMapper<ParticipantRow> ROW_MAPPER = (rs, rowNum) -> new ParticipantRow(
			EnrollmentTargetRepository.ROW_MAPPER.mapRow(rs, rowNum),
			rs.getLong("user_id"),
			rs.getString("name"),
			rs.getString("phone"),
			rs.getString("loan_member_no"),
			rs.getString("certificate_no"),
			toInstant(rs.getTimestamp("approved_at")),
			rs.getTimestamp("enrolled_at").toInstant());

	private final NamedParameterJdbcTemplate jdbcTemplate;

	/**
	 * 회차 참가자를 참가 신청 순서로 조회한다.
	 *
	 * @param courseId null이면 모든 코스
	 * @param keyword  null이면 거르지 않음. 이름 일부, 또는 하이픈을 뺀 전화번호 일부와 맞춘다
	 */
	public List<ParticipantRow> findByRound(long roundId, Long courseId, String keyword) {
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE e.round_id = :roundId");
		MapSqlParameterSource params = new MapSqlParameterSource("roundId", roundId);

		if (courseId != null) {
			sql.append(" AND e.course_id = :courseId");
			params.addValue("courseId", courseId);
		}
		if (keyword != null) {
			sql.append(" AND (u.name ILIKE :name ESCAPE '\\'");
			params.addValue("name", "%" + escapeLike(keyword) + "%");

			String digits = keyword.replaceAll("\\D", "");
			if (PHONE_LIKE.matcher(keyword).matches() && !digits.isEmpty()) {
				sql.append(" OR u.phone LIKE :phone");
				params.addValue("phone", "%" + digits + "%");
			}
			sql.append(")");
		}
		return jdbcTemplate.query(sql.append(ORDER).toString(), params, ROW_MAPPER);
	}

	/** 참가 신청 하나 (사용자별 기록 조회 F-0802) */
	public Optional<ParticipantRow> findById(long enrollmentId) {
		return jdbcTemplate.query(SELECT + " WHERE e.id = :enrollmentId", Map.of("enrollmentId", enrollmentId), ROW_MAPPER)
				.stream().findFirst();
	}

	private static Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	// 검색어의 %, _ 를 글자 그대로 찾게 한다. 안 하면 "%" 하나로 모든 참가자가 나옴
	static String escapeLike(String keyword) {
		return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
