package com.dobong.dobong.library;

import com.dobong.dobong.book.EnrollmentBook;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 내서재 조회 (F-0503) */
public interface LibraryRepository extends JpaRepository<EnrollmentBook, Long> {

    // 신청들의 서재 책 목록 (Book까지 한 번에 가져와 N+1 방지, 최근 등록순)
    @EntityGraph(attributePaths = "book")
    List<EnrollmentBook> findByEnrollmentIdInOrderByIdDesc(List<Long> enrollmentIds);
}