package com.dobong.dobong.book;

import com.dobong.dobong.common.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 예스24 Open API 호출 전용 클래스 (F-0302).
 * 외부 호출만 하고, 외부 오류는 모두 ApiException으로 바꿔서 던진다. 응답을 화면용으로 가공하는 일은 BookSearchService가 한다.
 *
 * 오류는 HTTP 상태만 보지 않고 오류 응답 본문의 errorCode까지 읽어서 바꾼다.
 * - GOODS_002 : BOOK_NOT_FOUND 404 (ISBN 조회만)
 * - SEARCH_001 : 오류가 아니라 빈 목록
 * - RATE_001, RATE_002 : TOO_MANY_REQUESTS 429
 * - 그 밖의 모든 실패(인증, 서버 오류, SEARCH_002, 시간 초과, 네트워크 오류, 키 없음) : EXTERNAL_BOOK_API_UNAVAILABLE 503
 *
 * 키 값은 로그와 예외 메시지에 절대 넣지 않는다. 로그에는 HTTP 상태와 errorCode만 남긴다.
 * SEARCH_002(검색엔진 오류)는 반복 호출 금지라서 재시도하지 않는다.
 */
@Slf4j
@Component
public class Yes24Client {

    static final String UNAVAILABLE_CODE = "EXTERNAL_BOOK_API_UNAVAILABLE";
    static final String UNAVAILABLE_MESSAGE = "도서 정보를 가져오지 못했습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요.";

    private static final String API_KEY_HEADER = "X-Api-Key";

    private final Yes24Properties properties;
    private final RestClient restClient;

    @Autowired
    public Yes24Client(Yes24Properties properties) {
        this(properties, buildRestClient(properties));
    }

    // 테스트에서 MockRestServiceServer를 붙인 RestClient를 넣는 용도
    Yes24Client(Yes24Properties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(Yes24Properties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /** ISBN13으로 책 한 권을 조회한다. 없으면 BOOK_NOT_FOUND(404). */
    public Item itemDetail(String isbn13) {
        Reply reply = call("/v1/goods/itemDetail",
                Map.of("searchType", "ISBN13", "query", isbn13, "detail", "Y"));
        if (reply.ok()) {
            List<Item> items = reply.items();
            if (items.isEmpty()) {
                throw notFound();
            }
            return items.get(0);
        }
        if ("GOODS_002".equals(reply.errorCode())) {
            throw notFound();
        }
        throw toException(reply);
    }

    /** 제목으로 검색한다. 결과가 없으면 빈 목록(오류 아님). */
    public List<Item> itemList(String query, int pageSize) {
        Reply reply = call("/v1/goods/itemList",
                Map.of("query", query, "category", "BOOK", "page", "1",
                        "pageSize", String.valueOf(pageSize), "detail", "Y"));
        if (reply.ok()) {
            return reply.items();
        }
        if ("SEARCH_001".equals(reply.errorCode())) {
            return List.of();
        }
        throw toException(reply);
    }

    private Reply call(String path, Map<String, String> params) {
        if (!properties.hasApiKey()) {
            log.warn("예스24 API 키(YES24_API_KEY)가 설정되지 않아 도서 검색을 건너뜁니다.");
            throw unavailable();
        }
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        // 값은 {변수}로 넘겨서 검색어에 들어 있는 특수문자가 안전하게 인코딩되게 한다
                        params.keySet().forEach(name -> uriBuilder.queryParam(name, "{" + name + "}"));
                        return uriBuilder.build(params);
                    })
                    .header(API_KEY_HEADER, properties.apiKey())
                    // 오류 응답(4xx, 5xx)도 예외로 바꾸지 않고 본문의 errorCode까지 읽는다
                    .exchange((request, response) -> new Reply(
                            response.getStatusCode().value(), readBody(response)));
        } catch (RestClientException e) {
            // 시간 초과, 연결 실패(ResourceAccessException), 응답 해석 실패. 메시지에 주소가 들어갈 수 있어 종류만 남긴다
            log.warn("예스24 API 호출 실패: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static Yes24Response readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(Yes24Response.class);
        } catch (RestClientException e) {
            // 본문이 JSON이 아니면 HTTP 상태만으로 판단한다
            return null;
        }
    }

    private static ApiException toException(Reply reply) {
        String errorCode = reply.errorCode();
        if ("RATE_001".equals(errorCode) || "RATE_002".equals(errorCode) || (errorCode == null && reply.status() == 429)) {
            log.warn("예스24 API 호출 제한: status={} errorCode={}", reply.status(), errorCode);
            return ApiException.tooManyRequests("TOO_MANY_REQUESTS", "요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        // 인증 실패(AUTH_001/002), 서버 오류, SEARCH_002 등. 원인은 서버 로그에만 남기고 사용자에게는 일반 문구만 보낸다
        log.error("예스24 API 오류: status={} errorCode={}", reply.status(), errorCode);
        return unavailable();
    }

    private static ApiException notFound() {
        return ApiException.notFound("BOOK_NOT_FOUND", "해당 도서를 찾을 수 없습니다. 직접 입력해 주세요.");
    }

    private static ApiException unavailable() {
        return ApiException.serviceUnavailable(UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE);
    }

    /** HTTP 상태와 해석한 본문 (본문이 없거나 해석하지 못하면 body는 null) */
    private record Reply(int status, Yes24Response body) {

        boolean ok() {
            return status >= 200 && status < 300 && body != null && body.success();
        }

        String errorCode() {
            return body == null ? null : body.errorCode();
        }

        List<Item> items() {
            if (body.data() == null || body.data().items() == null) {
                return List.of();
            }
            return body.data().items();
        }
    }

    // ---- 예스24 응답 형식 (이 클래스와 BookSearchService 안에서만 쓴다. 화면에는 BookSearchResponse로 바꿔서 보낸다) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Yes24Response(boolean success, String message, String errorCode, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(List<Item> items) {
    }

    /** 예스24 상품 한 건. pages는 detail=Y일 때만 오고 null일 수 있다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String author,
            String publisher,
            String publishDate,
            String isbn13,
            Integer pages,
            String cover,
            String link) {
    }
}
