package com.dobong.dobong.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoalTest {

	@Test
	@DisplayName("남은 값은 기준을 넘기면 0이다")
	void remaining() {
		assertThat(new Goal(12450, 17500).remaining()).isEqualTo(5050);
		assertThat(new Goal(17500, 17500).remaining()).isZero();
		assertThat(new Goal(20000, 17500).remaining()).isZero();
	}

	@Test
	@DisplayName("진행률은 내림하고 100에서 멈춘다")
	void percent() {
		assertThat(new Goal(17430, 17500).percent()).isEqualTo(99); // 99.6%를 100%로 올리지 않음
		assertThat(new Goal(17500, 17500).percent()).isEqualTo(100);
		assertThat(new Goal(30000, 17500).percent()).isEqualTo(100);
		assertThat(new Goal(0, 10).percent()).isZero();
	}

	@Test
	@DisplayName("기준과 같으면 채운 것이다")
	void met() {
		assertThat(new Goal(10, 10).met()).isTrue();
		assertThat(new Goal(9, 10).met()).isFalse();
	}
}
