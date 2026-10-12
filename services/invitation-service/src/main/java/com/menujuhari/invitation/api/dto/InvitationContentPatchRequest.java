package com.menujuhari.invitation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Autosave content patch (INV-003/INV-004): membawa contentVersion
 * untuk optimistic concurrency. 409 bila versi sudah usang.
 */
public record InvitationContentPatchRequest(
		@NotNull(message = "Konten wajib diisi.")
		Map<String, Object> content,

		@NotNull(message = "contentVersion wajib diisi.")
		@Min(value = 1, message = "contentVersion minimal 1.")
		Integer contentVersion) {
}
