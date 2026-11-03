package com.dobong.dobong.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * "Authorization: Bearer {토큰}" 헤더를 읽어 로그인한 참가자를 확인
 *
 * 토큰이 없거나 틀리거나 만료됐으면 로그인 안 한 상태로 다음 단계로 넘김.
 * 로그인이 필요한 주소면 SecurityConfig 규칙이 401로 응답.
 *
 * @Component를 붙이지 않음: 붙이면 Spring Boot가 일반 필터로도 등록해서 두 번 실행됨.
 * SecurityConfig에서 직접 만들어 보안 필터 체인에만 넣음.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";
	private static final List<SimpleGrantedAuthority> USER_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final SessionService sessionService;

	public BearerTokenAuthenticationFilter(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			String token = header.substring(PREFIX.length()).trim();
			sessionService.authenticate(token).ifPresent(principal -> {
				var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, USER_AUTHORITIES);
				SecurityContext context = SecurityContextHolder.createEmptyContext();
				context.setAuthentication(authentication);
				SecurityContextHolder.setContext(context);
			});
		}
		chain.doFilter(request, response);
	}
}
