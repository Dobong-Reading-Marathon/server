package com.dobong.dobong.book;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * books 표에 책을 저장하고 찾는 도구.
 * save, findById 같은 기본 기능은 JpaRepository가 이미 갖고 있다.
 */

public interface BookRepository extends JpaRepository<Book, Long> {
    //ISBN으로 책들을 찾음. 같은 ISBN의 책이 이미 있으면 새로 만들지 않고,그 책 재사용
    Optional<Book> findByIsbn(String isbn);
}
