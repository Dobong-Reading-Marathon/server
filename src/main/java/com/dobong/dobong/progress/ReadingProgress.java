package com.dobong.dobong.progress;

/**
 * 참가자 한 명(회차 참가 신청 하나)의 누적 집계 결과.
 *
 * @param readPages      누적 쪽수. 책별 읽은 쪽수의 합
 * @param completedBooks 완독 권수
 * @param reviewCount    서평 편수. 책당 최대 1편
 */
public record ReadingProgress(long enrollmentId, int readPages, int completedBooks, int reviewCount) {

	/** 1km에 해당하는 쪽수. 환산 비율은 아직 미결(Q2)이라 여기 한 곳에서만 정한다. */
	public static final int PAGES_PER_KM = 1000;

	public static ReadingProgress empty(long enrollmentId) {
		return new ReadingProgress(enrollmentId, 0, 0, 0);
	}

	public double readKm() {
		return readPages / (double) PAGES_PER_KM;
	}
}
