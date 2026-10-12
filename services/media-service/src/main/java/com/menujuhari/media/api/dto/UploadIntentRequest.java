package com.menujuhari.media.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UploadIntentRequest(
		UUID invitationId,

		@NotBlank(message = "Nama file wajib diisi.")
		@Size(max = 255, message = "Nama file maksimal 255 karakter.")
		String filename,

		@NotBlank(message = "Tipe konten wajib diisi.")
		String contentType,

		@Positive(message = "Ukuran file harus positif.")
		long sizeBytes) {
}
