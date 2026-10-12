package com.menujuhari.shared.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;

/**
 * Sesi opaque server-side di Redis (AUTH-003):
 * token 256-bit acak, cookie httpOnly, TTL sliding.
 * Satu implementasi dipakai semua service — sesi dibuat oleh identity-service,
 * dibaca validasi oleh service lain.
 */
public class SessionService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String KEY_PREFIX = "session:";

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	public SessionService(StringRedisTemplate redis, ObjectMapper objectMapper, SecuritySharedProperties props) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.ttl = props.sessionTtl();
	}

	public String create(java.util.UUID userId, String email, String role) {
		byte[] tokenBytes = new byte[32];
		RANDOM.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		SessionData data = new SessionData(userId.toString(), email, role);
		try {
			redis.opsForValue().set(key(token), objectMapper.writeValueAsString(data), ttl);
		}
		catch (Exception e) {
			throw new IllegalStateException("Gagal menyimpan sesi", e);
		}
		return token;
	}

	public Optional<SessionData> get(String token) {
		String value = redis.opsForValue().get(key(token));
		if (value == null) {
			return Optional.empty();
		}
		redis.expire(key(token), ttl);
		try {
			return Optional.of(objectMapper.readValue(value, SessionData.class));
		}
		catch (Exception e) {
			redis.delete(key(token));
			return Optional.empty();
		}
	}

	public void delete(String token) {
		redis.delete(key(token));
	}

	private String key(String token) {
		return KEY_PREFIX + token;
	}

	public record SessionData(String userId, String email, String role) {
	}
}
