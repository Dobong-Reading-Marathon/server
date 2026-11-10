package com.dobong.dobong.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dobong.dobong.book.dto.BookSearchResponse;
import com.dobong.dobong.common.ApiException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 도서 정보 자동 입력 변환 로직 테스트 (F-0302). 예스24 호출은 mock으로 대신한다.
 */
class BookSearchServiceTest {

    private Yes24Client yes24Client;
    private BookSearchService service;

    @BeforeEach
    void setUp() {
        yes24Client = mock(Yes24Client.class);
        service = new BookSearchService(yes24Client);
    }

    private static Yes24Client.Item item(String publishDate, Integer pages, String link) {
        return new Yes24Client.Item("토지", "박경리", "마로니에북스", publishDate, "9788937460470",
                pages, "https://image.yes24.com/a.jpg", link);
    }

    private static void assertInvalidInput(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getCode()).isEqualTo("INVALID_INPUT");
        });
    }

    // ---- 변환 ----

    @Test
    @DisplayName("ISBN 조회 결과를 등록 요청과 같은 이름의 필드로 바꾼다 (publishYear는 연도만)")
    void lookupMapsFields() {
        when(yes24Client.itemDetail("9788937460470"))
                .thenReturn(item("2012-05-15", 1000, "https://www.yes24.com/p/1"));

        BookSearchResponse r = service.lookup("9788937460470");

        assertThat(r.title()).isEqualTo("토지");
        assertThat(r.author()).isEqualTo("박경리");
        assertThat(r.publisher()).isEqualTo("마로니에북스");
        assertThat(r.publishYear()).isEqualTo(2012);
        assertThat(r.totalPages()).isEqualTo(1000);
        assertThat(r.isbn()).isEqualTo("9788937460470");
        assertThat(r.coverUrl()).isEqualTo("https://image.yes24.com/a.jpg");
        assertThat(r.sourceLink()).isEqualTo("https://www.yes24.com/p/1");
    }

    @Test
    @DisplayName("쪽수가 null이거나 0 이하이면 null")
    void pagesNullOrNonPositive() {
        when(yes24Client.itemDetail("9788937460470")).thenReturn(item("2012-05-15", null, null));
        assertThat(service.lookup("9788937460470").totalPages()).isNull();

        when(yes24Client.itemDetail("9788937460470")).thenReturn(item("2012-05-15", 0, null));
        assertThat(service.lookup("9788937460470").totalPages()).isNull();
    }

    @Test
    @DisplayName("출판일이 없거나 형식이 달라도 예외 없이 publishYear는 null")
    void publishYearParseFailure() {
        for (String date : new String[] {null, "", "  ", "미정", "May 2012"}) {
            when(yes24Client.itemDetail("9788937460470")).thenReturn(item(date, 100, null));
            assertThat(service.lookup("9788937460470").publishYear()).as("publishDate=%s", date).isNull();
        }
    }

    @Test
    @DisplayName("sourceLink는 https일 때만 내려주고, 그 밖의 주소나 빈 값은 null")
    void sourceLinkOnlyHttps() {
        for (String link : new String[] {"http://www.yes24.com/p/1", "javascript:alert(1)", "", null}) {
            when(yes24Client.itemDetail("9788937460470")).thenReturn(item("2012-05-15", 100, link));
            assertThat(service.lookup("9788937460470").sourceLink()).as("link=%s", link).isNull();
        }
    }

    @Test
    @DisplayName("빈 문자열은 null로 바꾸고, 응답에 ISBN이 없으면 조회한 ISBN을 쓴다")
    void blankToNullAndIsbnFallback() {
        when(yes24Client.itemDetail("9788937460470"))
                .thenReturn(new Yes24Client.Item("토지", "", " ", "2012-05-15", "", 100, "", "https://www.yes24.com/p/1"));

        BookSearchResponse r = service.lookup("9788937460470");

        assertThat(r.author()).isNull();
        assertThat(r.publisher()).isNull();
        assertThat(r.coverUrl()).isNull();
        assertThat(r.isbn()).isEqualTo("9788937460470");
    }

    // ---- 검색 ----

    @Test
    @DisplayName("검색은 앞뒤 공백을 지우고 pageSize 10으로 호출한다")
    void searchTrimsQuery() {
        when(yes24Client.itemList("토지", 10)).thenReturn(List.of(item("2012-05-15", 1000, null)));

        BookSearchResponse.Items result = service.search("  토지  ");

        assertThat(result.items()).hasSize(1);
        verify(yes24Client).itemList("토지", 10);
    }

    @Test
    @DisplayName("검색 결과는 최대 10권만 돌려준다")
    void searchLimitsToTen() {
        List<Yes24Client.Item> twelve = IntStream.range(0, 12).mapToObj(i -> item("2012-05-15", 100, null)).toList();
        when(yes24Client.itemList("토지", 10)).thenReturn(twelve);

        assertThat(service.search("토지").items()).hasSize(10);
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 배열 (오류 아님)")
    void searchEmpty() {
        when(yes24Client.itemList("없는책", 10)).thenReturn(List.of());

        assertThat(service.search("없는책").items()).isEmpty();
    }

    @Test
    @DisplayName("검색 결과의 ISBN이 없으면 null (조회한 값이 없으므로 채우지 않는다)")
    void searchIsbnNull() {
        when(yes24Client.itemList("토지", 10))
                .thenReturn(List.of(new Yes24Client.Item("토지", "박경리", null, null, null, null, null, null)));

        assertThat(service.search("토지").items().get(0).isbn()).isNull();
    }

    // ---- 입력 검증 ----

    @Test
    @DisplayName("검색어가 없거나 공백이거나 100자를 넘으면 INVALID_INPUT 400이고 예스24를 호출하지 않는다")
    void searchInvalidQuery() {
        assertInvalidInput(() -> service.search(null));
        assertInvalidInput(() -> service.search(""));
        assertInvalidInput(() -> service.search("   "));
        assertInvalidInput(() -> service.search("가".repeat(101)));
        verifyNoInteractions(yes24Client);
    }

    @Test
    @DisplayName("ISBN의 하이픈과 공백은 지우고 조회한다")
    void lookupStripsHyphenAndSpace() {
        when(yes24Client.itemDetail("9788937460470")).thenReturn(item("2012-05-15", 100, null));

        service.lookup(" 978-89-374-6047-0 ");

        verify(yes24Client).itemDetail("9788937460470");
    }

    @Test
    @DisplayName("13자리 숫자가 아닌 ISBN(ISBN-10 포함)은 INVALID_INPUT 400이고 예스24를 호출하지 않는다")
    void lookupInvalidIsbn() {
        assertInvalidInput(() -> service.lookup(null));
        assertInvalidInput(() -> service.lookup(""));
        assertInvalidInput(() -> service.lookup("8937460475")); // ISBN-10은 변환하지 않고 거부
        assertInvalidInput(() -> service.lookup("978893746047X"));
        assertInvalidInput(() -> service.lookup("97889374604700")); // 14자리
        verifyNoInteractions(yes24Client);
    }
}
