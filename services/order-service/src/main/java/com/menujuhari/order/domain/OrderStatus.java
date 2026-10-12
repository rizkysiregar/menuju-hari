package com.menujuhari.order.domain;

/**
 * Status order (PROJECT_PLAN §8.3) — TERPISAH dari status pembayaran
 * (payment-service) dan status publikasi (invitation-service).
 * PENDING -> PAID (event payment.settled) | EXPIRED (job 24 jam) | CANCELLED
 * Pembayaran masuk setelah EXPIRED -> tetap PAID (uang nyata diterima).
 */
public enum OrderStatus {
	PENDING, PAID, EXPIRED, CANCELLED, REFUNDED
}
