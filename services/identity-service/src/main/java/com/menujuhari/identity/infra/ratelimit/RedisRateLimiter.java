package com.menujuhari.identity.infra.ratelimit;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.menujuhari.identity.application.RateLimitExceededException;

/**
 * Rate limiter window tetap (fixed window) berbasis Redis.
 * Perlu dievaluasi bila buturuh distribusi yang lebih halus (token bucket).
 */
@Component
public class RedisRateLimiter {

	private final StringRedisTemplate redis;

	public RedisRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * Satu-satunya izin dalam periode ttl (untuk cooldown kirim ulang).
	 */
	public void requirePermitOnce(String key, Duration ttl) {
		Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", ttl);
		if (!Boolean.TRUE.equals(acquired)) {
			throw new RateLimitExceededException();
		}
	}

	/**
	 * Counter window tetap; melebihi limit -> exception.
	 */
	public void requirePermit(String key, long limit, Duration window) {
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1) {
			redis.expire(key, window);
		}
		if (count != null && count > limit) {
			throw new RateLimitExceededException();
		}
	}
}
