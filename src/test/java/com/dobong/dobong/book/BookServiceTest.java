package com.dobong.dobong.book;

import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BookServiceTest {
    private static final Long USER_ID = 7L;

    private BookRepository bookRepository;
    private EnrollmentBookRepository enrollmentBookRepository;
    private EnrollmentRepository enrollmentRepository;
    private BookService bookService;

    @BeforeEach
    void setUp(){
        bookRepository = mock(BookRepository.class);
        enrollmentBookRepository = mock(EnrollmentBookRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        bookService = new BookService(bookRepository,enrollmentBookRepository,enrollmentRepository);

        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentBookRepository.save(any(EnrollmentBook.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentBookRepository.existsByEnrollmentIdAndBookId(any(), any())).thenReturn(false);
        // 기본값 : enrollmentId 1은 USER_ID 본인의 참가 신청
        when(enrollmentRepository.existsByIdAndUserId(1L, USER_ID)).thenReturn(true);
    }

    @Test
    @DisplayName("아동도서 여부를 보내지 않으면 false")
    void childrenDefaultsToFalse(){
        BookRegisterRequest request = new BookRegisterRequest(
                1L ,"어린왕자","생택쥐페리",100,
                null,null,null,
                null,null,null,null,
                null);

        EnrollmentBookResponse response = bookService.register(USER_ID, request);

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

        EnrollmentBookResponse response = bookService.register(USER_ID, request);

        assertThat(response.children()).isTrue();
    }

    @Test
    @DisplayName("내 참가 신청이면 등록에 성공한다")
    void registerSucceedsForOwnEnrollment(){
        BookRegisterRequest request = new BookRegisterRequest(
                1L ,"어린왕자","생택쥐페리",100,
                null,null,null,
                null,null,null,null,
                null);

        EnrollmentBookResponse response = bookService.register(USER_ID, request);

        assertThat(response).isNotNull();
        verify(bookRepository).save(any(Book.class));
        verify(enrollmentBookRepository).save(any(EnrollmentBook.class));
    }

    @Test
    @DisplayName("남의 참가 신청이면 404 ENROLLMENT_NOT_FOUND이고 아무것도 저장하지 않는다")
    void registerFailsForOthersEnrollment(){
        // enrollmentId 2는 다른 사용자의 것 : USER_ID 기준으로는 존재하지 않음
        when(enrollmentRepository.existsByIdAndUserId(2L, USER_ID)).thenReturn(false);
        BookRegisterRequest request = new BookRegisterRequest(
                2L ,"어린왕자","생택쥐페리",100,
                "9788937460470",null,null,
                null,null,null,null,
                null);

        assertThatThrownBy(() -> bookService.register(USER_ID, request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verifyNoInteractions(bookRepository, enrollmentBookRepository);
    }

    @Test
    @DisplayName("없는 참가 신청이면 404 ENROLLMENT_NOT_FOUND이고 아무것도 저장하지 않는다")
    void registerFailsForMissingEnrollment(){
        when(enrollmentRepository.existsByIdAndUserId(999L, USER_ID)).thenReturn(false);
        BookRegisterRequest request = new BookRegisterRequest(
                999L ,"어린왕자","생택쥐페리",100,
                null,null,null,
                null,null,null,null,
                null);

        assertThatThrownBy(() -> bookService.register(USER_ID, request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("ENROLLMENT_NOT_FOUND");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(bookRepository, never()).save(any(Book.class));
        verify(enrollmentBookRepository, never()).save(any(EnrollmentBook.class));
    }

    @Test
    @DisplayName("대출 권수는 레포지토리가 센 소장도서 수를 그대로 돌려준다")
    void countLoanBooksReturnsRepositoryCount(){
        when(enrollmentBookRepository.countByEnrollmentIdAndLibraryOwnedTrue(1L)).thenReturn(2L);

        long count = bookService.countLoanBooks(1L);

        assertThat(count).isEqualTo(2L);
    }


}
