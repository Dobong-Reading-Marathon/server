package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.EnrollmentBook;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

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

    //기록 상태 : 작성중 ->제출 -> 승인
    public enum ReadingLogStatus {
        DRAFT,               // 작성중
        SUBMITTED,           // 제출
        APPROVED,            // 승인
        REVISION_REQUESTED   // 수정 요청
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

    // 서평을 수정할 수 있는 상태인가 ( 작성중 또는 수정 요청 )
    public boolean isEditable(){
        return status == ReadingLogStatus.DRAFT
                || status == ReadingLogStatus.REVISION_REQUESTED;
    }


}
