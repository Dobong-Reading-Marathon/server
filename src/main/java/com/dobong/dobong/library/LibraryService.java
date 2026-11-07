package com.dobong.dobong.library;

import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.enrollment.Enrollment;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.progress.PageRange;
import com.dobong.dobong.progress.PageRanges;
import com.dobong.dobong.readinglog.ReadingLog;
import com.dobong.dobong.readinglog.ReadingLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 내서재 서비스 (F-0503) */
@Service
@RequiredArgsConstructor
public class LibraryService {

    private final EnrollmentRepository enrollmentRepository;
    private final LibraryRepository libraryRepository;
    private final ReadingLogRepository readingLogRepository;

    // 내 서재 도서 목록 조회
    @Transactional(readOnly = true)
    public List<LibraryItemResponse> getMyLibrary(Long userId, String keyword, LibraryStatus status) {
        // 1) 내 신청 번호들
        List<Long> enrollmentIds = enrollmentRepository.findByUserId(userId).stream()
                .map(Enrollment::getId)
                .toList();
        if (enrollmentIds.isEmpty()) {
            return List.of();
        }

        // 2) 내 서재 책들
        List<EnrollmentBook> books =
                libraryRepository.findByEnrollmentIdInOrderByIdDesc(enrollmentIds);
        if (books.isEmpty()) {
            return List.of();
        }

        // 3) 책들의 기록을 책 번호별로 묶기
        List<Long> bookIds = books.stream().map(EnrollmentBook::getId).toList();
        Map<Long, List<ReadingLog>> logsByBook = readingLogRepository.findByEnrollmentBookIdIn(bookIds)
                .stream()
                .collect(Collectors.groupingBy(log -> log.getEnrollmentBook().getId()));

        // 추가: 검색어 정리 (없으면 null, 있으면 앞뒤 공백 제거 + 소문자)
        String word = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();

        // 4) 책마다 읽은 쪽수·완독·상태 계산해서 응답으로 변환 후 검색·상태 필터
        return books.stream()
                .map(eb -> {
                    int totalPages = eb.getBook().getTotalPages();
                    List<ReadingLog> logs = logsByBook.getOrDefault(eb.getId(), List.of());
                    List<PageRange> ranges = logs.stream()
                            .filter(log -> log.getEndPage() <= totalPages)
                            .map(log -> new PageRange(log.getStartPage(), log.getEndPage()))
                            .toList();
                    int readPages = PageRanges.countReadPages(ranges);
                    return new LibraryItemResponse(
                            eb.getId(),
                            eb.getBook().getTitle(),
                            eb.getBook().getAuthor(),
                            totalPages,
                            readPages,
                            readPages == totalPages,   // 완독
                            eb.isLibraryOwned(),
                            eb.getBook().isChildren(),
                            resolveStatus(logs)        // 대표 상태
                    );
                })
                // 추가: 검색 필터 (제목 또는 저자에 검색어 포함)
                .filter(item -> word == null
                        || item.title().toLowerCase().contains(word)
                        || item.author().toLowerCase().contains(word))
                // 추가: 상태 필터
                .filter(item -> status == null || item.status() == status)
                .toList();
    }

    // 책 한 권의 대표 상태 (기록이 여러 개일 때 하나로 정하는 규칙)
    private LibraryStatus resolveStatus(List<ReadingLog> logs) {
        // 기록이 하나도 없으면 "기록 전"
        if (logs.isEmpty()) {
            return LibraryStatus.NO_RECORD;
        }
        // 보완요청 기록이 하나라도 있으면 "보완요청" (가장 먼저 조치가 필요)
        if (logs.stream().anyMatch(log -> log.getStatus() == ReadingLog.ReadingLogStatus.REVISION_REQUESTED)) {
            return LibraryStatus.REVISION_REQUESTED;
        }
        // 임시저장 기록이 하나라도 있으면 "임시저장" (제출해야 함)
        if (logs.stream().anyMatch(log -> log.getStatus() == ReadingLog.ReadingLogStatus.DRAFT)) {
            return LibraryStatus.DRAFT;
        }
        // 심사 대기 기록이 하나라도 있으면 "심사 대기" (사서 확인 중)
        if (logs.stream().anyMatch(log -> log.getStatus() == ReadingLog.ReadingLogStatus.SUBMITTED)) {
            return LibraryStatus.SUBMITTED;
        }
        // 위 세 가지가 없으면 모두 승인된 기록
        return LibraryStatus.APPROVED;
    }
}