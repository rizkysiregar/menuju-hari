package com.menujuhari.shared.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mitigasi CSRF (PROJECT_PLAN §11.2): untuk metode mutasi, jika browser
 * mengirim header Origin maka origin harus termasuk daftar diizinkan.
 * Klien non-browser (curl, webhook) tanpa header Origin tetap lolos.
 */
public class OriginCheckFilter extends OncePerRequestFilter {

	private final Set<String> allowedOrigins;

	public OriginCheckFilter(SecuritySharedProperties props) {
		this.allowedOrigins = Set.copyOf(props.allowedOrigins());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String method = request.getMethod();
		boolean unsafe = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
		String origin = request.getHeader("Origin");
		if (unsafe && origin != null && !allowedOrigins.contains(origin)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/json");
			response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Origin tidak diizinkan.\"}");
			return;
		}
		chain.doFilter(request, response);
	}
}
