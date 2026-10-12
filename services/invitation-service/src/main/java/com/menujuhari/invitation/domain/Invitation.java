package com.menujuhari.invitation.domain;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "invitations")
public class Invitation {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "template_code", nullable = false, length = 64)
	private String templateCode;

	@Column(name = "template_version", nullable = false, length = 32)
	private String templateVersion;

	@Column(nullable = false, length = 160)
	private String slug;

	@Column(name = "bride_name", nullable = false, length = 100)
	private String brideName;

	@Column(name = "groom_name", nullable = false, length = 100)
	private String groomName;

	@Column(name = "event_date", nullable = false)
	private LocalDate eventDate;

	/**
	 * Konten terstruktur (JSONB) tervalidasi terhadap skema template (INV-002).
	 * Disimpan sebagai string JSON; skema per template dibawa catalog-service.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String content;

	@Column(name = "content_version", nullable = false)
	private int contentVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InvitationStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Invitation() {
	}

	public static Invitation newDraft(UUID userId, String templateCode, String templateVersion, String slug,
			String brideName, String groomName, LocalDate eventDate) {
		Invitation invitation = new Invitation();
		invitation.userId = userId;
		invitation.templateCode = templateCode;
		invitation.templateVersion = templateVersion;
		invitation.slug = slug;
		invitation.brideName = brideName;
		invitation.groomName = groomName;
		invitation.eventDate = eventDate;
		invitation.content = "{}";
		invitation.contentVersion = 1;
		invitation.status = InvitationStatus.DRAFT;
		return invitation;
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

	public String getTemplateCode() {
		return templateCode;
	}

	public String getTemplateVersion() {
		return templateVersion;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getBrideName() {
		return brideName;
	}

	public String getGroomName() {
		return groomName;
	}

	public LocalDate getEventDate() {
		return eventDate;
	}

	public void setEventDate(LocalDate eventDate) {
		this.eventDate = eventDate;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public int getContentVersion() {
		return contentVersion;
	}

	public void bumpContentVersion() {
		this.contentVersion = this.contentVersion + 1;
	}

	public InvitationStatus getStatus() {
		return status;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	/** Recompute masa aktif saat tanggal acara berubah pada undangan aktif [K3, K4]. */
	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public void archive() {
		this.status = InvitationStatus.ARCHIVED;
		this.archivedAt = Instant.now();
	}

	/** PUB-001: publikasi otomatis setelah pembayaran sukses; masa aktif event_date + 30 hari [K2]. */
	public void publish() {
		this.status = InvitationStatus.PUBLISHED;
		this.publishedAt = Instant.now();
		this.expiresAt = eventDate.plusDays(30).atTime(23, 59, 59)
				.atZone(java.time.ZoneId.of("Asia/Jakarta")).toInstant();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
