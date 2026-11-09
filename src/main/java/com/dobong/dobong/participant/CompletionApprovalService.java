package com.dobong.dobong.participant;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.participant.dto.CompletionApprovalRequest;
import com.dobong.dobong.participant.dto.CompletionApprovalResponse;
import com.dobong.dobong.progress.Completion;
import com.dobong.dobong.progress.CompletionStatus;
import com.dobong.dobong.progress.ReadingProgressService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완주 승인 (F-0803). 사서가 참가자의 완주를 확정하고 인증서 번호를 붙인다.
 *
 * 승인 조건: 쪽수와 서평 편수가 코스 기준을 채움(완주 승인 대기, F-0601) + 사서가 확인한 대출 권수 ≥ 코스 대출 권수.
 * 조건을 못 채워도 사유(note)를 적으면 승인할 수 있다. 도서관은 오프라인 수첩 기록도 합산하므로 앱 집계만으로는 부족할 수 있다.
 *
 * 승인하면 그 시점의 값은 저장하지 않고 상태(COMPLETED)와 승인 정보만 남긴다. 집계는 계속 조회할 때 계산한다.
 */
@Service
@RequiredArgsConstructor
public class CompletionApprovalService {

	/** 인증서 번호 앞부분. 도서관 양식(Q8)이 정해지면 바꾼다. 예: DB-2027-1-0001 */
	static final String CERTIFICATE_PREFIX = "DB";

	private final JdbcTemplate jdbcTemplate;
	private final ParticipantRepository participantRepository;
	private final ReadingProgressService readingProgressService;
	private final Clock clock;

	@Transactional
	public CompletionApprovalResponse approve(long enrollmentId, long adminId, CompletionApprovalRequest request) {
		// 두 관리자가 같은 참가자를 동시에 승인하지 않게 참가 신청 행을 잠근다
		List<String> status = jdbcTemplate.queryForList(
				"SELECT status FROM enrollments WHERE id = ? FOR UPDATE", String.class, enrollmentId);
		if (status.isEmpty()) {
			throw ApiException.notFound("ENROLLMENT_NOT_FOUND", "참가 신청을 찾을 수 없습니다.");
		}
		if ("COMPLETED".equals(status.get(0))) {
			throw ApiException.conflict("ALREADY_COMPLETED", "이미 완주 승인한 참가자입니다.");
		}

		ParticipantRow row = participantRepository.findById(enrollmentId).orElseThrow();
		Completion completion = Completion.of(row.target(), readingProgressService.getProgress(enrollmentId));
		boolean criteriaMet = completion.status() == CompletionStatus.PENDING_APPROVAL
				&& request.verifiedLoanCount() >= row.target().targetBooks();
		String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
		if (!criteriaMet && note == null) {
			throw ApiException.conflict("COMPLETION_NOT_MET", "완주 기준을 채우지 못했습니다. 승인하려면 사유를 적어 주세요.");
		}

		String certificateNo = nextCertificateNo(row.target().roundId(), row.target().year(), row.target().roundNo());
		Instant now = clock.instant();
		jdbcTemplate.update("""
				UPDATE enrollments
				   SET status = 'COMPLETED', verified_loan_count = ?, approval_note = ?, approved_by = ?,
				       approved_at = ?, certificate_no = ?, updated_at = ?
				 WHERE id = ?""",
				request.verifiedLoanCount(), note, adminId, Timestamp.from(now), certificateNo, Timestamp.from(now),
				enrollmentId);

		return new CompletionApprovalResponse(enrollmentId, CompletionStatus.COMPLETED, certificateNo,
				request.verifiedLoanCount(), note, now);
	}

	/**
	 * 회차별 일련번호. DB-{연도}-{회차 번호}-{4자리}.
	 * 같은 회차를 동시에 승인해도 번호가 겹치지 않게 회차 행을 잠그고 마지막 번호에 1을 더한다.
	 * 잠그는 순서는 늘 참가 신청 → 회차라서 서로 기다리다 멈추는(교착) 일이 없다.
	 */
	private String nextCertificateNo(long roundId, int year, int roundNo) {
		jdbcTemplate.queryForList("SELECT id FROM rounds WHERE id = ? FOR UPDATE", Long.class, roundId);
		String prefix = "%s-%d-%d-".formatted(CERTIFICATE_PREFIX, year, roundNo);
		Integer last = jdbcTemplate.queryForObject("""
				SELECT COALESCE(MAX(CAST(substring(certificate_no FROM ?) AS INTEGER)), 0)
				  FROM enrollments
				 WHERE round_id = ? AND certificate_no LIKE ?""",
				Integer.class, prefix.length() + 1, roundId, prefix + "%");
		return prefix + "%04d".formatted(last + 1);
	}
}
