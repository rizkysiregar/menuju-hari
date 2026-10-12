package com.menujuhari.order.application;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Penerbit event antar-service via Redis Streams (mh:stream:orders).
 * Konsumen: invitation-service (aktivasi publikasi).
 */
@Component
public class EventPublisher {

	public static final String ORDERS_STREAM = "mh:stream:orders";

	private final StringRedisTemplate redis;

	public EventPublisher(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void publish(String stream, Map<String, String> fields) {
		redis.opsForStream().add(stream, fields);
	}

	public void publishOrderPaid(String orderNumber, UUID invitationId, UUID userId) {
		publish(ORDERS_STREAM, Map.of(
				"type", "order.paid",
				"orderNumber", orderNumber,
				"invitationId", invitationId.toString(),
				"userId", userId.toString()));
	}
}
