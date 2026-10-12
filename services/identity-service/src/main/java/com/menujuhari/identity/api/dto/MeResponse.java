package com.menujuhari.identity.api.dto;

import com.menujuhari.identity.domain.user.User;

public record MeResponse(String id, String email, String role) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId().toString(), user.getEmail(), user.getRole().name());
	}
}
