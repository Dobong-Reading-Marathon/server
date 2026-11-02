package com.dobong.dobong.book;

import com.dobong.dobong.common.ApiException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;

/**
 * 도서 등록 로직 (F-0301).
 * 책 정보(books)는 한 권당 한 번만 저장하고, 참가자별 정보는 내서재(enrollment_books)에 저장한다.
 */

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookRepository bookRepository;
    private final EnrollmentBookRepository enrollmentBookRepository;

    @Transactional
    public EnrollmentBookResponse register(BookRegisterRequest request) {
        // 1) 책 준비 : 같은 책이 있으면 재사용,없으면 새로 저장
        Book book = null;

        if (request.isbn() != null ) {
            Optional<Book> found = bookRepository.findByIsbn(request.isbn());
            if (found.isPresent()){
                book = found.get();
            }
        }

        if (book == null){
            book = bookRepository.save(new Book(
                    request.isbn(),
                    request.title(),
                    request.author(),
                    request.publisher(),
                    request.publishYear(),
                    request.totalPages()
            ));
        }
        // 2) 중복 확인
        if (enrollmentBookRepository.existsByEnrollmentIdAndBookId(request.enrollmentId(), book.getId())) {
            throw ApiException.conflict("BOOK_ALREADY_REGISTERED", "이미 내서재에 등록한 도서입니다.");
        }
        // 3) 내 서재 저장
        EnrollmentBook saved = enrollmentBookRepository.save(new EnrollmentBook(
                request.enrollmentId(),
                book,
                Boolean.TRUE.equals(request.libraryOwned()),
                request.registrationNo(),
                request.loanDate(),
                request.returnDate()
        ));
        // 4) 응답 돌려주기 : Entity를 그대로 내보내지 않고,응답 상자로 바꿈
        return EnrollmentBookResponse.from(saved);
    }
}