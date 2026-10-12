package com.menujuhari.media.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kredensial MinIO tidak memiliki default — wajib lewat .env.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {

	public MinioProperties {
		if (endpoint == null || endpoint.isBlank()) {
			endpoint = "http://192.168.1.50:9000";
		}
		if (bucket == null || bucket.isBlank()) {
			bucket = "menujuhari";
		}
	}
}
