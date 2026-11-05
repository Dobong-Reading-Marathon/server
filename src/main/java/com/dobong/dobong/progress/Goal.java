package com.dobong.dobong.progress;

/**
 * 완주 기준 하나(쪽수, 서평 편수, 권수)의 달성 정도.
 *
 * @param current 지금까지 채운 값
 * @param target  코스 기준
 */
public record Goal(int current, int target) {

	/** 남은 값. 기준을 넘겼으면 0 */
	public int remaining() {
		return Math.max(0, target - current);
	}

	/**
	 * 진행률(%). 내림하고 100에서 멈춘다.
	 * 반올림하면 99.6%가 100%로 보여서, 아직 못 채웠는데 다 채운 것처럼 보이기 때문.
	 */
	public int percent() {
		if (target <= 0) {
			return 100;
		}
		return (int) Math.min(100, current * 100L / target);
	}

	public boolean met() {
		return current >= target;
	}
}
