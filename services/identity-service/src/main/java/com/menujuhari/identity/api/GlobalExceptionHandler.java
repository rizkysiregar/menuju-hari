package com.menujuhari.identity.api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.identity.application.OtpVerificationException;
import com.menujuhari.identity.application.RateLimitExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException e) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.body(new ErrorResponse("RATE_LIMITED", "Terlalu banyak permintaan. Silakan coba lagi nanti."));
	}

	@ExceptionHandler(OtpVerificationException.class)
	ResponseEntity<ErrorResponse> handleOtpInvalid(OtpVerificationException e) {
		// Satu pesan untuk semua mode gagal (salah/kedaluwarsa/habis percobaan)
		// agar tidak membocorkan status akun (anti-enumerasi).
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("OTP_INVALID", "Kode OTP tidak valid atau kedaluwarsa."));
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
