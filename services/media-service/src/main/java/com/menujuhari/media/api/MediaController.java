package com.menujuhari.media.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.media.api.dto.PhotoResponse;
import com.menujuhari.media.api.dto.UploadIntentRequest;
import com.menujuhari.media.application.MediaService;
import com.menujuhari.media.application.MinioStorage;
import com.menujuhari.media.application.MusicCatalogClient;
import com.menujuhari.media.domain.MediaAsset;
import com.menujuhari.media.domain.MediaStatus;
import com.menujuhari.media.infra.config.MediaProperties;
import com.menujuhari.shared.security.SessionPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/media")
public class MediaController {

	private final MediaService mediaService;
	private final MinioStorage storage;
	private final MediaProperties props;

	public MediaController(MediaService mediaService, MinioStorage storage, MediaProperties props) {
		this.mediaService = mediaService;
		this.storage = storage;
		this.props = props;
	}

	@PostMapping("/photos/upload-intent")
	public ResponseEntity<MediaService.UploadIntent> uploadIntent(
			@Valid @RequestBody UploadIntentRequest request,
			@AuthenticationPrincipal SessionPrincipal principal) {
		MediaService.UploadIntent intent = mediaService.createUploadIntent(principal.userId(),
				request.invitationId(), request.filename(), request.contentType(), request.sizeBytes());
		return ResponseEntity.status(HttpStatus.CREATED).body(intent);
	}

	@PostMapping("/photos/{id}/upload-complete")
	public ResponseEntity<PhotoResponse> uploadComplete(@PathVariable UUID id,
			@AuthenticationPrincipal SessionPrincipal principal) {
		MediaAsset asset = mediaService.completeUpload(principal.userId(), id);
		return ResponseEntity.status(asset.getStatus() == MediaStatus.PENDING ? HttpStatus.ACCEPTED
				: HttpStatus.OK).body(toResponse(asset, Map.of()));
	}

	@GetMapping("/photos/{id}")
	public ResponseEntity<PhotoResponse> photo(@PathVariable UUID id,
			@AuthenticationPrincipal SessionPrincipal principal) {
		MediaAsset asset = mediaService.getOwned(principal.userId(), id);
		return ResponseEntity.ok(toResponse(asset, mediaService.variantsWithUrls(asset)));
	}

	@DeleteMapping("/photos/{id}")
	public ResponseEntity<Void> deletePhoto(@PathVariable UUID id,
			@AuthenticationPrincipal SessionPrincipal principal) {
		mediaService.delete(principal.userId(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/music/{trackId}/stream-url")
	public ResponseEntity<MediaService.StreamUrl> musicStream(@PathVariable UUID trackId) {
		return ResponseEntity.ok(mediaService.musicStreamUrl(trackId));
	}

	private PhotoResponse toResponse(MediaAsset asset, Map<String, Object> variants) {
		String originalUrl = asset.getStatus() == MediaStatus.PENDING
				? null
				: storage.presignGet(asset.getObjectKey(), props.presignReadTtl());
		return new PhotoResponse(asset.getId(), asset.getStatus().name(), asset.getContentType(),
				asset.getSizeBytes(), originalUrl, variants, asset.getCreatedAt());
	}
}
