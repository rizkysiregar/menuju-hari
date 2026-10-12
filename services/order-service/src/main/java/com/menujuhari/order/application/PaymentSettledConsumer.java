package com.menujuhari.order.application;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Konsumen stream mh:stream:payments (group: order-service).
 * Event payment.settled dari payment-service = SATU-SATUNYA sumber
 * status PAID (webhook/inquiry memprosesnya di payment-service).
 */
@Component
public class PaymentSettledConsumer {

	private static final Logger log = LoggerFactory.getLogger(PaymentSettledConsumer.class);
	public static final String PAYMENTS_STREAM = "mh:stream:payments";
	public static final String GROUP = "order-service";

	private final OrderService orderService;
	private final StringRedisTemplate redis;

	public PaymentSettledConsumer(OrderService orderService, StringRedisTemplate redis) {
		this.orderService = orderService;
		this.redis = redis;
	}

	@PostConstruct
	void createGroup() {
		try {
			// MKSTREAM: buat stream bila belum ada, agar group tidak gagal dibuat
			redis.execute((org.springframework.data.redis.connection.RedisConnection connection) ->
					connection.streamCommands()
							.xGroupCreate(PAYMENTS_STREAM.getBytes(), GROUP, ReadOffset.from("0"), true));
		}
		catch (Exception e) {
			// BUSYGROUP: group sudah ada — aman diabaikan
			log.debug("Group {} sudah ada: {}", GROUP, e.getMessage());
		}
	}

	public void handle(MapRecord<String, String, String> record) {
		Map<String, String> fields = record.getValue();
		String type = fields.get("type");
		if (!"payment.settled".equals(type)) {
			return;
		}
		String orderNumber = fields.get("orderNumber");
		String paymentType = fields.get("paymentType");
		String paidAtRaw = fields.get("paidAt");
		Instant paidAt = null;
		if (paidAtRaw != null) {
			try {
				paidAt = Instant.parse(paidAtRaw);
			}
			catch (DateTimeParseException e) {
				paidAt = null;
			}
		}
		orderService.applyPaymentSettled(orderNumber, paymentType, paidAt);
	}

	/** Wiring listener container (pol: 1 detik). */
	@Configuration
	static class ListenerConfig {

		@Bean
		StreamMessageListenerContainer<String, MapRecord<String, String, String>> paymentStreamContainer(
				RedisConnectionFactory connectionFactory, PaymentSettledConsumer consumer) {
			var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
					.pollTimeout(java.time.Duration.ofSeconds(1))
					.build();
			var container = StreamMessageListenerContainer.create(connectionFactory, options);
			container.receive(
					Consumer.from(GROUP, "order-consumer"),
					StreamOffset.create(PaymentSettledConsumer.PAYMENTS_STREAM, ReadOffset.lastConsumed()),
					consumer::handle);
			container.start();
			return container;
		}
	}
}
