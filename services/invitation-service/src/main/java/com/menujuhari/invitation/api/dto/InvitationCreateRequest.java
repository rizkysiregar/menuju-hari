package com.menujuhari.invitation.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitationCreateRequest(
		@NotBlank(message = "Template wajib dipilih.")
		String templateCode,

		@NotBlank(message = "Nama pengantin perempuan wajib diisi.")
		@Size(max = 100, message = "Nama pengantin perempuan maksimal 100 karakter.")
		String brideName,

		@NotBlank(message = "Nama pengantin laki-laki wajib diisi.")
		@Size(max = 100, message = "Nama pengantin laki-laki maksimal 100 karakter.")
		String groomName,

		@NotNull(message = "Tanggal acara wajib diisi.")
		LocalDate eventDate) {
}
