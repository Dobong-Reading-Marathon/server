package com.dobong.dobong.library;

import com.dobong.dobong.book.Book;
import com.dobong.dobong.book.EnrollmentBook;
import com.dobong.dobong.enrollment.Enrollment;
import com.dobong.dobong.enrollment.EnrollmentRepository;
import com.dobong.dobong.readinglog.ReadingLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibraryServiceTest {

    @Test
    @DisplayName("내서재 응답에 표지 주소가 들어가고, 표지가 없는 책은 null이다")
    void responseIncludesCoverUrl() {
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        LibraryRepository libraryRepository = mock(LibraryRepository.class);
        ReadingLogRepository readingLogRepository = mock(ReadingLogRepository.class);
        LibraryService service = new LibraryService(enrollmentRepository, libraryRepository, readingLogRepository);

        Enrollment enrollment = mock(Enrollment.class);
        when(enrollment.getId()).thenReturn(1L);
        when(enrollmentRepository.findByUserId(7L)).thenReturn(List.of(enrollment));

        EnrollmentBook withCover = mock(EnrollmentBook.class);
        when(withCover.getId()).thenReturn(10L);
        when(withCover.getBook()).thenReturn(
                new Book("9788937460470", "어린왕자", "생텍쥐페리", null, null, 100, false, "https://image.yes24.com/a.jpg"));
        EnrollmentBook withoutCover = mock(EnrollmentBook.class);
        when(withoutCover.getId()).thenReturn(11L);
        when(withoutCover.getBook()).thenReturn(
                new Book(null, "모모", "미하엘 엔데", null, null, 336, false, null));
        when(libraryRepository.findByEnrollmentIdInOrderByIdDesc(any())).thenReturn(List.of(withCover, withoutCover));
        when(readingLogRepository.findByEnrollmentBookIdIn(any())).thenReturn(List.of());

        List<LibraryItemResponse> result = service.getMyLibrary(7L, null, null);

        assertThat(result).extracting(LibraryItemResponse::coverUrl)
                .containsExactly("https://image.yes24.com/a.jpg", null);
    }
}
