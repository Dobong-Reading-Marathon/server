package com.dobong.dobong.participant;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse;
import com.dobong.dobong.participant.dto.ParticipantPageResponse;
import com.dobong.dobong.participant.dto.ParticipantResponse;
import com.dobong.dobong.progress.ReadingProgress;
import com.dobong.dobong.progress.ReadingProgressService;
import com.dobong.dobong.round.RoundRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 참가자 관리 (F-0801 참가자 목록, F-0802 사용자별 기록 조회, F-0804 명단 다운로드)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipantService {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;
	static final int MAX_KEYWORD_LENGTH = 50;

	private final RoundRepository roundRepository;
	private final ParticipantRepository participantRepository;
	private final ReadingProgressService readingProgressService;
	private final EnrollmentBookDetailRepository enrollmentBookDetailRepository;

	/** 참가자 한 명의 정보, 누적 집계, 등록 도서별 기록 전체 (F-0802). 없는 참가 신청은 404 */
	public EnrollmentDetailResponse getDetail(long enrollmentId) {
		ParticipantRow row = participantRepository.findById(enrollmentId)
				.orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다."));
		Participant participant = Participant.of(row, readingProgressService.getProgress(enrollmentId));
		return EnrollmentDetailResponse.of(participant,
				enrollmentBookDetailRepository.findBooks(enrollmentId),
				readingProgressService.getBookProgress(enrollmentId),
				enrollmentBookDetailRepository.findLogs(enrollmentId));
	}

	/** 참가자 목록 한 페이지. page는 0부터 */
	public ParticipantPageResponse getPage(long roundId, ParticipantFilter filter, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_SIZE) {
			throw ApiException.badRequest("INVALID_INPUT", "입력값을 확인해 주세요.");
		}
		List<Participant> all = findAll(roundId, filter);

		int from = (int) Math.min((long) page * size, all.size());
		int to = Math.min(from + size, all.size());
		List<ParticipantResponse> content = all.subList(from, to).stream().map(ParticipantResponse::from).toList();
		int totalPages = (all.size() + size - 1) / size;
		return new ParticipantPageResponse(roundId, page, size, all.size(), totalPages, content);
	}

	/** 조건에 맞는 참가자 전체를 참가 신청 순서로. 명단 다운로드(F-0804)는 페이지 없이 이걸 쓴다 */
	public List<Participant> findAll(long roundId, ParticipantFilter filter) {
		if (!roundRepository.existsById(roundId)) {
			throw ApiException.notFound("ROUND_NOT_FOUND", "회차를 찾을 수 없습니다.");
		}
		String keyword = normalizeKeyword(filter.keyword());
		Map<Long, ReadingProgress> progressByEnrollment = readingProgressService.getProgressByRound(roundId);

		return participantRepository.findByRound(roundId, filter.courseId(), keyword).stream()
				.map(row -> {
					long enrollmentId = row.target().enrollmentId();
					return Participant.of(row, progressByEnrollment.getOrDefault(enrollmentId, ReadingProgress.empty(enrollmentId)));
				})
				.filter(p -> filter.status() == null || p.completion().status() == filter.status())
				.toList();
	}

	// 앞뒤 공백을 지우고, 비어 있으면 거르지 않는다 (null)
	private static String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		String trimmed = keyword.strip();
		if (trimmed.length() > MAX_KEYWORD_LENGTH) {
			throw ApiException.badRequest("INVALID_INPUT", "입력값을 확인해 주세요.");
		}
		return trimmed;
	}
}
