package com.menujuhari.invitation.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.menujuhari.invitation.domain.Invitation;

/** Ringkasan untuk daftar (tanpa konten — lebih ringan). */
public record InvitationSummaryResponse(
		UUID id,
		String templateCode,
		String slug,
		String brideName,
		String groomName,
		LocalDate eventDate,
		String status,
		int contentVersion,
		Instant updatedAt) {

	public static InvitationSummaryResponse from(Invitation invitation) {
		return new InvitationSummaryResponse(invitation.getId(), invitation.getTemplateCode(),
				invitation.getSlug(), invitation.getBrideName(), invitation.getGroomName(),
				invitation.getEventDate(), invitation.getStatus().name(), invitation.getContentVersion(),
				invitation.getUpdatedAt());
	}
}
