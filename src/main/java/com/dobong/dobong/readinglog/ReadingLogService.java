package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.book.EnrollmentBookRepository;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.ReadingLogRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogResponse;
import com.dobong.dobong.readinglog.dto.ReviewUpdateRequest;
import com.dobong.dobong.readinglog.dto.ReviewUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}