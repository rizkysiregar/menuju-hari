package com.menujuhari.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
		@NotBlank(message = "Email wajib diisi.")
		@Email(message = "Format email tidak valid.")
		String email,

		@NotBlank(message = "Kode OTP wajib diisi.")
		@Pattern(regexp = "\\d{6}", message = "Kode OTP harus 6 digit angka.")
		String code) {
}
