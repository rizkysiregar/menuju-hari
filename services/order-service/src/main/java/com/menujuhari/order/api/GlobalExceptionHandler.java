package com.menujuhari.order.api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.order.application.HttpCatalogClient.CatalogUnavailableException;
import com.menujuhari.order.application.HttpCatalogClient.PackageNotFoundException;
import com.menujuhari.order.application.HttpPaymentClient.PaymentUnavailableException;
import com.menujuhari.order.application.OrderService;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler({OrderService.OrderNotFoundException.class, PackageNotFoundException.class})
	ResponseEntity<ErrorResponse> handleNotFound(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler({OrderService.OrderAlreadyPaidException.class})
	ResponseEntity<ErrorResponse> handleConflict(OrderService.OrderAlreadyPaidException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("ORDER_ALREADY_PAID", e.getMessage()));
	}

	@ExceptionHandler({OrderService.OrderNotPayableException.class})
	ResponseEntity<ErrorResponse> handleNotPayable(OrderService.OrderNotPayableException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("ORDER_NOT_PAYABLE", e.getMessage()));
	}

	@ExceptionHandler({OrderService.PackageInactiveException.class, OrderService.CouponInvalidException.class,
			OrderService.CouponInactiveException.class, OrderService.CouponMinTransactionException.class,
			OrderService.CouponPackageNotAllowedException.class, OrderService.CouponQuotaExceededException.class,
			OrderService.CouponPerUserLimitException.class})
	ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
		String code = switch (e) {
			case OrderService.PackageInactiveException ex -> "PACKAGE_INACTIVE";
			case OrderService.CouponInvalidException ex -> "COUPON_INVALID";
			case OrderService.CouponInactiveException ex -> "COUPON_INACTIVE";
			case OrderService.CouponMinTransactionException ex -> "COUPON_MIN_TRANSACTION";
			case OrderService.CouponPackageNotAllowedException ex -> "COUPON_PACKAGE_NOT_ALLOWED";
			case OrderService.CouponQuotaExceededException ex -> "COUPON_QUOTA_EXCEEDED";
			default -> "COUPON_PER_USER_LIMIT";
		};
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(code, e.getMessage()));
	}

	@ExceptionHandler({CatalogUnavailableException.class, PaymentUnavailableException.class})
	ResponseEntity<ErrorResponse> handleDependencyDown(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("DEPENDENCY_UNAVAILABLE", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new HashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("VALIDATION_FAILED", "Data yang dikirim tidak valid.", fieldErrors));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("Kesalahan tidak terduga", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL", "Terjadi kesalahan. Silakan coba lagi."));
	}

	public record ErrorResponse(String code, String message, Map<String, String> fieldErrors) {

		public ErrorResponse(String code, String message) {
			this(code, message, Map.of());
		}
	}
}
