package com.menujuhari.shared.security;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfigurasi keamanan bersama semua service.
 * Nilai per-service diatur di application.yml masing-masing.
 */
@ConfigurationProperties(prefix = "mh.security")
public record SecuritySharedProperties(
		Duration sessionTtl,
		String cookieName,
		boolean cookieSecure,
		List<String> allowedOrigins,
		List<String> permitAllPaths,
		boolean apiDocsEnabled,
		Openapi openapi) {

	public record Openapi(String title, String description, String version) {
	}
}
