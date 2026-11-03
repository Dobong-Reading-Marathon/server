package com.dobong.dobong.book;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BookServiceTest {
    private BookRepository bookRepository;
    private EnrollmentBookRepository enrollmentBookRepository;
    private BookService bookService;

    @BeforeEach
    void setUp(){
        bookRepository = mock(BookRepository.class);
        enrollmentBookRepository = mock(EnrollmentBookRepository.class);
        bookService = new BookService(bookRepository,enrollmentBookRepository);

        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentBookRepository.save(any(EnrollmentBook.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentBookRepository.existsByEnrollmentIdAndBookId(any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("아동도서 여부를 보내지 않으면 false")
    void childrenDefaultsToFalse(){
        BookRegisterRequest request = new BookRegisterRequest(
                1L ,"어린왕자","생택쥐페리",100,
                null,null,null,
                null,null,null,null,
                null);

        EnrollmentBookResponse response = bookService.register(request);

        assertThat(response.children()).isFalse();
    }

    @Test
    @DisplayName("아동도서 여부를 보내면 True")
    void childrenDefaultsTrue(){
        BookRegisterRequest request = new BookRegisterRequest(
                1L ,"어린왕자","생택쥐페리",100,
                null,null,null,
                null,null,null,null,
                true);

        EnrollmentBookResponse response = bookService.register(request);

        assertThat(response.children()).isTrue();
    }

    @Test
    @DisplayName("대출 권수는 레포지토리가 센 소장도서 수를 그대로 돌려준다")
    void countLoanBooksReturnsRepositoryCount(){
        when(enrollmentBookRepository.countByEnrollmentIdAndLibraryOwnedTrue(1L)).thenReturn(2L);

        long count = bookService.countLoanBooks(1L);

        assertThat(count).isEqualTo(2L);
    }


}