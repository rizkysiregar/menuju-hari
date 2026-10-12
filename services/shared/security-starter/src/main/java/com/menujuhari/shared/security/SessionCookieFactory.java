package com.menujuhari.shared.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * Pabrik cookie sesi: httpOnly + Secure + SameSite=Lax (AUTH-003).
 */
public class SessionCookieFactory {

	private final SecuritySharedProperties props;

	public SessionCookieFactory(SecuritySharedProperties props) {
		this.props = props;
	}

	public ResponseCookie auth(String token) {
		return ResponseCookie.from(props.cookieName(), token)
				.httpOnly(true)
				.secure(props.cookieSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(props.sessionTtl())
				.build();
	}

	public ResponseCookie expired() {
		return ResponseCookie.from(props.cookieName(), "")
				.httpOnly(true)
				.secure(props.cookieSecure())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
	}
}
