package com.dobong.dobong.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서버를 호출해도 되는 프론트 주소 목록 (application.yml 의 app.cors.allowed-origins).
 *
 * 화면(Cloudflare Pages)과 서버(Cloud Run)는 주소가 달라서, 이 목록에 화면 주소가 없으면 브라우저가 요청을 막는다.
 * 목록이 비어 있으면 다른 주소에서 오는 브라우저 요청은 모두 막힌다 (안전한 기본값).
 *
 * 와일드카드(*)는 쓸 수 없다. 쓰면 아무 사이트(피싱 사이트 포함)나 사용자 브라우저를 통해 우리 API를 부를 수 있기 때문.
 *
 * @param allowedOrigins 예: https://dobong-marathon.pages.dev (끝에 / 없이, 프로토콜 포함)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
				.map(String::strip)
				.filter(o -> !o.isEmpty())
				.toList();
		if (allowedOrigins.stream().anyMatch(o -> o.contains("*"))) {
			throw new IllegalStateException("app.cors.allowed-origins 에 와일드카드(*)를 쓸 수 없습니다. 프론트 주소를 정확히 적으세요.");
		}
	}
}
