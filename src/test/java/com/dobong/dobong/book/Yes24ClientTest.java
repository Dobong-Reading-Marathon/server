package com.dobong.dobong.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dobong.dobong.common.ApiException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 예스24 호출 클래스 테스트 (F-0302). 실제 예스24는 호출하지 않고 MockRestServiceServer로 응답을 흉내 낸다.
 */
class Yes24ClientTest {

    private static final String KEY = "test-secret-key-12345";
    private static final String BASE = "https://apis.yes24.com";

    private MockRestServiceServer server;
    private Yes24Client client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new Yes24Client(properties(KEY), builder.build());
    }

    private static Yes24Properties properties(String apiKey) {
        return new Yes24Properties(BASE, apiKey, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    private static String error(String errorCode) {
        return "{\"success\":false,\"message\":\"오류\",\"errorCode\":\"" + errorCode + "\",\"data\":null}";
    }

    private static String detailBody(String pagesJson) {
        return "{\"success\":true,\"message\":\"OK\",\"errorCode\":null,\"data\":{\"items\":[{"
                + "\"title\":\"토지\",\"author\":\"박경리\",\"publisher\":\"마로니에북스\","
                + "\"publishDate\":\"2012-05-15\",\"isbn13\":\"9788937460470\",\"pages\":" + pagesJson + ","
                + "\"cover\":\"https://image.yes24.com/a.jpg\",\"link\":\"https://www.yes24.com/p/1\","
                + "\"unknownField\":\"무시\"}],\"currentPage\":1,\"pageSize\":1,\"totalCount\":1}}";
    }

    // ---- 정상 ----

    @Test
    @DisplayName("ISBN 조회: 키 헤더와 파라미터를 보내고 응답을 읽는다")
    void itemDetailSuccess() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemDetail")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", KEY))
                .andExpect(queryParam("searchType", "ISBN13"))
                .andExpect(queryParam("query", "9788937460470"))
                .andExpect(queryParam("detail", "Y"))
                .andRespond(withSuccess(detailBody("1000"), MediaType.APPLICATION_JSON));

        Yes24Client.Item item = client.itemDetail("9788937460470");

        assertThat(item.title()).isEqualTo("토지");
        assertThat(item.author()).isEqualTo("박경리");
        assertThat(item.publishDate()).isEqualTo("2012-05-15");
        assertThat(item.pages()).isEqualTo(1000);
        assertThat(item.link()).isEqualTo("https://www.yes24.com/p/1");
        server.verify();
    }

    @Test
    @DisplayName("제목 검색: 한글 검색어와 pageSize를 보내고 목록을 읽는다")
    void itemListSuccess() {
        String body = "{\"success\":true,\"errorCode\":null,\"data\":{\"items\":["
                + "{\"title\":\"토지 1\",\"isbn13\":\"9788937460470\"},{\"title\":\"토지 2\",\"isbn13\":\"9788937460471\"}]}}";
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andExpect(header("X-Api-Key", KEY))
                // 한글, 공백, &가 주소에 안전하게 인코딩되어 나가는지 확인 (매처는 인코딩된 값과 비교한다)
                .andExpect(queryParam("query", "%ED%86%A0%EC%A7%80%20%26%20%EB%B0%95%EA%B2%BD%EB%A6%AC"))
                .andExpect(queryParam("category", "BOOK"))
                .andExpect(queryParam("pageSize", "10"))
                .andExpect(queryParam("detail", "Y"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<Yes24Client.Item> items = client.itemList("토지 & 박경리", 10);

        assertThat(items).extracting(Yes24Client.Item::title).containsExactly("토지 1", "토지 2");
        server.verify();
    }

    @Test
    @DisplayName("pages가 null이어도 정상으로 읽는다")
    void pagesNull() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemDetail")))
                .andRespond(withSuccess(detailBody("null"), MediaType.APPLICATION_JSON));

        assertThat(client.itemDetail("9788937460470").pages()).isNull();
    }

    // ---- 404 ----

    @Test
    @DisplayName("GOODS_002는 ISBN 조회에서 BOOK_NOT_FOUND 404")
    void goodsNotFound() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemDetail")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("GOODS_002")));

        assertThatThrownBy(() -> client.itemDetail("9788937460470"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCode()).isEqualTo("BOOK_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("SEARCH_001은 오류가 아니라 빈 목록")
    void searchNoResult() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("SEARCH_001")));

        assertThat(client.itemList("없는책", 10)).isEmpty();
    }

    @Test
    @DisplayName("SEARCH_002(검색엔진 오류)는 503이고 다시 호출하지 않는다")
    void searchEngineError() {
        // 한 번만 응답하도록 설정했으므로, 재시도하면 두 번째 호출에서 테스트가 실패한다
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("SEARCH_002")));

        assertUnavailable(() -> client.itemList("토지", 10));
        server.verify();
    }

    // ---- 오류 응답의 errorCode 변환 ----

    @Test
    @DisplayName("AUTH_001(401)은 503으로 바뀌고, 키 값은 응답 메시지에 나오지 않는다")
    void authError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemDetail")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error("AUTH_001")));

        // assertUnavailable이 응답 메시지에 키가 없는지도 확인한다
        assertUnavailable(() -> client.itemDetail("9788937460470"));
    }

    @Test
    @DisplayName("RATE_001, RATE_002(429)는 TOO_MANY_REQUESTS 429")
    void rateLimited() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body(error("RATE_001")));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body(error("RATE_002")));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.itemList("토지", 10))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(e.getCode()).isEqualTo("TOO_MANY_REQUESTS");
                    });
        }
        server.verify();
    }

    @Test
    @DisplayName("UPSTREAM_UNAVAILABLE(503), INTERNAL_ERROR(500)는 503")
    void upstreamErrors() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(error("UPSTREAM_UNAVAILABLE")));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(error("INTERNAL_ERROR")));

        assertUnavailable(() -> client.itemList("토지", 10));
        assertUnavailable(() -> client.itemList("토지", 10));
        server.verify();
    }

    @Test
    @DisplayName("JSON이 아닌 5xx 응답이나 깨진 본문도 503")
    void nonJsonError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>Bad Gateway</html>"));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withSuccess("{broken", MediaType.APPLICATION_JSON));

        assertUnavailable(() -> client.itemList("토지", 10));
        assertUnavailable(() -> client.itemList("토지", 10));
        server.verify();
    }

    // ---- 시간 초과, 네트워크 오류 ----

    @Test
    @DisplayName("시간 초과(ResourceAccessException)는 503으로 바뀐다")
    void timeout() {
        // 실제로 기다리지 않고 예외만 발생시킨다. RestClient가 IOException을 ResourceAccessException으로 감싼다
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemDetail")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/goods/itemList")))
                .andRespond(withException(new IOException("Connection refused")));

        assertUnavailable(() -> client.itemDetail("9788937460470"));
        assertUnavailable(() -> client.itemList("토지", 10));
        server.verify();
    }

    // ---- 키 없음 ----

    @Test
    @DisplayName("키가 비어 있으면 예스24를 호출하지 않고 503")
    void noApiKey() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer noCalls = MockRestServiceServer.bindTo(builder).build();
        Yes24Client noKeyClient = new Yes24Client(properties(" "), builder.build());

        assertUnavailable(() -> noKeyClient.itemDetail("9788937460470"));
        assertUnavailable(() -> noKeyClient.itemList("토지", 10));
        noCalls.verify(); // 기대한 요청이 없으므로, 호출이 있었다면 여기서 실패한다
    }

    private static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(e.getCode()).isEqualTo("EXTERNAL_BOOK_API_UNAVAILABLE");
            assertThat(e.getMessage()).doesNotContain(KEY);
        });
    }
}
