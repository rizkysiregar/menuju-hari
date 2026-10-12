package com.menujuhari.invitation.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

/**
 * Konsumen stream mh:stream:orders (group: invitation-service).
 * Event order.paid dari order-service memicu publikasi otomatis (PUB-001).
 */
@Component
public class OrderPaidConsumer {

	private static final Logger log = LoggerFactory.getLogger(OrderPaidConsumer.class);
	public static final String ORDERS_STREAM = "mh:stream:orders";
	public static final String GROUP = "invitation-service";

	private final InvitationService invitationService;
	private final StringRedisTemplate redis;

	public OrderPaidConsumer(InvitationService invitationService, StringRedisTemplate redis) {
		this.invitationService = invitationService;
		this.redis = redis;
	}

	@PostConstruct
	void createGroup() {
		try {
			// MKSTREAM: buat stream bila belum ada, agar group tidak gagal dibuat
			redis.execute((org.springframework.data.redis.connection.RedisConnection connection) ->
					connection.streamCommands()
							.xGroupCreate(ORDERS_STREAM.getBytes(), GROUP, ReadOffset.from("0"), true));
		}
		catch (Exception e) {
			log.debug("Group {} sudah ada: {}", GROUP, e.getMessage());
		}
	}

	public void handle(MapRecord<String, String, String> record) {
		Map<String, String> fields = record.getValue();
		if (!"order.paid".equals(fields.get("type"))) {
			return;
		}
		try {
			UUID invitationId = UUID.fromString(fields.get("invitationId"));
			UUID userId = UUID.fromString(fields.get("userId"));
			invitationService.publishAfterPayment(userId, invitationId);
		}
		catch (Exception e) {
			log.error("Gagal memproses order.paid: {}", record.getValue(), e);
		}
	}

	@Configuration
	static class ListenerConfig {

		@Bean
		StreamMessageListenerContainer<String, MapRecord<String, String, String>> orderStreamContainer(
				RedisConnectionFactory connectionFactory, OrderPaidConsumer consumer) {
			var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
					.pollTimeout(java.time.Duration.ofSeconds(1))
					.build();
			var container = StreamMessageListenerContainer.create(connectionFactory, options);
			container.receive(
					Consumer.from(GROUP, "invitation-consumer"),
					StreamOffset.create(OrderPaidConsumer.ORDERS_STREAM, ReadOffset.lastConsumed()),
					consumer::handle);
			container.start();
			return container;
		}
	}
}
