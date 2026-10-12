package com.menujuhari.payment.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.payment.application.PaymentService;
import com.menujuhari.payment.domain.Payment;

/**
 * Endpoint antar-service (order-service). TIDAK terekspos internet di
 * produksi — Ingress hanya me-route route publik.
 */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

	private final PaymentService paymentService;

	public InternalPaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	public record PaymentSessionResponse(String gatewayOrderId, String token, String redirectUrl) {
	}

	public record CreateSessionRequest(String orderNumber, int grossAmountIdr, String itemName,
			String customerEmail) {
	}

	@PostMapping("/sessions")
	public ResponseEntity<PaymentSessionResponse> createSession(
			@org.springframework.web.bind.annotation.RequestBody CreateSessionRequest request) {
		Payment payment = paymentService.createSession(request.orderNumber(), request.grossAmountIdr(),
				request.itemName(), request.customerEmail());
		return ResponseEntity.ok(new PaymentSessionResponse(payment.getGatewayOrderId(), payment.getToken(),
				payment.getRedirectUrl()));
	}

	/** Inquiry manual (ADM-003 / rekonsiliasi). */
	@GetMapping("/{gatewayOrderId}/inquire")
	public ResponseEntity<Map<String, String>> inquire(@PathVariable String gatewayOrderId,
			@RequestParam(name = "sync", defaultValue = "false") boolean sync) {
		if (sync) {
			paymentService.inquireAndSync(gatewayOrderId);
		}
		Payment payment = paymentService.getByGatewayOrderId(gatewayOrderId);
		return ResponseEntity.ok(Map.of(
				"gatewayOrderId", payment.getGatewayOrderId(),
				"orderNumber", payment.getOrderNumber(),
				"status", payment.getStatus().name()));
	}
}
