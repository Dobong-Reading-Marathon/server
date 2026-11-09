package com.dobong.dobong.book;

import com.dobong.dobong.book.dto.BookSearchResponse;
import com.dobong.dobong.common.ApiException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 도서 정보 자동 입력 (F-0302).
 * 예스24에서 조회한 결과를 F-0301 등록 화면에 채울 형태로 바꿔서 돌려준다.
 * 조회 전용이라 DB에 저장하지 않는다. 저장은 사용자가 확인한 뒤 POST /api/books(F-0301)가 한다.
 *
 * 외부 호출은 DB 연결을 붙잡지 않도록 @Transactional을 붙이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BookSearchService {

    static final int MAX_RESULTS = 10;
    static final int MAX_QUERY_LENGTH = 100;

    private static final Pattern ISBN13 = Pattern.compile("^\\d{13}$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})");

    private final Yes24Client yes24Client;

    /** 제목 검색어로 찾는다. 최대 10권. */
    public BookSearchResponse.Items search(String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            throw ApiException.badRequest("INVALID_INPUT", "검색어를 입력해 주세요.");
        }
        if (keyword.length() > MAX_QUERY_LENGTH) {
            throw ApiException.badRequest("INVALID_INPUT", "검색어는 " + MAX_QUERY_LENGTH + "자 이내로 입력해 주세요.");
        }
        List<BookSearchResponse> items = yes24Client.itemList(keyword, MAX_RESULTS).stream()
                .limit(MAX_RESULTS)
                .map(item -> toResponse(item, null))
                .toList();
        return new BookSearchResponse.Items(items);
    }

    /** ISBN13으로 한 권을 찾는다. 하이픈과 공백은 지우고 13자리 숫자만 받는다 (ISBN-10은 변환하지 않고 거부). */
    public BookSearchResponse lookup(String isbn) {
        String digits = isbn == null ? "" : isbn.replaceAll("[-\\s]", "");
        if (!ISBN13.matcher(digits).matches()) {
            throw ApiException.badRequest("INVALID_INPUT", "ISBN은 하이픈 없이 13자리 숫자입니다.");
        }
        return toResponse(yes24Client.itemDetail(digits), digits);
    }

    // 예스24 응답을 화면용으로 바꾼다. 빈 문자열은 null로 통일한다.
    // fallbackIsbn: 응답에 ISBN이 없거나 형식이 다를 때 대신 쓸 값 (ISBN 조회에서만 넘김)
    private static BookSearchResponse toResponse(Yes24Client.Item item, String fallbackIsbn) {
        String isbn = blankToNull(item.isbn13());
        if (isbn == null || !ISBN13.matcher(isbn).matches()) {
            isbn = fallbackIsbn;
        }
        return new BookSearchResponse(
                blankToNull(item.title()),
                blankToNull(item.author()),
                blankToNull(item.publisher()),
                extractYear(item.publishDate()),
                // 0 이하인 쪽수는 의미가 없어서 비워 둔다 (F-0301은 1 이상만 받는다)
                item.pages() != null && item.pages() > 0 ? item.pages() : null,
                isbn,
                blankToNull(item.cover()),
                safeLink(item.link())
        );
    }

    // "yyyy-MM-dd"에서 연도만. 비어 있거나 형식이 다르면 예외 없이 null
    private static Integer extractYear(String publishDate) {
        if (publishDate == null) {
            return null;
        }
        Matcher m = YEAR.matcher(publishDate.trim());
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    // 외부 응답의 링크는 https일 때만 화면에 내린다 (javascript: 같은 주소가 링크로 쓰이는 것을 막음)
    private static String safeLink(String link) {
        String value = blankToNull(link);
        return value != null && value.regionMatches(true, 0, "https://", 0, 8) ? value : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
