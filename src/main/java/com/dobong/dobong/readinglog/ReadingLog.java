package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.EnrollmentBook;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 독서기록 (reading_logs). 내서재 도서 한 권에 읽은 쪽 범위(시작~끝 쪽, 읽은 날짜)를 기록한다.
 * 한 도서에 여러 기록 가능, 글귀·서평은 선택. 생성 시 상태는 DRAFT. (F-0401)
 */

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reading_logs")

public class ReadingLog {

    //기록 번호
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기록 대상 내 서재 도서(기록 여러개 : 도서 1개, 필요할 때만 조회)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_book_id")
    private EnrollmentBook enrollmentBook;

    // 시작 쪽수
    @Column(nullable = false)
    private Integer startPage;

    // 끝 쪽수
    @Column(nullable = false)
    private Integer endPage;

    // 읽은 날짜
    @Column(nullable = false)
    private LocalDate readDate;

    // 마음에 드는 글귀
    @Column(columnDefinition = "TEXT")
    private String quote;

    // 서평
    @Column(columnDefinition = "TEXT")
    private String review;

    // 기록 상태 : 문자열로 저장
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReadingLogStatus status;

    // 제출 일시
    @Column
    private OffsetDateTime submittedAt;

    // 사서 코멘트
    @Column(columnDefinition = "TEXT")
    private String librarianComment;

    // 심사 시각
    @Column
    private OffsetDateTime reviewedAt;

    // 심사를 받을 수 있는 서평 글자 수. 이보다 짧은 서평은 서평 편수에 들어가지 않는다.
    public static final int MIN_REVIEW_LENGTH = 500;

    // 기록 상태
    // 작성중 -> 제출: 서평 500자 이상이면 SUBMITTED(심사) -> APPROVED / REVISION_REQUESTED
    //               서평 500자 미만이면 SAVED(저장). 심사 없이 끝난 기록이고, 고쳐서 다시 제출할 수 있다
    public enum ReadingLogStatus {
        DRAFT,               // 작성중
        SUBMITTED,           // 제출 (심사 대기)
        APPROVED,            // 승인
        REVISION_REQUESTED,  // 수정 요청
        SAVED                // 저장 (서평 500자 미만으로 제출, 심사 안 함)
    }

    public ReadingLog(EnrollmentBook enrollmentBook, Integer startPage,Integer endPage,
                      LocalDate readDate,String quote,String review){
        this.enrollmentBook = enrollmentBook;
        this.startPage= startPage;
        this.endPage = endPage;
        this.readDate = readDate;
        this.quote = quote;
        this.review = review;

        this.status = ReadingLogStatus.DRAFT;
    }

    // 서평 수정
    public void updateReview(String review){
        this.review=review;
    }

    // 기록 수정
    public void updateLog(Integer startPage, Integer endPage, LocalDate readDate,
                          String quote, String review){
        this.startPage = startPage;
        this.endPage = endPage;
        this.readDate = readDate;
        this.quote = quote;
        this.review = review;
    }

    // 기록을 수정·삭제할 수 있는 상태 (작성중, 보완요청, 저장)
    public boolean isEditable(){
        return status == ReadingLogStatus.DRAFT
                || status == ReadingLogStatus.REVISION_REQUESTED
                || status == ReadingLogStatus.SAVED;
    }

    // 제출할 수 있는 상태인가 (작성중, 보완요청, 저장)
    // 저장 기록은 서평을 500자 이상으로 고친 뒤 다시 제출해서 심사를 받을 수 있다.
    public boolean isSubmittable(){
        return isEditable();
    }

    // 서평이 심사를 받을 만큼 긴가 (글자 수는 코드 포인트로 센다. DB의 char_length와 같은 기준)
    public boolean hasReviewForApproval(){
        return review != null && review.codePointCount(0, review.length()) >= MIN_REVIEW_LENGTH;
    }

    // 제출 처리: 서평이 500자 이상이면 심사 대기, 아니면 심사 없이 저장
    // 저장은 심사 제출이 아니므로 제출 일시를 남기지 않는다.
    public void submit(OffsetDateTime submittedAt){
        if (hasReviewForApproval()) {
            this.status = ReadingLogStatus.SUBMITTED;
            this.submittedAt = submittedAt;
        } else {
            this.status = ReadingLogStatus.SAVED;
            this.submittedAt = null;
        }
    }

}
