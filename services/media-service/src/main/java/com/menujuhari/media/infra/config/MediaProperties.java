package com.menujuhari.media.infra.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.media")
public record MediaProperties(
		long maxSizeBytes,
		int maxDimension,
		int maxPhotosPerUser,
		Duration presignUploadTtl,
		Duration presignReadTtl) {

	public MediaProperties {
		if (maxSizeBytes <= 0) {
			maxSizeBytes = 5_242_880;
		}
		if (maxDimension <= 0) {
			maxDimension = 4000;
		}
		if (maxPhotosPerUser <= 0) {
			maxPhotosPerUser = 60;
		}
		if (presignUploadTtl == null) {
			presignUploadTtl = Duration.ofMinutes(10);
		}
		if (presignReadTtl == null) {
			presignReadTtl = Duration.ofHours(1);
		}
	}
}
