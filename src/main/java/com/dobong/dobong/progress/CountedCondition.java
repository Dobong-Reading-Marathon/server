package com.dobong.dobong.progress;

/**
 * 독서기록이 누적 집계(F-0502)에 들어가는 공통 조건. 소장도서, 끝 쪽 ≤ 책 전체 쪽수, 읽은 날짜가 회차 기간 안.
 *
 * 집계, 독서달력(F-0504), 최근 기록(F-0501)이 같은 조건을 쓰도록 SQL 조각을 한 곳에 둔다.
 * 조건을 바꾸면 세 기능에 함께 반영된다.
 *
 * 쓰는 쪽 SQL은 테이블 별칭을 reading_logs rl, enrollment_books eb, books b, rounds r로 맞춰야 한다.
 */
public final class CountedCondition {

	public static final String SQL = """
			eb.is_library_owned
			  AND rl.end_page <= b.total_pages
			  AND rl.read_date BETWEEN r.start_date AND r.end_date""";

	private CountedCondition() {
	}
}
