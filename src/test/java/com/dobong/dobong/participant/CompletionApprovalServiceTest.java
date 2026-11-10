package com.dobong.dobong.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.participant.dto.CompletionApprovalRequest;
import com.dobong.dobong.participant.dto.CompletionApprovalResponse;
import com.dobong.dobong.participant.dto.EnrollmentDetailResponse;
import com.dobong.dobong.progress.CompletionStatus;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완주 승인(F-0803)의 조건, 인증서 번호, 저장 값을 실제 DB에서 확인한다.
 * 테스트마다 데이터를 넣고, 끝나면 되돌린다 (@Transactional). 실행 전에 로컬 DB가 켜져 있어야 한다.
 */
@SpringBootTest
@Transactional
class CompletionApprovalServiceTest {

	private static final LocalDate IN_PERIOD = LocalDate.of(2027, 3, 1);
	private static final String REVIEW_500 = "가".repeat(500);

	@Autowired
	private CompletionApprovalService service;

	@Autowired
	private ParticipantService participantService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long roundId;
	private long courseId; // 500쪽, 서평 1편, 대출 3권
	private long adminId;

	@BeforeEach
	void setUp() {
		// 다른 테스트나 시연용 데이터와 겹치지 않는 연도
		roundId = insert("INSERT INTO rounds (round_no, year, start_date, end_date) VALUES (1, 2992, '2027-01-05', '2027-11-30') RETURNING id");
		courseId = insert("INSERT INTO courses (round_id, name, target_pages, target_reviews, target_books) VALUES (?, '시험코스', 500, 1, 3) RETURNING id", roundId);
		adminId = insert("INSERT INTO admins (login_id, password_hash, name) VALUES ('completion-test-admin', ?, '사서') RETURNING id",
				new BCryptPasswordEncoder().encode("admin-pass-2027"));
	}

	@Test
	@DisplayName("기준을 채운 참가자를 승인하면 완주가 되고, 회차별 인증서 번호가 차례로 붙는다")
	void approve() {
		long first = pendingParticipant("01070000301");
		long second = pendingParticipant("01070000302");

		CompletionApprovalResponse response = service.approve(first, adminId, new CompletionApprovalRequest(3, null));

		assertThat(response.status()).isEqualTo(CompletionStatus.COMPLETED);
		assertThat(response.certificateNo()).isEqualTo("DB-2992-1-0001");
		assertThat(response.approvalNote()).isNull();
		assertThat(service.approve(second, adminId, new CompletionApprovalRequest(5, "  ")).certificateNo())
				.isEqualTo("DB-2992-1-0002");

		Map<String, Object> saved = jdbcTemplate.queryForMap(
				"SELECT status, verified_loan_count, approved_by, approved_at, certificate_no FROM enrollments WHERE id = ?", first);
		assertThat(saved.get("status")).isEqualTo("COMPLETED");
		assertThat(saved.get("verified_loan_count")).isEqualTo(3);
		assertThat(saved.get("approved_by")).isEqualTo(adminId);
		assertThat(saved.get("approved_at")).isNotNull();

		// 사용자별 기록 조회에도 승인 정보가 보인다
		EnrollmentDetailResponse detail = participantService.getDetail(first);
		assertThat(detail.status()).isEqualTo(CompletionStatus.COMPLETED);
		assertThat(detail.certificateNo()).isEqualTo("DB-2992-1-0001");
		assertThat(detail.verifiedLoanCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("이미 완주한 참가자는 다시 승인할 수 없다 (409)")
	void alreadyCompleted() {
		long enrollment = pendingParticipant("01070000303");
		service.approve(enrollment, adminId, new CompletionApprovalRequest(3, null));

		assertError(() -> service.approve(enrollment, adminId, new CompletionApprovalRequest(3, null)),
				HttpStatus.CONFLICT, "ALREADY_COMPLETED");
	}

	@Test
	@DisplayName("쪽수·서평 기준이나 대출 권수가 모자라면 사유 없이는 승인할 수 없다 (409)")
	void criteriaNotMet() {
		long shortPages = enroll("01070000304");
		addLog(addBook(shortPages, 300), 1, 300, REVIEW_500, "APPROVED"); // 300 / 500쪽
		long pending = pendingParticipant("01070000305");

		assertError(() -> service.approve(shortPages, adminId, new CompletionApprovalRequest(3, null)),
				HttpStatus.CONFLICT, "COMPLETION_NOT_MET");
		// 쪽수, 서평은 채웠지만 대출 권수 2 < 3
		assertError(() -> service.approve(pending, adminId, new CompletionApprovalRequest(2, null)),
				HttpStatus.CONFLICT, "COMPLETION_NOT_MET");
	}

	@Test
	@DisplayName("기준을 못 채워도 사유를 적으면 승인하고 사유를 남긴다")
	void approveWithNote() {
		long enrollment = enroll("01070000306"); // 기록 없음

		CompletionApprovalResponse response = service.approve(enrollment, adminId,
				new CompletionApprovalRequest(10, " 오프라인 수첩 합산 "));

		assertThat(response.status()).isEqualTo(CompletionStatus.COMPLETED);
		assertThat(response.approvalNote()).isEqualTo("오프라인 수첩 합산");
		assertThat(jdbcTemplate.queryForObject("SELECT approval_note FROM enrollments WHERE id = ?", String.class, enrollment))
				.isEqualTo("오프라인 수첩 합산");
	}

	@Test
	@DisplayName("없는 참가 신청은 404")
	void notFound() {
		assertError(() -> service.approve(Long.MAX_VALUE, adminId, new CompletionApprovalRequest(3, null)),
				HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND");
	}

	/** 500쪽, 승인된 500자 서평 1편을 채운 참가자 (완주 승인 대기) */
	private long pendingParticipant(String phone) {
		long enrollment = enroll(phone);
		addLog(addBook(enrollment, 500), 1, 500, REVIEW_500, "APPROVED");
		return enrollment;
	}

	private long enroll(String phone) {
		long userId = insert("INSERT INTO users (name, phone, password_hash, privacy_agreed_at, privacy_agreed_version) VALUES ('테스트', ?, 'x', now(), 1) RETURNING id", phone);
		return insert("INSERT INTO enrollments (user_id, round_id, course_id) VALUES (?, ?, ?) RETURNING id", userId, roundId, courseId);
	}

	private long addBook(long enrollmentId, int totalPages) {
		long bookId = insert("INSERT INTO books (title, author, total_pages) VALUES ('책', '저자', ?) RETURNING id", totalPages);
		return insert("INSERT INTO enrollment_books (enrollment_id, book_id, is_library_owned) VALUES (?, ?, true) RETURNING id",
				enrollmentId, bookId);
	}

	private void addLog(long enrollmentBookId, int start, int end, String review, String status) {
		jdbcTemplate.update("INSERT INTO reading_logs (enrollment_book_id, start_page, end_page, read_date, review, status) VALUES (?, ?, ?, ?, ?, ?)",
				enrollmentBookId, start, end, IN_PERIOD, review, status);
	}

	private static void assertError(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	private long insert(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Long.class, args);
	}
}
