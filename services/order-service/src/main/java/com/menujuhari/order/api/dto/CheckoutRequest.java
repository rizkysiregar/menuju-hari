package com.menujuhari.order.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
		@NotNull(message = "invitationId wajib diisi.")
		UUID invitationId,

		@NotBlank(message = "packageCode wajib diisi.")
		@Size(max = 32, message = "packageCode maksimal 32 karakter.")
		String packageCode,

		@Size(max = 64, message = "couponCode maksimal 64 karakter.")
		String couponCode) {
}
