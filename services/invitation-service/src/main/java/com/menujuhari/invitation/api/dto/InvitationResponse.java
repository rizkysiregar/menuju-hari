package com.menujuhari.invitation.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.menujuhari.invitation.domain.Invitation;

public record InvitationResponse(
		UUID id,
		String templateCode,
		String templateVersion,
		String slug,
		String brideName,
		String groomName,
		LocalDate eventDate,
		String status,
		int contentVersion,
		Map<String, Object> content,
		Instant publishedAt,
		Instant expiresAt,
		Instant createdAt,
		Instant updatedAt) {

	public static InvitationResponse from(Invitation invitation, Map<String, Object> content) {
		return new InvitationResponse(invitation.getId(), invitation.getTemplateCode(),
				invitation.getTemplateVersion(), invitation.getSlug(), invitation.getBrideName(),
				invitation.getGroomName(), invitation.getEventDate(), invitation.getStatus().name(),
				invitation.getContentVersion(), content, invitation.getPublishedAt(), invitation.getExpiresAt(),
				invitation.getCreatedAt(), invitation.getUpdatedAt());
	}
}
