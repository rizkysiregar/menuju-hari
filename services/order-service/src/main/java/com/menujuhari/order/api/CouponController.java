package com.menujuhari.order.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.order.application.OrderService;
import com.menujuhari.shared.security.SessionPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/v1/coupons")
public class CouponController {

	private final OrderService orderService;

	public CouponController(OrderService orderService) {
		this.orderService = orderService;
	}

	public record CouponValidateRequest(
			@NotBlank(message = "Kode kupon wajib diisi.") String code,
			@NotBlank(message = "packageCode wajib diisi.") String packageCode) {
	}

	/** CUP-002: preview validitas kupon (tanpa mengunci penukaran). */
	@PostMapping("/validate")
	public ResponseEntity<OrderService.CouponPreview> validate(
			@Valid @RequestBody CouponValidateRequest request,
			@AuthenticationPrincipal SessionPrincipal principal) {
		return ResponseEntity.ok(orderService.previewCoupon(request.code(), request.packageCode(),
				principal.userId()));
	}
}
