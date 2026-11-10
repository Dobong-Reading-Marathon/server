package com.dobong.dobong.progress;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 독서기록으로 책별 값을 구하고, 참가자 단위로 합산한다. DB에 접근하지 않는 순수 계산이다.
 *
 * <ul>
 *   <li>읽은 쪽수: 쪽 범위를 합친 쪽 수 ({@link PageRanges})</li>
 *   <li>완독: 읽은 쪽수 = 전체 쪽수. 범위가 1쪽 이상, 전체 쪽수 이하로 걸러져 있으므로 1쪽부터 마지막 쪽까지 빈틈없다는 뜻이다</li>
 *   <li>서평: 승인된 500자 이상 서평이 있으면 1편. 책당 최대 1편</li>
 * </ul>
 */
public final class ReadingProgressCalculator {

	private ReadingProgressCalculator() {
	}

	/**
	 * 참가자별로 합산한다. 기록이 하나도 없는 참가자는 결과에 들어가지 않는다.
	 */
	public static Map<Long, ReadingProgress> calculate(List<ReadingLogRow> rows) {
		Map<Long, Map<Long, List<ReadingLogRow>>> byEnrollmentAndBook = rows.stream()
				.collect(groupingBy(ReadingLogRow::enrollmentId, LinkedHashMap::new,
						groupingBy(ReadingLogRow::enrollmentBookId, LinkedHashMap::new, toList())));

		Map<Long, ReadingProgress> result = new LinkedHashMap<>();
		byEnrollmentAndBook.forEach((enrollmentId, books) ->
				result.put(enrollmentId, sumBooks(enrollmentId, books.values())));
		return result;
	}

	/**
	 * 책별 값 (키: 등록 도서 id). 기록이 하나도 없는 책은 결과에 들어가지 않는다.
	 * 참가자 합산({@link #calculate})과 같은 계산을 쓰므로, 책별 값의 합은 참가자 누적 집계와 같다.
	 */
	public static Map<Long, BookProgress> calculateBooks(List<ReadingLogRow> rows) {
		Map<Long, BookProgress> result = new LinkedHashMap<>();
		rows.stream()
				.collect(groupingBy(ReadingLogRow::enrollmentBookId, LinkedHashMap::new, toList()))
				.forEach((enrollmentBookId, logs) -> result.put(enrollmentBookId, book(enrollmentBookId, logs)));
		return result;
	}

	private static ReadingProgress sumBooks(long enrollmentId, Iterable<List<ReadingLogRow>> books) {
		int readPages = 0;
		int completedBooks = 0;
		int reviewCount = 0;

		for (List<ReadingLogRow> logs : books) {
			BookProgress book = book(logs.get(0).enrollmentBookId(), logs);
			readPages += book.readPages();
			if (book.completed()) {
				completedBooks++;
			}
			if (book.reviewCounted()) {
				reviewCount++;
			}
		}
		return new ReadingProgress(enrollmentId, readPages, completedBooks, reviewCount);
	}

	private static BookProgress book(long enrollmentBookId, List<ReadingLogRow> logs) {
		int totalPages = logs.get(0).totalPages();
		int readPages = PageRanges.countReadPages(logs.stream().map(ReadingLogRow::pageRange).toList());
		boolean reviewCounted = logs.stream().anyMatch(ReadingLogRow::approvedLongReview);
		return new BookProgress(enrollmentBookId, readPages, readPages == totalPages, reviewCounted);
	}
}
