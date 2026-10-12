package com.menujuhari.order.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.order.api.dto.CheckoutRequest;
import com.menujuhari.order.api.dto.OrderResponse;
import com.menujuhari.order.application.HttpPaymentClient.PaymentUnavailableException;
import com.menujuhari.order.application.OrderService;
import com.menujuhari.order.application.HttpCatalogClient.CatalogUnavailableException;
import com.menujuhari.order.application.HttpCatalogClient.PackageNotFoundException;
import com.menujuhari.order.domain.Order;
import com.menujuhari.shared.security.SessionPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	/** ORD-001: checkout idempoten via header Idempotency-Key. */
	@PostMapping("/checkout")
	public ResponseEntity<OrderResponse> checkout(@Valid @RequestBody CheckoutRequest request,
			@AuthenticationPrincipal SessionPrincipal principal,
			@org.springframework.web.bind.annotation.RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
		Order order = orderService.checkout(principal.userId(), principal.email(), request.invitationId(),
				request.packageCode(), request.couponCode(), idempotencyKey);
		return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
	}

	/** ORD-004: lanjutkan pembayaran -> payment session Midtrans. */
	@PostMapping("/orders/{orderNumber}/pay")
	public ResponseEntity<com.menujuhari.order.application.PaymentClient.PaymentSession> pay(
			@PathVariable String orderNumber,
			@AuthenticationPrincipal SessionPrincipal principal) {
		return ResponseEntity.ok(orderService.startPayment(orderNumber, principal.userId(), principal.email()));
	}

	@GetMapping("/orders")
	public Page<OrderResponse> list(
			@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return orderService.list(principal.userId(), PageRequest.of(Math.max(page, 0), Math.min(size, 50)))
				.map(OrderResponse::from);
	}

	@GetMapping("/orders/{orderNumber}")
	public ResponseEntity<OrderResponse> detail(@PathVariable String orderNumber,
			@AuthenticationPrincipal SessionPrincipal principal) {
		return ResponseEntity.ok(OrderResponse.from(orderService.getOwned(principal.userId(), orderNumber)));
	}
}
