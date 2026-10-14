package com.menujuhari.payment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

	@Id
	@UuidGenerator
	private UUID id;

	/** Nomor order (order-service). */
	@Column(name = "order_number", nullable = false, length = 64)
	private String orderNumber;

	/** order_id di sisi gateway; attempt > 1 diberi suffix -rN (order_id Midtrans unik). */
	@Column(name = "gateway_order_id", nullable = false, length = 128)
	private String gatewayOrderId;

	@Column(nullable = false)
	private int attempt;

	@Column(nullable = false, length = 20)
	private String gateway;

	@Column(length = 255)
	private String token;

	@Column(name = "redirect_url", length = 500)
	private String redirectUrl;

	@Column(name = "gross_amount_idr", nullable = false)
	private int grossAmountIdr;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PaymentStatus status;

	@Column(name = "payment_type", length = 40)
	private String paymentType;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Payment() {
	}

	public static Payment newPending(String orderNumber, String gatewayOrderId, int attempt,
			int grossAmountIdr, String token, String redirectUrl) {
		Payment payment = new Payment();
		payment.orderNumber = orderNumber;
		payment.gatewayOrderId = gatewayOrderId;
		payment.attempt = attempt;
		payment.gateway = "MIDTRANS";
		payment.grossAmountIdr = grossAmountIdr;
		payment.token = token;
		payment.redirectUrl = redirectUrl;
		payment.status = PaymentStatus.PENDING;
		return payment;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public String getGatewayOrderId() {
		return gatewayOrderId;
	}

	public int getAttempt() {
		return attempt;
	}

	public String getGateway() {
		return gateway;
	}

	public String getToken() {
		return token;
	}

	public String getRedirectUrl() {
		return redirectUrl;
	}

	public int getGrossAmountIdr() {
		return grossAmountIdr;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(Instant paidAt) {
		this.paidAt = paidAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
