package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageRangesTest {

	private static int count(PageRange... ranges) {
		return PageRanges.countReadPages(List.of(ranges));
	}

	@Test
	@DisplayName("겹치는 범위는 한 번만 센다: 1–120, 100–250 → 250쪽")
	void overlapping() {
		assertThat(count(new PageRange(1, 120), new PageRange(100, 250))).isEqualTo(250);
	}

	@Test
	@DisplayName("중간부터 읽으면 읽은 쪽만 센다: 200–300 → 101쪽")
	void startsInMiddle() {
		assertThat(count(new PageRange(200, 300))).isEqualTo(101);
	}

	@Test
	@DisplayName("빈 구간은 세지 않는다: 1–100, 200–300 → 201쪽")
	void withGap() {
		assertThat(count(new PageRange(1, 100), new PageRange(200, 300))).isEqualTo(201);
	}

	@Test
	@DisplayName("맞닿은 범위는 이어 붙인다: 1–150, 151–300 → 300쪽")
	void adjacent() {
		assertThat(count(new PageRange(1, 150), new PageRange(151, 300))).isEqualTo(300);
	}

	@Test
	@DisplayName("같은 범위를 두 번 읽어도 한 번만 센다: 1–300 두 번 → 300쪽")
	void readTwice() {
		assertThat(count(new PageRange(1, 300), new PageRange(1, 300))).isEqualTo(300);
	}

	@Test
	@DisplayName("다른 범위 안에 들어가는 범위는 더하지 않는다: 1–300, 50–60 → 300쪽")
	void contained() {
		assertThat(count(new PageRange(1, 300), new PageRange(50, 60))).isEqualTo(300);
	}

	@Test
	@DisplayName("입력 순서와 상관없이 같은 결과: 260–300, 100–250, 1–120 → 291쪽")
	void unordered() {
		assertThat(count(new PageRange(260, 300), new PageRange(100, 250), new PageRange(1, 120))).isEqualTo(291);
	}

	@Test
	@DisplayName("기록이 없으면 0쪽")
	void empty() {
		assertThat(PageRanges.countReadPages(List.of())).isZero();
	}

	@Test
	@DisplayName("시작 쪽이 1보다 작거나 끝 쪽보다 크면 만들 수 없다")
	void invalidRange() {
		assertThatThrownBy(() -> new PageRange(0, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageRange(20, 10)).isInstanceOf(IllegalArgumentException.class);
	}
}
