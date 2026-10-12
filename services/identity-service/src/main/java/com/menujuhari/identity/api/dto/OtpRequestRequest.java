package com.menujuhari.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpRequestRequest(
		@NotBlank(message = "Email wajib diisi.")
		@Email(message = "Format email tidak valid.")
		String email) {
}
