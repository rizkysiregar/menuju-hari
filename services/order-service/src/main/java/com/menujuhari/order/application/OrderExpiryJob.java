package com.menujuhari.order.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORD-006: order PENDING > 24 jam -> EXPIRED (kupon dilepas).
 */
@Component
public class OrderExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

	private final OrderService orderService;

	public OrderExpiryJob(OrderService orderService) {
		this.orderService = orderService;
	}

	@Scheduled(fixedDelay = 900_000, initialDelay = 300_000) // tiap 15 menit
	@Transactional
	public void expire() {
		int expired = orderService.expireStaleOrders();
		if (expired > 0) {
			log.info("Expiry job: {} order -> EXPIRED", expired);
		}
	}
}
