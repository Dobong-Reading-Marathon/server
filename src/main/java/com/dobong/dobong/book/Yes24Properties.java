package com.dobong.dobong.book;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예스24 Open API 설정 (application.yml 의 app.yes24, F-0302)
 *
 * apiKey는 비어 있어도 서버가 뜬다. 이때 도서 검색 API만 503으로 응답한다.
 * 키 값은 로그나 예외 메시지에 넣지 않는다. record 기본 toString에 키가 찍히지 않도록 직접 막아 둔다.
 *
 * @param baseUrl        예스24 API 주소
 * @param apiKey         X-Api-Key 헤더에 보낼 키 (환경변수 YES24_API_KEY)
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout    응답 제한 시간
 */
@ConfigurationProperties(prefix = "app.yes24")
public record Yes24Properties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout) {

    public Yes24Properties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://apis.yes24.com";
        }
        if (!positive(connectTimeout) || !positive(readTimeout)) {
            throw new IllegalStateException("app.yes24.connect-timeout, read-timeout 을 0보다 크게 설정하세요.");
        }
    }

    boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static boolean positive(Duration d) {
        return d != null && !d.isZero() && !d.isNegative();
    }

    @Override
    public String toString() {
        return "Yes24Properties[baseUrl=" + baseUrl + ", apiKey=" + (hasApiKey() ? "****" : "(없음)") + "]";
    }
}
