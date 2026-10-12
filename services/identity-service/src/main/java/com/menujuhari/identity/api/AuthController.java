package com.menujuhari.identity.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.identity.api.dto.MeResponse;
import com.menujuhari.identity.api.dto.OtpRequestRequest;
import com.menujuhari.identity.api.dto.OtpVerifyRequest;
import com.menujuhari.identity.application.OtpService;
import com.menujuhari.shared.security.SessionCookieFactory;
import com.menujuhari.shared.security.SessionPrincipal;
import com.menujuhari.shared.security.SessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

	private static final String GENERIC_OTP_RESPONSE = "Jika email valid, kode OTP telah dikirim. Periksa kotak masuk Anda.";

	private final OtpService otpService;
	private final SessionService sessionService;
	private final SessionCookieFactory cookies;

	public AuthController(OtpService otpService, SessionService sessionService, SessionCookieFactory cookies) {
		this.otpService = otpService;
		this.sessionService = sessionService;
		this.cookies = cookies;
	}

	@PostMapping("/otp/request")
	public ResponseEntity<GenericMessageResponse> requestOtp(@Valid @RequestBody OtpRequestRequest request,
			HttpServletRequest http) {
		otpService.requestOtp(request.email(), ClientIp.resolve(http));
		// Respons selalu generik — mencegah enumerasi akun (AUTH-001).
		return ResponseEntity.accepted().body(new GenericMessageResponse(GENERIC_OTP_RESPONSE));
	}

	@PostMapping("/otp/verify")
	public ResponseEntity<MeResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		OtpService.VerifyResult result = otpService.verify(request.email(), request.code());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.auth(result.sessionToken()).toString())
				.body(MeResponse.from(result.user()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = "${mh.security.cookie-name:mh_session}", required = false) String token,
			HttpServletResponse response) {
		if (token != null && !token.isBlank()) {
			sessionService.delete(token);
		}
		response.addHeader(HttpHeaders.SET_COOKIE, cookies.expired().toString());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public ResponseEntity<MeResponse> me(@AuthenticationPrincipal SessionPrincipal principal) {
		return ResponseEntity.ok(new MeResponse(principal.userId().toString(), principal.email(), principal.role()));
	}

	public record GenericMessageResponse(String message) {
	}
}
