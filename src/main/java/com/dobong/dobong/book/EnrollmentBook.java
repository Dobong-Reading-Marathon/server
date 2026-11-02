package com.dobong.dobong.book;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GenerationType;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내서재의 책 한 줄 (enrollment_books 표).
 * "이 참가 신청이 이 책을 등록했다"는 연결 기록이다.
 * 책 정보 자체는 books 표(Book)에 한 번만 저장하고,
 * 소장 여부, 도서등록번호, 대출일, 반납일처럼 사람마다 달라지는 정보는 여기에 저장한다.
 */

@Entity
@Table(name="enrollment_books")
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)

public class EnrollmentBook {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 참가 신청인지. Enrollment Entity가 아직 없어서 번호만 담아 둔다.
    @Column(name="enrollment_id",nullable=false)
    private Long enrollmentId;

    //어느 책인지. 같은 책을 여러 참가자가 등록할 수 있어서 여러 개 → 하나(ManyToOne).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name="is_library_owned",nullable=false)
    private boolean libraryOwned; // 도서관 소장도서 여부

    private String registrationNo; // 도서등록번호
    private LocalDate loanDate; // 대출일
    private LocalDate returnDate; // 반납일

    // 내서재에 책을 등록할 때 쓰는 생성자
    public EnrollmentBook(Long enrollmentId,Book book,boolean libraryOwned,
                          String registrationNo,LocalDate loanDate,LocalDate returnDate){
        this.enrollmentId = enrollmentId;
        this.book=book;
        this.libraryOwned= libraryOwned;
        this.registrationNo=registrationNo;
        this.loanDate=loanDate;
        this.returnDate=returnDate;
    }
}
