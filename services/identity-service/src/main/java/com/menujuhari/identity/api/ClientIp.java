package com.menujuhari.identity.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Ekstraksi IP klien di belakang Cloudflare Tunnel / Ingress.
 * Kepercayaan header X-Forwarded-For diatur di sisi Ingress; di sini
 * nilai pertama diambil untuk kebutuhan rate limit.
 */
public final class ClientIp {

	private ClientIp() {
	}

	public static String resolve(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
