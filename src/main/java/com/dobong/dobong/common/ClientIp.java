package com.dobong.dobong.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 보낸 쪽 IP
 *
 * X-Forwarded-For 헤더는 직접 읽지 않음. 누구나 꾸며 보낼 수 있어서, 읽으면 IP를 바꿔 가며 횟수 제한을 피할 수 있음.
 * 배포 환경(프록시 뒤)에서는 server.forward-headers-strategy 설정으로 믿을 수 있는 프록시의 값만 반영되게 한다 (배포 때 확인).
 */
public final class ClientIp {

	private ClientIp() {
	}

	public static String of(HttpServletRequest request) {
		String ip = request.getRemoteAddr();
		return ip != null ? ip : "unknown";
	}
}
