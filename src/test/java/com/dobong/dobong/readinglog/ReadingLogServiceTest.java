package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.Book;
import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.book.EnrollmentBookRepository;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;

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
    @Test
    @DisplayName("서평 없이 임시저장해도 DRAFT 상태로 응답")
    void noReview_savesAsDraft() {
        // given: 내 도서이고, 전체 쪽수가 100쪽인 도서
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        Book book = mock(Book.class);
        when(enrollmentBookRepository.findById(1L)).thenReturn(Optional.of(enrollmentBook));
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        when(enrollmentBook.getBook()).thenReturn(book);
        when(book.getTotalPages()).thenReturn(100);
        when(enrollmentBook.getId()).thenReturn(1L);
        when(readingLogRepository.save(any(ReadingLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 글귀와 서평이 모두 없는 요청 (임시저장)
        ReadingLogRequest request = new ReadingLogRequest(
                1L, 1, 30, LocalDate.now(), null, null);

        // when
        ReadingLogResponse response = readingLogService.create(1L, request);

        // then: 글귀와 서평은 비어 있어도 저장되고 상태는 DRAFT
        assertEquals(1L, response.enrollmentBookId());
        assertEquals(1, response.startPage());
        assertEquals(30, response.endPage());
        assertNull(response.quote());
        assertNull(response.review());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, response.status());
        verify(readingLogRepository).save(any(ReadingLog.class));
    }

    @Test
    @DisplayName("DRAFT 상태의 서평을 수정하면 저장된다")
    void updateReview_draft_success() {
        // given: 내 기록이고 상태가 DRAFT
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        when(readingLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ReviewUpdateRequest request = new ReviewUpdateRequest("재미있었다");

        // when
        ReviewUpdateResponse response = readingLogService.updateReview(1L, 1L, request);

        // then: 서평이 바뀌고 글자 수가 계산된다
        assertEquals("재미있었다", response.review());
        assertEquals(5, response.charCount());
        assertEquals(false, response.meetsMinLength());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, response.status());
    }
    @Test
    @DisplayName("REVISION_REQUESTED 상태에서도 서평을 수정할 수 있다")
    void updateReview_revisionRequested_success() {
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, "처음 서평");
        ReflectionTestUtils.setField(log, "status", ReadingLog.ReadingLogStatus.REVISION_REQUESTED);
        when(readingLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ReviewUpdateResponse response = readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("고친 서평"));

        assertEquals("고친 서평", response.review());
        assertEquals(ReadingLog.ReadingLogStatus.REVISION_REQUESTED, response.status());
    }

    @Test
    @DisplayName("없는 기록이면 404")
    void updateReview_notFound_throws() {
        when(readingLogRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("서평")));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("READING_LOG_NOT_FOUND", e.getCode());
    }

    @Test
    @DisplayName("남의 기록이면 404")
    void updateReview_notOwned_throws() {
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        when(readingLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(false);

        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("서평")));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("READING_LOG_NOT_FOUND", e.getCode());
    }

    @Test
    @DisplayName("SUBMITTED 상태면 409")
    void updateReview_submitted_throws() {
        assertNotEditable(ReadingLog.ReadingLogStatus.SUBMITTED);
    }

    @Test
    @DisplayName("APPROVED 상태면 409")
    void updateReview_approved_throws() {
        assertNotEditable(ReadingLog.ReadingLogStatus.APPROVED);
    }

    @Test
    @DisplayName("499자는 미충족, 500자는 충족")
    void updateReview_minLengthBoundary() {
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        when(readingLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ReviewUpdateResponse r499 = readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("가".repeat(499)));
        assertEquals(499, r499.charCount());
        assertEquals(false, r499.meetsMinLength());

        ReviewUpdateResponse r500 = readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("가".repeat(500)));
        assertEquals(500, r500.charCount());
        assertEquals(true, r500.meetsMinLength());
    }

    // 수정 불가 상태 공통 검증: 409 + READING_LOG_NOT_EDITABLE, 서평은 그대로
    private void assertNotEditable(ReadingLog.ReadingLogStatus status) {
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, "기존 서평");
        ReflectionTestUtils.setField(log, "status", status);
        when(readingLogRepository.findById(1L)).thenReturn(Optional.of(log));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.updateReview(1L, 1L, new ReviewUpdateRequest("바꾸려는 서평")));

        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("READING_LOG_NOT_EDITABLE", e.getCode());
        assertEquals("기존 서평", log.getReview());
    }
    // 정상 제출: DRAFT 기록 여러 건을 제출하면 SUBMITTED로 바뀌고 제출 일시가 채워지는지 확인
    @Test
    @DisplayName("DRAFT 기록을 일괄 제출하면 SUBMITTED로 바뀌고 제출 일시가 채워진다")
    void submit_drafts_success() {
        // given: 내 기록 2건, 둘 다 DRAFT
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog log1 = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        ReadingLog log2 = new ReadingLog(enrollmentBook, 11, 20, LocalDate.now(), null, null);
        ReflectionTestUtils.setField(log1, "id", 1L);
        ReflectionTestUtils.setField(log2, "id", 2L);
        when(readingLogRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(log1, log2));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ReadingLogSubmitRequest request = new ReadingLogSubmitRequest(List.of(1L, 2L));

        // when
        ReadingLogSubmitResponse response = readingLogService.submit(1L, request);

        // then: 상태와 제출 일시가 바뀌고, 응답에 두 기록의 번호가 담긴다
        assertEquals(ReadingLog.ReadingLogStatus.SUBMITTED, log1.getStatus());
        assertEquals(ReadingLog.ReadingLogStatus.SUBMITTED, log2.getStatus());
        assertNotNull(log1.getSubmittedAt());
        assertNotNull(log2.getSubmittedAt());
        assertEquals(List.of(1L, 2L), response.submittedIds());
    }
    // 없는 기록이 섞이면 404를 던지고, 있는 기록도 제출하지 않는지 확인
    @Test
    @DisplayName("없는 기록이 섞여 있으면 404")
    void submit_notFound_throws() {
        // given: 1번, 2번을 요청했지만 DB에는 1번만 있음
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        ReadingLog log1 = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        ReflectionTestUtils.setField(log1, "id", 1L);
        // 조회 결과가 요청보다 적게 나오도록 흉내 낸다
        when(readingLogRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(log1));

        ReadingLogSubmitRequest request = new ReadingLogSubmitRequest(List.of(1L, 2L));

        // when: 일괄 제출
        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.submit(1L, request));

        // then: 404 + 코드 확인, 있던 1번 기록도 제출되지 않음
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("READING_LOG_NOT_FOUND", e.getCode());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, log1.getStatus());
        assertNull(log1.getSubmittedAt());
    }
    // 남의 기록이 섞이면 404를 던지고, 내 기록도 제출하지 않는지 확인
    @Test
    @DisplayName("남의 기록이 섞여 있으면 404")
    void submit_notOwned_throws() {
        // given: 1번은 내 기록(참가 신청 10), 2번은 남의 기록(참가 신청 20)
        EnrollmentBook myBook = mock(EnrollmentBook.class);
        EnrollmentBook otherBook = mock(EnrollmentBook.class);
        when(myBook.getEnrollmentId()).thenReturn(10L);
        when(otherBook.getEnrollmentId()).thenReturn(20L);
        ReadingLog myLog = new ReadingLog(myBook, 1, 10, LocalDate.now(), null, null);
        ReadingLog otherLog = new ReadingLog(otherBook, 1, 10, LocalDate.now(), null, null);
        ReflectionTestUtils.setField(myLog, "id", 1L);
        ReflectionTestUtils.setField(otherLog, "id", 2L);
        when(readingLogRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(myLog, otherLog));
        // 10번 참가 신청은 내 것, 20번은 내 것이 아니도록 흉내 낸다
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);
        when(enrollmentRepository.existsByIdAndUserId(20L, 1L)).thenReturn(false);

        ReadingLogSubmitRequest request = new ReadingLogSubmitRequest(List.of(1L, 2L));

        // when: 일괄 제출
        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.submit(1L, request));

        // then: 없는 기록과 같은 404, 내 기록(1번)도 제출되지 않음
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("READING_LOG_NOT_FOUND", e.getCode());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, myLog.getStatus());
        assertNull(myLog.getSubmittedAt());
    }
    // 이미 제출된 기록이 섞이면 409를 던지고, 제출 가능한 기록도 제출하지 않는지 확인 (전체 취소)
    @Test
    @DisplayName("이미 제출된 기록이 섞여 있으면 409이고 아무것도 제출되지 않는다")
    void submit_alreadySubmitted_throws() {
        // given: 둘 다 내 기록, 1번은 DRAFT, 2번은 이미 SUBMITTED
        EnrollmentBook enrollmentBook = mock(EnrollmentBook.class);
        when(enrollmentBook.getEnrollmentId()).thenReturn(10L);
        ReadingLog draftLog = new ReadingLog(enrollmentBook, 1, 10, LocalDate.now(), null, null);
        ReadingLog submittedLog = new ReadingLog(enrollmentBook, 11, 20, LocalDate.now(), null, null);
        ReflectionTestUtils.setField(draftLog, "id", 1L);
        ReflectionTestUtils.setField(submittedLog, "id", 2L);
        // 2번 기록은 이미 제출된 상태로 만든다
        ReflectionTestUtils.setField(submittedLog, "status", ReadingLog.ReadingLogStatus.SUBMITTED);
        when(readingLogRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(draftLog, submittedLog));
        when(enrollmentRepository.existsByIdAndUserId(10L, 1L)).thenReturn(true);

        ReadingLogSubmitRequest request = new ReadingLogSubmitRequest(List.of(1L, 2L));

        // when: 일괄 제출
        ApiException e = assertThrows(ApiException.class,
                () -> readingLogService.submit(1L, request));

        // then: 409 + 코드 확인, DRAFT였던 1번 기록도 제출되지 않음
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("READING_LOG_NOT_SUBMITTABLE", e.getCode());
        assertEquals(ReadingLog.ReadingLogStatus.DRAFT, draftLog.getStatus());
        assertNull(draftLog.getSubmittedAt());
    }
}