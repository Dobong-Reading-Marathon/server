package com.dobong.dobong.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/**
 * 도서 정보(books 표)를 나타내는 Entity.
 * 책 정보 자체는 전체 회차에서 한 권당 한 줄만 저장하고,
 * 어떤 참가자가 이 책을 등록했는지는 enrollment_books 표에서 연결한다.
 * 아동도서 여부(F-0303)는 책 자체의 성질이라 이 Entity에 저장한다.
 */

@Entity
@Table(name="books")
@Getter
@NoArgsConstructor(access= AccessLevel.PROTECTED)

public class Book {
    @Id // 필드 관리 번호
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false) // 필수
    private String title;

    @Column(nullable=false) // 필수
    private String author;

    @Column(nullable=false) //필수
    private Integer totalPages;

    // 아동도서 여부(F-0303)
    @Column(name="is_children",nullable=false)
    private boolean children;

    private String isbn;
    private String publisher;
    private Integer publishYear;

    // 책 만들때 쓰는 생성자 ( id 는 DB가 만들기 때문에 받지 않음)
    public Book(String isbn,String title,String author,String publisher,
                Integer publishYear,Integer totalPages,boolean children){
        this.isbn = isbn;
        this.title= title;
        this.author = author;
        this.publisher = publisher;
        this.publishYear = publishYear;
        this.totalPages = totalPages;
        this.children = children;
    }

}
