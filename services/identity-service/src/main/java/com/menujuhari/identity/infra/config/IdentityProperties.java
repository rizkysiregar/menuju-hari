package com.menujuhari.identity.infra.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record IdentityProperties(Otp otp, Mail mail) {

	public record Otp(
			Duration ttl,
			int maxAttempts,
			Duration cooldown,
			int maxPerEmailPerHour,
			int maxPerEmailPerDay,
			int maxPerIpPerHour) {
	}

	public record Mail(String from, boolean consoleFallback) {
	}
}
