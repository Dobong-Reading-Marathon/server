package com.dobong.dobong.readinglog;

import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.book.EnrollmentBookRepository;
import com.dobong.dobong.common.ApiException;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.dto.ReadingLogRequest;
import com.dobong.dobong.readinglog.dto.ReadingLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 독서기록 작성 로직 (F-0401).
 * 내 도서인지 확인하고 쪽 범위를 검증한 뒤 저장한다.
 */

@Service
@RequiredArgsConstructor
public class ReadingLogService {
    private final ReadingLogRepository readingLogRepository;
    private final EnrollmentBookRepository enrollmentBookRepository;
    private final EnrollmentRepository enrollmentRepository;

    // 독서기록 작성 (userId: 로그인한 사용자, request: 기록 내용)
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
}