package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.Book;
import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.book.EnrollmentBookRepository;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.ReadingLogRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingLogServiceTest {

    @Mock
    ReadingLogRepository readingLogRepository;

    @Mock
    EnrollmentBookRepository enrollmentBookRepository;

    @Mock
    EnrollmentRepository enrollmentRepository;

    @InjectMocks
    ReadingLogService readingLogService;

    @Test
    @DisplayName("없는 도서면 404")
    void bookNotFound_throwsNotFound() {
        // given: 해당 번호의 내서재 도서가 없음
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.empty());

        ReadingLogRequest request = new ReadingLogRequest(
                1L, 1, 10, LocalDate.now(), null, null);

        // when / then
        assertThrows(ApiException.class,
                () -> readingLogService.create(1L, request));
    }

    @Test
    @DisplayName("내 도서가 아니면 404")
    void notOwnedBook_throwsNotFound() {
        // given: 도서는 있지만 그 참가 신청이 내 것이 아님
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.of(enrollmentBook));
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(false);

        ReadingLogRequest request = new ReadingLogRequest(
                1L, 1, 10, LocalDate.now(), null, null);

        // when / then: 서비스를 부르면 ApiException이 던져진다
        assertThrows(ApiException.class,
                () -> readingLogService.create(1L, request));
    }

    @Test
    @DisplayName("끝 쪽이 시작 쪽보다 작으면 400")
    void endPageBeforeStartPage_throwsBadRequest() {
        // given: 내 도서이고 도서도 있음
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.of(enrollmentBook));
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        // 시작 쪽 20, 끝 쪽 10 (잘못된 범위)
        ReadingLogRequest request = new ReadingLogRequest(
                1L, 20, 10, LocalDate.now(), null, null);

        // when / then
        assertThrows(ApiException.class,
                () -> readingLogService.create(1L, request));
    }

    @Test
    @DisplayName("끝 쪽이 전체 쪽수를 넘으면 400")
    void endPageOverTotalPages_throwsBadRequest() {
        // given: 내 도서이고, 전체 쪽수가 100쪽인 도서
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        Book book = mock(Book.class);
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.of(enrollmentBook));
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        when(enrollmentBook.getBook()).thenReturn(book);
        when(book.getTotalPages()).thenReturn(100);

        // 끝 쪽 150 (전체 쪽수 100을 넘음)
        ReadingLogRequest request = new ReadingLogRequest(
                1L, 1, 150, LocalDate.now(), null, null);

        // when / then
        assertThrows(ApiException.class,
                () -> readingLogService.create(1L, request));
    }

    @Test
    @DisplayName("정상 요청이면 저장하고 DRAFT 상태로 응답")
    void validRequest_savesAndReturnsDraft() {
        // given: 내 도서이고, 전체 쪽수가 100쪽인 도서
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        Book book = mock(Book.class);
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.of(enrollmentBook));
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        when(enrollmentBook.getBook()).thenReturn(book);
        when(book.getTotalPages()).thenReturn(100);
        when(enrollmentBook.getId()).thenReturn(1L);
        // 저장하면 받은 값을 그대로 돌려줌
        when(readingLogRepository.save(any(ReadingLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReadingLogRequest request = new ReadingLogRequest(
                1L, 1, 30, LocalDate.now(), "좋은 문장", "재미있었다");

        // when
        ReadingLogResponse response = readingLogService.create(1L, request);

        // then: 값이 그대로 담기고 상태는 DRAFT
        assertEquals(1L, response.enrollmentBookId());
        assertEquals(1, response.startPage());
        assertEquals(30, response.endPage());
        assertEquals("좋은 문장", response.quote());
        assertEquals("재미있었다", response.review());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, response.status());
        verify(readingLogRepository).save(any(ReadingLog.class));
    }
}