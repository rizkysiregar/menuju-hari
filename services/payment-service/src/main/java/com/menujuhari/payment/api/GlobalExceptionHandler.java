package com.menujuhari.payment.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.payment.application.HttpMidtransGateway.GatewayException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(GatewayException.class)
	ResponseEntity<GlobalExceptionHandler.ErrorResponse> handleGateway(GatewayException e) {
		org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Gateway error", e);
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse("GATEWAY_ERROR", "Gateway pembayaran bermasalah. Coba lagi."));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Kesalahan tidak terduga", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL", "Terjadi kesalahan. Silakan coba lagi."));
	}

	public record ErrorResponse(String code, String message) {
	}
}
