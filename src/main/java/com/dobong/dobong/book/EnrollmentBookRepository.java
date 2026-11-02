package com.dobong.dobong.book;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 내서재(enrollment_books 표)에 책을 저장하고 찾는 도구.
 * save, findById 같은 기본 기능은 JpaRepository가 이미 갖고 있다.
 */

public interface EnrollmentBookRepository extends JpaRepository<EnrollmentBook,Long> {
    //같은 참가 신청이 같은 책을 이미 내서재에 등록했는지 확인한다. (중복 등록 방지)
    boolean existsByEnrollmentIdAndBookId(Long enrollmentId, Long bookId);
}
