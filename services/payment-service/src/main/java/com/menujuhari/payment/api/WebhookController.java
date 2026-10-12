package com.menujuhari.payment.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.payment.application.PaymentService;

/**
 * Webhook Midtrans (PAY-002). Publik via Cloudflare Tunnel.
 * Respond 2xx = diterima (Midtrans berhenti retry); signature tidak
 * valid tetap dijawab 2xx setelah tercatat agar tidak retry tak berujung —
 * event tetap tersimpan untuk audit.
 */
@RestController
@RequestMapping("/v1/webhooks/payments")
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private final PaymentService paymentService;

	public WebhookController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/midtrans")
	public ResponseEntity<Map<String, String>> midtrans(@RequestBody Map<String, Object> payload) {
		try {
			paymentService.handleNotification(payload);
		}
		catch (Exception e) {
			log.error("Gagal memproses webhook Midtrans", e);
			// 500 -> Midtrans retry sekali (docs); event tetap tersimpan
			return ResponseEntity.internalServerError()
					.body(Map.of("status", "error", "message", "internal"));
		}
		return ResponseEntity.ok(Map.of("status", "ok"));
	}
}
