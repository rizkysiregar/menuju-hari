package com.menujuhari.shared.security;

/**
 * Principal sesi yang dipasang ke SecurityContext.
 * Dipakai controller via @AuthenticationPrincipal.
 */
public record SessionPrincipal(java.util.UUID userId, String email, String role) {
}
