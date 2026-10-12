package com.menujuhari.media.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "invitation_id")
	private UUID invitationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MediaKind kind;

	@Column(name = "object_key", nullable = false, length = 300)
	private String objectKey;

	@Column(name = "original_filename")
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 50)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MediaStatus status;

	/** {"web": {"key":..., "width":..., "height":..., "contentType":...}, "thumb": {...}} */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String variants;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MediaAsset() {
	}

	public static MediaAsset pendingPhoto(UUID userId, UUID invitationId, String objectKey,
			String originalFilename, String contentType, long sizeBytes) {
		MediaAsset asset = new MediaAsset();
		asset.userId = userId;
		asset.invitationId = invitationId;
		asset.kind = MediaKind.PHOTO;
		asset.objectKey = objectKey;
		asset.originalFilename = originalFilename;
		asset.contentType = contentType;
		asset.sizeBytes = sizeBytes;
		asset.status = MediaStatus.PENDING;
		return asset;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getInvitationId() {
		return invitationId;
	}

	public MediaKind getKind() {
		return kind;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public MediaStatus getStatus() {
		return status;
	}

	public void markFailed() {
		this.status = MediaStatus.FAILED;
	}

	public void markReady(String variantsJson) {
		this.status = MediaStatus.READY;
		this.variants = variantsJson;
	}

	/** Object key baru setelah mediaId diketahui (dipanggil dalam transaksi). */
	public void assignObjectKey(String objectKey) {
		this.objectKey = objectKey;
	}

	/** Ukuran aktual hasil verifikasi storage. */
	public void updateSize(long sizeBytes) {
		this.sizeBytes = sizeBytes;
	}

	public String getVariants() {
		return variants;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
