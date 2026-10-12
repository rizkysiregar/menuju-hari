package com.menujuhari.media.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PhotoResponse(
		UUID id,
		String status,
		String contentType,
		long sizeBytes,
		String originalUrl,
		Map<String, Object> variants,
		Instant createdAt) {
}
