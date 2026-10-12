package com.menujuhari.catalog.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.catalog.application.CatalogService.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", e.getMessage()));
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
