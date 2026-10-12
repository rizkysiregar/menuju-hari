package com.menujuhari.payment.application;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rekonsiliasi terjadwal (PAY-003): payment PENDING > 30 menit
 * -> inquiry ke Midtrans -> sinkronkan status.
 */
@Component
public class ReconciliationJob {

	private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

	private final PaymentService paymentService;

	public ReconciliationJob(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@Scheduled(fixedDelay = 900_000, initialDelay = 600_000) // tiap 15 menit
	@Transactional
	public void reconcile() {
		List<com.menujuhari.payment.domain.Payment> stale =
				paymentService.stalePendingPayments(Duration.ofMinutes(30));
		int synced = 0;
		for (var payment : stale) {
			try {
				if (paymentService.inquireAndSync(payment.getGatewayOrderId())) {
					synced++;
				}
			}
			catch (Exception e) {
				log.warn("Rekonsiliasi gagal untuk {}: {}", payment.getGatewayOrderId(), e.getMessage());
			}
		}
		if (synced > 0) {
			log.info("Rekonsiliasi: {} payment tersinkron", synced);
		}
	}
}
