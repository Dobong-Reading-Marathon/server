package com.dobong.dobong.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 주소별 접근 권한 (서버의 "문지기" 규칙).
 *
 * 규칙은 위에서부터 차례로 적용되고, 마지막 줄에서 나머지를 모두 막는다 (기본 거부).
 * 그래서 새 API를 만들면 처음에는 막혀 있고, 아래 규칙에 맞는 주소일 때만 열린다.
 *
 * /api/admin/** : 관리자(ROLE_ADMIN)만
 * 그 밖의 /api/** : 로그인한 참가자(ROLE_USER)만
 * 로그인 없이 열어야 하는 주소(가입, 로그인 등)는 각 기능 PR에서 permitAll 줄을 이 위에 추가한다
 * 나머지: 모두 거부
 *
 * CSRF 보호를 끈 이유: 로그인 정보를 쿠키가 아니라 Authorization 헤더로 보낼 예정이라,
 * 다른 사이트가 사용자 브라우저를 이용해 요청을 위조해도 로그인 정보가 자동으로 실리지 않는다.
 * 나중에 쿠키 방식으로 바꾸면 CSRF 보호를 반드시 다시 켜야 한다.
 */
@Configuration
public class SecurityConfig {

	/**
	 * Spring 기본 로그인(아이디/비밀번호 폼)은 쓰지 않는다.
	 * 이 빈이 없으면 Spring Boot가 "user" 계정과 임시 비밀번호를 자동으로 만들어 서버 로그에 찍는다.
	 * 로그를 볼 수 있는 사람이 그 계정을 쓸 수 있으므로 막는다.
	 */
	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("not used");
		};
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults()) // 아래 corsConfigurationSource 빈을 쓴다
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e
						.authenticationEntryPoint((req, res, ex) ->
								writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."))
						.accessDeniedHandler((req, res, ex) ->
								writeError(res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다.")))
				.authorizeHttpRequests(auth -> auth
						// 브라우저가 실제 요청 전에 보내는 CORS 사전 확인 요청
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						// 오류가 났을 때 Spring이 내부적으로 넘어가는 주소. 막으면 원래 오류 대신 401이 나간다
						.requestMatchers("/error").permitAll()
						// (로그인 없이 열 주소는 각 기능 PR에서 이 자리에 추가)
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/**").hasRole("USER")
						.anyRequest().denyAll());
		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		config.setAllowCredentials(false); // 쿠키를 쓰지 않으므로 끈다
		config.setMaxAge(3600L);           // 사전 확인 결과를 브라우저가 1시간 기억
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}

	/** 401, 403을 GlobalExceptionHandler와 같은 형식으로 보낸다. 문구는 고정값만 쓴다 (사용자 입력을 넣지 않음). */
	private static void writeError(HttpServletResponse res, int status, String code, String message) throws IOException {
		res.setStatus(status);
		res.setContentType("application/json;charset=UTF-8");
		res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fieldErrors\":[]}");
	}
}
