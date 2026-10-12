package com.menujuhari.media.api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.media.application.CatalogUnavailableException;
import com.menujuhari.media.application.MediaNotFoundException;
import com.menujuhari.media.application.PhotoInvalidException;
import com.menujuhari.media.application.PhotoQuotaExceededException;
import com.menujuhari.media.application.PhotoTooLargeException;
import com.menujuhari.media.application.PhotoTypeNotSupportedException;
import com.menujuhari.media.application.TrackNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MediaNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(MediaNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(TrackNotFoundException.class)
	ResponseEntity<ErrorResponse> handleTrackNotFound(TrackNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("TRACK_NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler({PhotoTypeNotSupportedException.class, PhotoTooLargeException.class,
			PhotoQuotaExceededException.class, PhotoInvalidException.class})
	ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
		String code = switch (e) {
			case PhotoTypeNotSupportedException ex -> "PHOTO_TYPE_NOT_SUPPORTED";
			case PhotoTooLargeException ex -> "PHOTO_TOO_LARGE";
			case PhotoQuotaExceededException ex -> "PHOTO_QUOTA_EXCEEDED";
			default -> "PHOTO_INVALID";
		};
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(code, e.getMessage()));
	}

	@ExceptionHandler(CatalogUnavailableException.class)
	ResponseEntity<ErrorResponse> handleCatalogDown(CatalogUnavailableException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("CATALOG_UNAVAILABLE", "Katalog sedang tidak tersedia."));
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
