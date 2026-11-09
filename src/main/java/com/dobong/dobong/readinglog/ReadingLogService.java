package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.book.EnrollmentBookRepository;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * 독서기록 로직 (F-0401, F-0402).
 * 내 도서인지 확인하고 쪽 범위를 검증한 뒤 저장한다.
 * 서평은 내 기록이고 수정 가능한 상태일 때만 바꾼다.
 */

@Service
@RequiredArgsConstructor
public class ReadingLogService {
    private final ReadingLogRepository readingLogRepository;
    private final EnrollmentBookRepository enrollmentBookRepository;
    private final EnrollmentRepository enrollmentRepository;
    // 내 기록 조회 ( 없는 기록과 남의 기록 -404)
    private ReadingLog getMyLog(Long userId,Long readingLogId){
        // 1) 기록 조회
        ReadingLog readingLog = readingLogRepository.findById(readingLogId)
                .orElseThrow(()->ApiException.notFound("READING_LOG_NOT_FOUND","독서기록을 찾을 수 없습니다."));
        // 2) 내 기록인지 확인
        if (!enrollmentRepository.existsByIdAndUserId(readingLog.getEnrollmentBook().getEnrollmentId(), userId)) {
            throw ApiException.notFound("READING_LOG_NOT_FOUND", "독서기록을 찾을 수 없습니다.");
        }
        // 3) 기록 돌려주기
        return readingLog;
    }

    // 독서기록 작성 (F-0401)
    @Transactional
    public ReadingLogResponse create(Long userId, ReadingLogRequest request){

        // 1) 도서 조회
        EnrollmentBook enrollmentBook = enrollmentBookRepository.findById(request.enrollmentBookId())
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_BOOK_NOT_FOUND", "내서재에 없는 도서입니다."));

        // 2) 내 도서인지 확인 (없는 도서와 남의 도서는 같은 404로 응답)
        if (!enrollmentRepository.existsByIdAndUserId(enrollmentBook.getEnrollmentId(), userId)) {
            throw ApiException.notFound("ENROLLMENT_BOOK_NOT_FOUND", "내서재에 없는 도서입니다.");
        }

        // 3) 쪽 범위 검증
        if (request.endPage() < request.startPage()){
            throw ApiException.badRequest("INVALID_PAGE_RANGE","끝 쪽수는 시작 쪽수보다 작을 수 없습니다.");
        }
        if (request.endPage() > enrollmentBook.getBook().getTotalPages()) {
            throw ApiException.badRequest("PAGE_EXCEEDS_TOTAL", "끝 쪽이 전체 쪽수를 넘을 수 없습니다.");
        }

        // 4) 저장
        ReadingLog saved = readingLogRepository.save(new ReadingLog(
                enrollmentBook,
                request.startPage(),
                request.endPage(),
                request.readDate(),
                request.quote(),
                request.review()
        ));

        // 5) 응답 돌려주기
        return ReadingLogResponse.from(saved);
    }

    // 서평 작성·수정 (F-0402)
    @Transactional
    public ReviewUpdateResponse updateReview(Long userId, Long readingLogId, ReviewUpdateRequest request){

        // 1) 기록 조회
        ReadingLog readingLog = readingLogRepository.findById(readingLogId)
                .orElseThrow(() -> ApiException.notFound("READING_LOG_NOT_FOUND", "독서기록을 찾을 수 없습니다."));

        // 2) 내 기록인지 확인 (없는 기록과 남의 기록은 같은 404로 응답)
        if (!enrollmentRepository.existsByIdAndUserId(readingLog.getEnrollmentBook().getEnrollmentId(), userId)) {
            throw ApiException.notFound("READING_LOG_NOT_FOUND", "독서기록을 찾을 수 없습니다.");
        }

        // 3) 수정할 수 있는 상태인지 확인
        if (!readingLog.isEditable()) {
            throw ApiException.conflict("READING_LOG_NOT_EDITABLE", "현재 상태에서는 서평을 수정할 수 없습니다.");
        }

        // 4) 서평 수정 (변경 감지로 저장됨)
        readingLog.updateReview(request.review());

        // 5) 응답 돌려주기
        return ReviewUpdateResponse.from(readingLog);
    }

    // 독서기록 일괄 제출 (F-0405)
    @Transactional
    public ReadingLogSubmitResponse submit(Long userId, ReadingLogSubmitRequest request){
        // 1) 기록 한꺼번에 조회
        List<ReadingLog> readingLogs = readingLogRepository.findAllById(request.readingLogIds());
        // 2) 없는 기록이 있는지 확인 (중복 ID는 한 번만 센다)
        if (readingLogs.size() != new HashSet<>(request.readingLogIds()).size()) {
            throw ApiException.notFound("READING_LOG_NOT_FOUND", "독서기록을 찾을 수 없습니다.");
        }
        // 3) 내 기록인지 확인 (없는 기록과 남의 기록은 같은 404로 응답)
        for (ReadingLog readingLog : readingLogs) {
            if (!enrollmentRepository.existsByIdAndUserId(readingLog.getEnrollmentBook().getEnrollmentId(), userId)) {
                throw ApiException.notFound("READING_LOG_NOT_FOUND", "독서기록을 찾을 수 없습니다.");
            }
        }
        // 4) 제출 할 수 있는 상태인지 확인
        for (ReadingLog readingLog : readingLogs){
            if( ! readingLog.isSubmittable()){
                throw ApiException.conflict("READING_LOG_NOT_SUBMITTABLE","제출할 수 없는 상태의 기록이 있습니다");
            }
        }
        // 5) 제출 처리하고 응답
        // 제출 시각은 한 번만 만든다 (모든 기록에 같은 시각)
        OffsetDateTime now = OffsetDateTime.now();

        for (ReadingLog readingLog : readingLogs) {
            readingLog.submit(now);
        }

        // 심사로 간 기록과 저장으로 끝난 기록을 나눠서 알려 준다
        List<Long> submittedIds = readingLogs.stream()
                .filter(readingLog -> readingLog.getStatus() == ReadingLog.ReadingLogStatus.SUBMITTED)
                .map(ReadingLog::getId)
                .toList();
        List<Long> savedIds = readingLogs.stream()
                .filter(readingLog -> readingLog.getStatus() == ReadingLog.ReadingLogStatus.SAVED)
                .map(ReadingLog::getId)
                .toList();

        return new ReadingLogSubmitResponse(submittedIds, savedIds);

    }
    // 독서 기록 수정 ( F-0406 )
    @Transactional
    public ReadingLogResponse update(Long userId,Long readingLogId,ReadingLogUpdateRequest request){
        // 1) 내 기록 조회
        ReadingLog readingLog = getMyLog(userId,readingLogId);
        // 2) 수정할 수 있는 상태인지 확인
        if (!readingLog.isEditable()){
            throw ApiException.conflict("READING_LOG_NOT_EDITABLE", "현재 상태에서는 기록을 수정할 수 없습니다.");
        }
        // 3) 최종 값 계산 ( 보낸 값이 없으면 기존 값 )
        Integer startPage = request.startPage() != null ? request.startPage() : readingLog.getStartPage();
        Integer endPage = request.endPage() != null ? request.endPage() : readingLog.getEndPage();
        LocalDate readDate = request.readDate() != null ? request.readDate() : readingLog.getReadDate();
        String quote = request.quote() != null ? request.quote() : readingLog.getQuote();
        String review = request.review() != null ? request.review() : readingLog.getReview();
        // 4) 쪽 범위 검증
        if (endPage<startPage){
            throw ApiException.badRequest("INVALID_PAGE_RANGE","끝 쪽수는 시작 쪽수보다 작을 수 없습니다.");
        }
        if (endPage>readingLog.getEnrollmentBook().getBook().getTotalPages()){
            throw ApiException.badRequest("PAGE_EXCEEDS_TOTAL", "끝 쪽이 전체 쪽수를 넘을 수 없습니다.");
        }
        // 5) 기록 수정 ( 변경 감지로 저장됨 )
        readingLog.updateLog(startPage,endPage,readDate,quote,review);
        // 6) 응답 돌려주기
        return ReadingLogResponse.from(readingLog);
    }

    // 독서 기록 삭제 (F-0406)
    @Transactional
    public void delete(Long userId, Long readingLogId){
        // 1) 내 기록 조회
        ReadingLog readingLog = getMyLog(userId, readingLogId);
        // 2) 삭제할 수 있는 상태인지 확인 (제출 전 기록만)
        if (!readingLog.isDeletable()){
            throw ApiException.conflict("READING_LOG_NOT_DELETABLE", "제출한 기록은 삭제할 수 없습니다.");
        }
        // 3) 기록 삭제
        readingLogRepository.delete(readingLog);
    }

    // 독서기록 상세 조회 (F-0407)
    @Transactional(readOnly = true)
    public ReadingLogDetailResponse getReadingLog(Long userId, Long readingLogId) {
        // 1) 내 기록 조회
        ReadingLog readingLog = getMyLog(userId, readingLogId);

        // 2) 응답 돌려주기
        return ReadingLogDetailResponse.from(readingLog);
    }


}