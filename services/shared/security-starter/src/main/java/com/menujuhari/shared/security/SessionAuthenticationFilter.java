package com.menujuhari.shared.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentikasi dari cookie sesi opaque. Sesi valid -> principal dipasang;
 * TTL sesi diperbarui (sliding) setiap kali dibaca.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

	private final SessionService sessionService;
	private final String cookieName;

	public SessionAuthenticationFilter(SessionService sessionService, SecuritySharedProperties props) {
		this.sessionService = sessionService;
		this.cookieName = props.cookieName();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = extractToken(request);
		if (token != null) {
			sessionService.get(token).ifPresent(session -> {
				SessionPrincipal principal = new SessionPrincipal(
						java.util.UUID.fromString(session.userId()), session.email(), session.role());
				UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
						.authenticated(principal, null,
								List.of(new SimpleGrantedAuthority("ROLE_" + session.role())));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}
		try {
			chain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private String extractToken(HttpServletRequest request) {
		if (request.getCookies() == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (cookieName.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
