package com.menujuhari.invitation.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH meta (slug / tanggal acara) — field null berarti tidak diubah. */
public record InvitationMetaPatchRequest(
		@Size(max = 160, message = "Slug maksimal 160 karakter.")
		@Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug hanya boleh huruf kecil, angka, dan tanda hubung.")
		String slug,

		LocalDate eventDate) {
}
