package com.menujuhari.invitation.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invitation")
public record InvitationProperties(
		int maxActiveDrafts,
		int contentMaxChars,
		int slugMaxAttempts) {

	public InvitationProperties {
		if (maxActiveDrafts <= 0) {
			maxActiveDrafts = 3;
		}
		if (contentMaxChars <= 0) {
			contentMaxChars = 131072;
		}
		if (slugMaxAttempts <= 0) {
			slugMaxAttempts = 5;
		}
	}
}
