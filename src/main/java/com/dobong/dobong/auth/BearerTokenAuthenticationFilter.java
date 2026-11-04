package com.dobong.dobong.auth;

import com.dobong.dobong.admin.AdminSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * "Authorization: Bearer {토큰}" 헤더를 읽어 로그인한 참가자 또는 관리자를 확인
 *
 * 주소에 따라 세션 테이블 하나만 조회 (F-0104)
 * - /api/admin/ 주소: 관리자 세션(admin_sessions)만 → ROLE_ADMIN
 * - 그 밖: 참가자 세션(auth_sessions)만 → ROLE_USER
 * 그래서 참가자 토큰을 관리자 주소에, 관리자 토큰을 참가자 주소에 보내면 찾지 못해 로그인 안 한 상태가 됨.
 *
 * 토큰이 없거나 틀리거나 만료됐으면 로그인 안 한 상태로 다음 단계로 넘김.
 * 로그인이 필요한 주소면 SecurityConfig 규칙이 401로 응답.
 *
 * @Component를 붙이지 않음: 붙이면 Spring Boot가 일반 필터로도 등록해서 두 번 실행됨.
 * SecurityConfig에서 직접 만들어 보안 필터 체인에만 넣음.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	private static final String PREFIX = "Bearer ";
	private static final String ADMIN_PATH = "/api/admin";
	private static final List<SimpleGrantedAuthority> USER_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));
	private static final List<SimpleGrantedAuthority> ADMIN_AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

	private final SessionService sessionService;
	private final AdminSessionService adminSessionService;

	public BearerTokenAuthenticationFilter(SessionService sessionService, AdminSessionService adminSessionService) {
		this.sessionService = sessionService;
		this.adminSessionService = adminSessionService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			String token = header.substring(PREFIX.length()).trim();
			if (isAdminPath(request)) {
				adminSessionService.authenticate(token).ifPresent(admin -> login(admin, ADMIN_AUTHORITIES));
			} else {
				sessionService.authenticate(token).ifPresent(user -> login(user, USER_AUTHORITIES));
			}
		}
		chain.doFilter(request, response);
	}

	// SecurityConfig의 "/api/admin/**" 규칙과 같은 범위 (/api/admin 자체 포함)
	static boolean isAdminPath(HttpServletRequest request) {
		String path = Optional.ofNullable(request.getServletPath()).orElse("");
		String pathInfo = request.getPathInfo();
		if (pathInfo != null) {
			path += pathInfo;
		}
		return path.equals(ADMIN_PATH) || path.startsWith(ADMIN_PATH + "/");
	}

	private static void login(Object principal, List<SimpleGrantedAuthority> authorities) {
		var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
	}
}
