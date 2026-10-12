package com.menujuhari.media.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.media.domain.MediaAsset;
import com.menujuhari.media.domain.MediaAssetRepository;
import com.menujuhari.media.domain.MediaKind;
import com.menujuhari.media.domain.MediaStatus;
import com.menujuhari.media.infra.config.MediaProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Logika media sesuai MEDIA-001..005 di PROJECT_PLAN.md:
 * presigned upload, verifikasi isi (magic bytes), proses varian async,
 * otorisasi kepemilikan, cleanup, dan streaming musik via signed URL.
 */
@Service
public class MediaService {

	private static final Logger log = LoggerFactory.getLogger(MediaService.class);
	private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png");
	private static final Duration MUSIC_STREAM_TTL = Duration.ofMinutes(10);

	private final MediaAssetRepository repository;
	private final MinioStorage storage;
	private final ImageProcessor imageProcessor;
	private final MusicCatalogClient musicCatalogClient;
	private final MediaProperties props;
	private final ObjectMapper objectMapper;

	public MediaService(MediaAssetRepository repository, MinioStorage storage, ImageProcessor imageProcessor,
			MusicCatalogClient musicCatalogClient, MediaProperties props, ObjectMapper objectMapper) {
		this.repository = repository;
		this.storage = storage;
		this.imageProcessor = imageProcessor;
		this.musicCatalogClient = musicCatalogClient;
		this.props = props;
		this.objectMapper = objectMapper;
	}

	public record UploadIntent(UUID mediaId, String uploadUrl, String objectKey, long expiresInSeconds) {
	}

	@Transactional
	public UploadIntent createUploadIntent(UUID userId, UUID invitationId, String filename, String contentType,
			long sizeBytes) {
		String normalizedType = contentType == null ? "" : contentType.toLowerCase();
		if (!ALLOWED_CONTENT_TYPES.contains(normalizedType)) {
			throw new PhotoTypeNotSupportedException();
		}
		if (sizeBytes <= 0 || sizeBytes > props.maxSizeBytes()) {
			throw new PhotoTooLargeException();
		}
		long activePhotos = repository.countByUserIdAndKindAndStatusIn(userId, MediaKind.PHOTO,
				List.of(MediaStatus.PENDING, MediaStatus.READY));
		if (activePhotos >= props.maxPhotosPerUser()) {
			throw new PhotoQuotaExceededException();
		}

		String extension = normalizedType.contains("png") ? "png" : "jpg";
		// Row dibuat dulu supaya mediaId jadi bagian dari object key.
		MediaAsset asset = repository.save(MediaAsset.pendingPhoto(userId, invitationId, "placeholder",
				filename, normalizedType, sizeBytes));
		asset.assignObjectKey("photos/%s/%s/original.%s".formatted(userId, asset.getId(), extension));
		repository.save(asset);

		String uploadUrl = storage.presignPut(asset.getObjectKey(), props.presignUploadTtl());
		log.info("Upload intent foto: {} (user {})", asset.getId(), userId);
		return new UploadIntent(asset.getId(), uploadUrl, asset.getObjectKey(),
				props.presignUploadTtl().toSeconds());
	}

	@Transactional
	public MediaAsset completeUpload(UUID userId, UUID mediaId) {
		MediaAsset asset = getOwned(userId, mediaId);
		if (asset.getStatus() == MediaStatus.READY) {
			return asset; // idempoten
		}
		if (!storage.exists(asset.getObjectKey())) {
			asset.markFailed();
			throw new PhotoInvalidException();
		}
		long actualSize = storage.size(asset.getObjectKey());
		if (actualSize > props.maxSizeBytes()) {
			asset.markFailed();
			throw new PhotoTooLargeException();
		}
		byte[] bytes = storage.download(asset.getObjectKey());
		boolean matches = (asset.getContentType().contains("png") && ImageProcessor.isPng(bytes))
				|| (asset.getContentType().contains("jpeg") && ImageProcessor.isJpeg(bytes));
		if (!matches) {
			asset.markFailed();
			throw new PhotoInvalidException();
		}

		asset.updateSize(actualSize);
		repository.save(asset);
		processAsync(asset.getId(), bytes);
		return asset;
	}

	/** Proses varian async (MEDIA-002). Kegagalan -> status FAILED, bukan crash. */
	@Async("mediaExecutor")
	public void processAsync(UUID assetId, byte[] original) {
		try {
			MediaAsset asset = repository.findById(assetId).orElseThrow(MediaNotFoundException::new);
			ImageProcessor.Result result = imageProcessor.process(original, asset.getContentType());
			String base = asset.getObjectKey().replaceAll("/original\\.[a-z]+$", "");
			String webKey = base + "/web." + (result.web().contentType().contains("png") ? "png" : "jpg");
			String thumbKey = base + "/thumb." + (result.thumb().contentType().contains("png") ? "png" : "jpg");
			storage.upload(webKey, result.web().bytes(), result.web().contentType());
			storage.upload(thumbKey, result.thumb().bytes(), result.thumb().contentType());

			Map<String, Object> variants = Map.of(
					"web", ImageProcessor.variantMap(webKey, result.web().contentType(),
							result.web().width(), result.web().height()),
					"thumb", ImageProcessor.variantMap(thumbKey, result.thumb().contentType(),
							result.thumb().width(), result.thumb().height()));

			asset.markReady(objectMapper.writeValueAsString(variants));
			repository.save(asset);
			log.info("Varian foto siap: {} (web {} px)", assetId, result.web().width());
		}
		catch (Exception e) {
			log.error("Proses gambar gagal untuk media {}", assetId, e);
			repository.findById(assetId).ifPresent(asset -> {
				asset.markFailed();
				repository.save(asset);
			});
		}
	}

	public MediaAsset getOwned(UUID userId, UUID mediaId) {
		return repository.findByIdAndUserId(mediaId, userId).orElseThrow(MediaNotFoundException::new);
	}

	public Map<String, Object> variantsWithUrls(MediaAsset asset) {
		if (asset.getVariants() == null) {
			return Map.of();
		}
		Map<String, Object> variants = objectMapper.readValue(asset.getVariants(),
				new TypeReference<Map<String, Object>>() {
				});
		for (Map.Entry<String, Object> entry : variants.entrySet()) {
			if (entry.getValue() instanceof Map<?, ?> raw) {
				Map<String, Object> variant = new java.util.LinkedHashMap<>();
				raw.forEach((key, value) -> variant.put(String.valueOf(key), value));
				if (variant.get("key") instanceof String key) {
					variant.put("url", storage.presignGet(key, props.presignReadTtl()));
				}
				variants.put(entry.getKey(), variant);
			}
		}
		return variants;
	}

	public record StreamUrl(String url, long expiresInSeconds) {
	}

	public StreamUrl musicStreamUrl(UUID trackId) {
		MusicCatalogClient.TrackInfo track = musicCatalogClient.getTrack(trackId);
		String url = storage.presignGet(track.objectKey(), MUSIC_STREAM_TTL);
		return new StreamUrl(url, MUSIC_STREAM_TTL.toSeconds());
	}

	@Transactional
	public void delete(UUID userId, UUID mediaId) {
		MediaAsset asset = getOwned(userId, mediaId);
		storage.delete(asset.getObjectKey());
		if (asset.getVariants() != null) {
			Map<String, Object> variants = objectMapper.readValue(asset.getVariants(),
					new TypeReference<Map<String, Object>>() {
					});
			variants.values().forEach(value -> {
				if (value instanceof Map<?, ?> map && map.get("key") instanceof String key) {
					try {
						storage.delete(key);
					}
					catch (Exception e) {
						log.warn("Gagal hapus varian {} (dilanjutkan)", key);
					}
				}
			});
		}
		repository.delete(asset);
	}

	/** Dipakai cleanup job (MEDIA-004). */
	public int cleanupExpired(Instant pendingCutoff, Instant failedCutoff) {
		int removed = 0;
		for (MediaAsset asset : repository.findByStatusAndCreatedAtBefore(MediaStatus.PENDING, pendingCutoff)) {
			try {
				storage.delete(asset.getObjectKey());
			}
			catch (Exception e) {
				log.warn("Cleanup: objek {} sudah tidak ada (dilanjutkan)", asset.getObjectKey());
			}
			repository.delete(asset);
			removed++;
		}
		for (MediaAsset asset : repository.findByStatusAndCreatedAtBefore(MediaStatus.FAILED, failedCutoff)) {
			try {
				storage.delete(asset.getObjectKey());
			}
			catch (Exception e) {
				// sudah tidak ada
			}
			repository.delete(asset);
			removed++;
		}
		return removed;
	}
}
