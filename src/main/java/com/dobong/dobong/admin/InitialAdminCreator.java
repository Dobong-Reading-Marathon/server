package com.dobong.dobong.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 관리자 계정이 하나도 없으면, 서버 시작 시 환경변수로 첫 관리자 계정을 만든다.
 * 비밀번호가 깃에 남지 않도록 SQL 마이그레이션 대신 이 방식을 쓴다.
 *
 * <ul>
 *   <li>ADMIN_LOGIN_ID, ADMIN_PASSWORD: 필수. 비밀번호는 8자 이상</li>
 *   <li>ADMIN_NAME: 선택. 기본값은 "관리자"</li>
 * </ul>
 *
 * 관리자가 이미 있으면 아무것도 하지 않는다. 환경변수를 바꿔도 기존 계정의 비밀번호는 바뀌지 않는다.
 * 비밀번호는 접두사 없는 BCrypt 해시로 저장하므로, 로그인 검증에도 BCryptPasswordEncoder를 써야 한다.
 */
@Slf4j
@Component
public class InitialAdminCreator implements ApplicationRunner {

	static final int MIN_PASSWORD_LENGTH = 8;

	private final JdbcTemplate jdbcTemplate;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final String loginId;
	private final String password;
	private final String name;

	public InitialAdminCreator(
			JdbcTemplate jdbcTemplate,
			@Value("${ADMIN_LOGIN_ID:}") String loginId,
			@Value("${ADMIN_PASSWORD:}") String password,
			@Value("${ADMIN_NAME:관리자}") String name) {
		this.jdbcTemplate = jdbcTemplate;
		this.loginId = loginId;
		this.password = password;
		this.name = name;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer adminCount = jdbcTemplate.queryForObject("SELECT count(*) FROM admins", Integer.class);
		if (adminCount != null && adminCount > 0) {
			return;
		}

		if (!StringUtils.hasText(loginId) || !StringUtils.hasText(password)) {
			log.warn("관리자 계정이 없습니다. ADMIN_LOGIN_ID, ADMIN_PASSWORD 환경변수를 설정하면 서버 시작 시 만들어집니다.");
			return;
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException("ADMIN_PASSWORD는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
		}

		// 서버가 여러 대 동시에 시작해도 같은 계정이 두 번 들어가지 않게 ON CONFLICT로 막는다
		jdbcTemplate.update(
				"INSERT INTO admins (login_id, password_hash, name) VALUES (?, ?, ?) ON CONFLICT (login_id) DO NOTHING",
				loginId, passwordEncoder.encode(password), name);
		log.info("첫 관리자 계정을 만들었습니다. 아이디: {}", loginId);
	}
}
