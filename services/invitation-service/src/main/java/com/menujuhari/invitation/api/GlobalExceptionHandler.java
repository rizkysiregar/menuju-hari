package com.menujuhari.invitation.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.menujuhari.invitation.application.CatalogUnavailableException;
import com.menujuhari.invitation.application.ContentInvalidException;
import com.menujuhari.invitation.application.ContentTooLargeException;
import com.menujuhari.invitation.application.DraftQuotaExceededException;
import com.menujuhari.invitation.application.EventDateInvalidException;
import com.menujuhari.invitation.application.InvitationNotEditableException;
import com.menujuhari.invitation.application.InvitationNotFoundException;
import com.menujuhari.invitation.application.UnknownTemplateException;
import com.menujuhari.invitation.application.SlugAlreadyTakenException;
import com.menujuhari.invitation.application.SlugInvalidException;
import com.menujuhari.invitation.application.VersionConflictException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(InvitationNotFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(InvitationNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse("NOT_FOUND", "Invitation tidak ditemukan."));
	}

	@ExceptionHandler(VersionConflictException.class)
	ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("VERSION_CONFLICT",
						"Konten telah berubah di tempat lain. Muat ulang atau paksa tulis.",
						Map.of("currentVersion", String.valueOf(e.getCurrentVersion()))));
	}

	@ExceptionHandler({SlugAlreadyTakenException.class, InvitationNotEditableException.class})
	ResponseEntity<ErrorResponse> handleConflict(RuntimeException e) {
		String code = e instanceof SlugAlreadyTakenException ? "SLUG_TAKEN" : "NOT_EDITABLE";
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(code, e.getMessage()));
	}

	@ExceptionHandler(CatalogUnavailableException.class)
	ResponseEntity<ErrorResponse> handleCatalogDown(CatalogUnavailableException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ErrorResponse("CATALOG_UNAVAILABLE",
						"Katalog sedang tidak tersedia. Coba lagi beberapa saat."));
	}

	@ExceptionHandler({DraftQuotaExceededException.class, EventDateInvalidException.class,
			SlugInvalidException.class, UnknownTemplateException.class,
			ContentInvalidException.class, ContentTooLargeException.class})
	ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e) {
		String code = switch (e) {
			case DraftQuotaExceededException ex -> "DRAFT_QUOTA_EXCEEDED";
			case EventDateInvalidException ex -> "EVENT_DATE_INVALID";
			case SlugInvalidException ex -> "SLUG_INVALID";
			case UnknownTemplateException ex -> "TEMPLATE_UNKNOWN";
			case ContentTooLargeException ex -> "CONTENT_TOO_LARGE";
			default -> "CONTENT_INVALID";
		};
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse(code, e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new java.util.HashMap<>();
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
