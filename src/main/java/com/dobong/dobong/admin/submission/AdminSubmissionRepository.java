package com.dobong.dobong.admin.submission;

import com.dobong.dobong.readinglog.ReadingLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminSubmissionRepository extends Repository<ReadingLog, Long> {

    // 쿼리 결과 한 줄을 담는 그릇
    interface Row {
        Long getId();
        String getParticipantName();
        String getBookTitle();
        Integer getStartPage();
        Integer getEndPage();
        LocalDate getReadDate();
        String getStatus();
        OffsetDateTime getSubmittedAt();
    }

    // 상태/검색어는 "값이 있으면 적용, 없으면 무시"
    @Query(value = """
            SELECT rl.id           AS "id",
                   u.name          AS "participantName",
                   b.title         AS "bookTitle",
                   rl.start_page   AS "startPage",
                   rl.end_page     AS "endPage",
                   rl.read_date    AS "readDate",
                   rl.status       AS "status",
                   rl.submitted_at AS "submittedAt"
            FROM reading_logs rl
            JOIN enrollment_books eb ON eb.id = rl.enrollment_book_id
            JOIN enrollments e       ON e.id = eb.enrollment_id
            JOIN users u             ON u.id = e.user_id
            JOIN books b             ON b.id = eb.book_id
            WHERE rl.status <> 'DRAFT'
              AND (CAST(:status AS varchar) IS NULL OR rl.status = CAST(:status AS varchar))
              AND (CAST(:keyword AS varchar) IS NULL
                   OR u.name LIKE CAST(:keyword AS varchar)
                   OR b.title LIKE CAST(:keyword AS varchar))
            ORDER BY rl.submitted_at DESC NULLS LAST, rl.id DESC
            """, nativeQuery = true)
    List<Row> search(@Param("status") String status,
                     @Param("keyword") String keywordPattern);

    // 심사 대기 건수
    @Query(value = "SELECT COUNT(*) FROM reading_logs WHERE status = 'SUBMITTED'",
            nativeQuery = true)
    long countPending();

    // [02 추가] 승인: 심사 대기(SUBMITTED)인 건만 가능
    @Modifying
    @Query(value = """
            UPDATE reading_logs
               SET status = 'APPROVED', reviewed_by = :adminId, reviewed_at = now(),
                   librarian_comment = NULL, updated_at = now()
             WHERE id = :id AND status = 'SUBMITTED'
            """, nativeQuery = true)
    int approve(@Param("id") Long id, @Param("adminId") Long adminId);

    // [02 추가] 보완요청: 심사 대기인 건만 가능, 사유 저장
    @Modifying
    @Query(value = """
            UPDATE reading_logs
               SET status = 'REVISION_REQUESTED', reviewed_by = :adminId, reviewed_at = now(),
                   librarian_comment = :comment, updated_at = now()
             WHERE id = :id AND status = 'SUBMITTED'
            """, nativeQuery = true)
    int requestRevision(@Param("id") Long id, @Param("adminId") Long adminId,
                        @Param("comment") String comment);

    // [02 추가] 승인 취소: 승인된 건만 가능, 심사 대기로 되돌림
    @Modifying
    @Query(value = """
            UPDATE reading_logs
               SET status = 'SUBMITTED', reviewed_by = NULL, reviewed_at = NULL,
                   updated_at = now()
             WHERE id = :id AND status = 'APPROVED'
            """, nativeQuery = true)
    int cancelApproval(@Param("id") Long id);

    // [02 추가] 0건 갱신됐을 때 "없는 기록"인지 "상태가 안 맞는 기록"인지 구분용
    @Query(value = "SELECT status FROM reading_logs WHERE id = :id", nativeQuery = true)
    Optional<String> findStatus(@Param("id") Long id);
}