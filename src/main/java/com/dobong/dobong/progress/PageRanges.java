package com.dobong.dobong.progress;

import java.util.Comparator;
import java.util.List;

/**
 * 책 한 권의 읽은 쪽수를 계산한다.
 *
 * 쪽 범위들을 합쳐서 센다. 겹치는 쪽은 한 번만 세고, 맞닿은 범위(150 다음 151)는 이어 붙인다.
 * 재독(같은 쪽을 다시 읽음)을 세는 규칙이 바뀌면 이 클래스만 바꾼다.
 */
public final class PageRanges {

	private PageRanges() {
	}

	public static int countReadPages(List<PageRange> ranges) {
		if (ranges.isEmpty()) {
			return 0;
		}

		List<PageRange> sorted = ranges.stream()
				.sorted(Comparator.comparingInt(PageRange::start))
				.toList();

		int readPages = 0;
		int currentStart = sorted.get(0).start();
		int currentEnd = sorted.get(0).end();

		for (PageRange range : sorted.subList(1, sorted.size())) {
			if (range.start() <= currentEnd + 1) {
				currentEnd = Math.max(currentEnd, range.end());
			} else {
				readPages += currentEnd - currentStart + 1;
				currentStart = range.start();
				currentEnd = range.end();
			}
		}
		return readPages + currentEnd - currentStart + 1;
	}
}
