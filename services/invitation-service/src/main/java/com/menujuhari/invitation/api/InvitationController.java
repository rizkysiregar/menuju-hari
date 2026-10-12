package com.menujuhari.invitation.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.invitation.api.dto.InvitationContentPatchRequest;
import com.menujuhari.invitation.api.dto.InvitationCreateRequest;
import com.menujuhari.invitation.api.dto.InvitationMetaPatchRequest;
import com.menujuhari.invitation.api.dto.InvitationResponse;
import com.menujuhari.invitation.api.dto.InvitationSummaryResponse;
import com.menujuhari.invitation.application.InvitationService;
import com.menujuhari.invitation.domain.Invitation;
import com.menujuhari.invitation.domain.InvitationStatus;
import com.menujuhari.shared.security.SessionPrincipal;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/invitations")
public class InvitationController {

	private final InvitationService invitationService;

	public InvitationController(InvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping
	public ResponseEntity<InvitationResponse> create(@Valid @RequestBody InvitationCreateRequest request,
			@AuthenticationPrincipal SessionPrincipal principal) {
		Invitation created = invitationService.create(principal.userId(), request.templateCode(),
				request.brideName(), request.groomName(), request.eventDate());
		return ResponseEntity
				.created(URI.create("/v1/invitations/" + created.getId()))
				.body(InvitationResponse.from(created, invitationService.parseContent(created)));
	}

	@GetMapping
	public Page<InvitationSummaryResponse> list(
			@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) InvitationStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return invitationService.list(principal.userId(), status, page, size)
				.map(InvitationSummaryResponse::from);
	}

	@GetMapping("/{id}")
	public ResponseEntity<InvitationResponse> get(@PathVariable UUID id,
			@AuthenticationPrincipal SessionPrincipal principal) {
		Invitation invitation = invitationService.getOwned(principal.userId(), id);
		return ResponseEntity.ok(InvitationResponse.from(invitation, invitationService.parseContent(invitation)));
	}

	@PatchMapping("/{id}")
	public ResponseEntity<InvitationResponse> patchMeta(@PathVariable UUID id,
			@Valid @RequestBody InvitationMetaPatchRequest request,
			@AuthenticationPrincipal SessionPrincipal principal) {
		Invitation invitation = invitationService.patchMeta(principal.userId(), id, request.slug(),
				request.eventDate());
		return ResponseEntity.ok(InvitationResponse.from(invitation, invitationService.parseContent(invitation)));
	}

	@PatchMapping("/{id}/content")
	public ResponseEntity<InvitationResponse> patchContent(@PathVariable UUID id,
			@Valid @RequestBody InvitationContentPatchRequest request,
			@AuthenticationPrincipal SessionPrincipal principal) {
		Invitation invitation = invitationService.patchContent(principal.userId(), id, request.content(),
				request.contentVersion());
		return ResponseEntity.ok(InvitationResponse.from(invitation, invitationService.parseContent(invitation)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id,
			@AuthenticationPrincipal SessionPrincipal principal) {
		invitationService.delete(principal.userId(), id);
		return ResponseEntity.noContent().build();
	}
}
