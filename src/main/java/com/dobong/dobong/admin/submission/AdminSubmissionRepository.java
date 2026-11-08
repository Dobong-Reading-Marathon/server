package com.dobong.dobong.admin.submission;

import com.dobong.dobong.readinglog.ReadingLog;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
}