package com.menujuhari.payment.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.payment.domain.Payment;
import com.menujuhari.payment.domain.PaymentEvent;
import com.menujuhari.payment.domain.PaymentEventRepository;
import com.menujuhari.payment.domain.PaymentRepository;
import com.menujuhari.payment.domain.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

/**
 * Logika pembayaran sesuai PAY-001..005 (PROJECT_PLAN):
 * session Snap, webhook idempoten dengan state machine, verifikasi signature
 * & nominal, event payment.settled, dan inquiry rekonsiliasi.
 */
@Service
public class PaymentService {

	private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
	public static final String PAYMENTS_STREAM = "mh:stream:payments";

	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository eventRepository;
	private final MidtransGateway gateway;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public PaymentService(PaymentRepository paymentRepository, PaymentEventRepository eventRepository,
			MidtransGateway gateway, StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.paymentRepository = paymentRepository;
		this.eventRepository = eventRepository;
		this.gateway = gateway;
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	/**
	 * Sesi pembayaran: pakai ulang sesi PENDING yang ada (token Snap masih
	 * hidup), atau buat attempt baru dengan gateway_order_id -rN (order_id
	 * Midtrans unik per transaksi).
	 */
	@Transactional
	public Payment createSession(String orderNumber, int grossAmountIdr, String itemName, String customerEmail) {
		List<Payment> existing = paymentRepository.findByOrderNumberOrderByAttemptDesc(orderNumber);
		Payment pending = existing.stream()
				.filter(p -> p.getStatus() == PaymentStatus.PENDING)
				.findFirst()
				.orElse(null);
		if (pending != null) {
			return pending; // reuse — token masih valid
		}
		if (existing.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SETTLED)) {
			throw new IllegalStateException("Order sudah terbayar");
		}

		int attempt = existing.isEmpty() ? 1 : existing.get(0).getAttempt() + 1;
		String gatewayOrderId = attempt == 1 ? orderNumber : orderNumber + "-r" + attempt;
		MidtransGateway.SnapTransaction transaction = gateway.createSnapTransaction(gatewayOrderId,
				grossAmountIdr, itemName, customerEmail);
		Payment payment = paymentRepository.save(
				Payment.newPending(orderNumber, gatewayOrderId, attempt, grossAmountIdr,
						transaction.token(), transaction.redirectUrl()));
		log.info("Sesi pembayaran dibuat: {} attempt {} (order {})", gatewayOrderId, attempt, orderNumber);
		return payment;
	}

	/**
	 * Webhook Midtrans (PAY-002): verifikasi signature, cek nominal, state
	 * machine, idempoten terhadap duplikat & out-of-order, lalu publikasi
	 * event payment.settled bila settlement diterapkan.
	 */
	@Transactional
	public void handleNotification(Map<String, Object> payload) {
		String orderId = str(payload, "order_id");
		String statusCode = str(payload, "status_code");
		String grossAmount = str(payload, "gross_amount");
		String signatureKey = str(payload, "signature_key");
		String transactionStatus = str(payload, "transaction_status");
		String fraudStatus = payload.get("fraud_status") == null ? null : String.valueOf(payload.get("fraud_status"));

		boolean signatureValid = gateway.verifySignature(orderId, statusCode, grossAmount, signatureKey);
		Payment payment = paymentRepository.findByGatewayOrderId(orderId).orElse(null);

		if (payment == null) {
			eventRepository.save(PaymentEvent.incoming(orderId, orderId, transactionStatus, statusCode,
					fraudStatus, grossAmount, signatureValid, toJson(payload)));
			log.warn("Webhook untuk payment tak dikenal: {}", orderId);
			return;
		}

		PaymentEvent event = eventRepository.save(PaymentEvent.incoming(orderId, payment.getOrderNumber(),
				transactionStatus, statusCode, fraudStatus, grossAmount, signatureValid, toJson(payload)));

		if (!signatureValid) {
			log.warn("Webhook dengan signature TIDAK VALID ditolak: {} (order {})", orderId, payment.getOrderNumber());
			return;
		}

		long amountFromGateway = MidtransSignature.parseGrossAmount(grossAmount);
		if (amountFromGateway != payment.getGrossAmountIdr()) {
			// Nominal tidak cocok = indikasi manipulation/error — simpan, jangan proses (PAY-002)
			log.error("Webhook nominal tidak cocok: {} vs {} (order {})", amountFromGateway,
					payment.getGrossAmountIdr(), payment.getOrderNumber());
			return;
		}

		PaymentStatus target = mapStatus(transactionStatus, fraudStatus);
		PaymentStatus before = payment.getStatus();

		if (!before.canTransitionTo(target)) {
			log.info("Webhook diabaikan (transisi {} -> {} tidak legal, order {})", before, target,
					payment.getOrderNumber());
			event.markProcessed();
			return;
		}

		payment.setStatus(target);
		if (target == PaymentStatus.SETTLED) {
			payment.setPaymentType(str(payload, "payment_type"));
			payment.setPaidAt(Instant.now());
		}
		event.markProcessed();
		paymentRepository.save(payment);

		if (target == PaymentStatus.SETTLED) {
			publishSettled(payment);
		}
		log.info("Payment {} : {} -> {}", orderId, before, target);
	}

	/** Inquiry & rekonsiliasi (PAY-003) — juga dipicu admin (ADM-003). */
	@Transactional
	public boolean inquireAndSync(String gatewayOrderId) {
		Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow(
				() -> new IllegalArgumentException("Payment tidak dikenal: " + gatewayOrderId));
		if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.EXPIRED) {
			return false;
		}
		MidtransGateway.TransactionStatus status = gateway.getStatus(gatewayOrderId);
		PaymentStatus target = mapStatus(status.transactionStatus(), status.fraudStatus());
		if (target == PaymentStatus.PENDING || !payment.getStatus().canTransitionTo(target)) {
			return false;
		}
		payment.setStatus(target);
		if (target == PaymentStatus.SETTLED) {
			payment.setPaymentType(status.paymentType());
			payment.setPaidAt(Instant.now());
			publishSettled(payment);
		}
		log.info("Inquiry {}: {} -> {}", gatewayOrderId, payment.getStatus(), target);
		return true;
	}

	public List<Payment> stalePendingPayments(java.time.Duration olderThan) {
		return paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, Instant.now().minus(olderThan));
	}

	public Payment getByGatewayOrderId(String gatewayOrderId) {
		return paymentRepository.findByGatewayOrderId(gatewayOrderId).orElseThrow(
				() -> new IllegalArgumentException("Payment tidak dikenal: " + gatewayOrderId));
	}

	private void publishSettled(Payment payment) {
		redis.opsForStream().add(PAYMENTS_STREAM, Map.of(
				"type", "payment.settled",
				"orderNumber", payment.getOrderNumber(),
				"gatewayOrderId", payment.getGatewayOrderId(),
				"grossAmount", String.valueOf(payment.getGrossAmountIdr()),
				"paymentType", payment.getPaymentType() == null ? "unknown" : payment.getPaymentType(),
				"paidAt", (payment.getPaidAt() == null ? Instant.now() : payment.getPaidAt()).toString()));
	}

	private static PaymentStatus mapStatus(String transactionStatus, String fraudStatus) {
		return switch (transactionStatus == null ? "" : transactionStatus.toLowerCase()) {
			case "settlement" -> PaymentStatus.SETTLED;
			case "capture" -> "challenge".equalsIgnoreCase(fraudStatus) ? PaymentStatus.PENDING : PaymentStatus.SETTLED;
			case "pending" -> PaymentStatus.PENDING;
			case "deny" -> PaymentStatus.DENIED;
			case "cancel" -> PaymentStatus.CANCELLED;
			case "expire" -> PaymentStatus.EXPIRED;
			case "refund", "partial_refund" -> PaymentStatus.REFUNDED;
			default -> PaymentStatus.PENDING;
		};
	}

	private String toJson(Map<String, Object> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (Exception e) {
			return "{}";
		}
	}

	private static String str(Map<String, Object> payload, String key) {
		Object value = payload.get(key);
		return value == null ? null : String.valueOf(value);
	}
}
