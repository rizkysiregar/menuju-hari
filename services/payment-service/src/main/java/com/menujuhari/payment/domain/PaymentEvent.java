package com.menujuhari.payment.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Audit trail webhook/inquiry (PAY-002/003): semua payload mentah
 * tersimpan — dasar rekonsiliasi & investigasi selisih.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(name = "gateway_order_id", nullable = false, length = 64)
	private String gatewayOrderId;

	@Column(name = "order_number", nullable = false, length = 64)
	private String orderNumber;

	@Column(name = "event_type", nullable = false, length = 40)
	private String eventType;

	@Column(name = "status_code", length = 10)
	private String statusCode;

	@Column(name = "fraud_status", length = 20)
	private String fraudStatus;

	@Column(name = "gross_amount", length = 40)
	private String grossAmount;

	@Column(name = "signature_valid", nullable = false)
	private boolean signatureValid;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(nullable = false)
	private boolean processed;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	protected PaymentEvent() {
	}

	public static PaymentEvent incoming(String gatewayOrderId, String orderNumber, String eventType,
			String statusCode, String fraudStatus, String grossAmount, boolean signatureValid, String payload) {
		PaymentEvent event = new PaymentEvent();
		event.gatewayOrderId = gatewayOrderId;
		event.orderNumber = orderNumber;
		event.eventType = eventType;
		event.statusCode = statusCode;
		event.fraudStatus = fraudStatus;
		event.grossAmount = grossAmount;
		event.signatureValid = signatureValid;
		event.payload = payload;
		event.processed = false;
		return event;
	}

	@PrePersist
	void onCreate() {
		if (this.receivedAt == null) {
			this.receivedAt = Instant.now();
		}
	}

	public void markProcessed() {
		this.processed = true;
	}

	public UUID getId() {
		return id;
	}

	public String getGatewayOrderId() {
		return gatewayOrderId;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public String getEventType() {
		return eventType;
	}

	public String getStatusCode() {
		return statusCode;
	}

	public String getFraudStatus() {
		return fraudStatus;
	}

	public String getGrossAmount() {
		return grossAmount;
	}

	public boolean isSignatureValid() {
		return signatureValid;
	}

	public boolean isProcessed() {
		return processed;
	}
}
