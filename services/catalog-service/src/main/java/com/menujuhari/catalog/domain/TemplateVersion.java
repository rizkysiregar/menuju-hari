package com.menujuhari.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "template_versions")
public class TemplateVersion {

	public enum Status {
		ACTIVE, RETIRED
	}

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "template_id", nullable = false)
	private Template template;

	@Column(nullable = false, length = 32)
	private String version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	/** JSON Schema konten undangan — sumber validasi invitation-service (INV-002). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String contentSchema;

	/** Theme tokens: palet warna & font preset (renderran oleh frontend). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String theme;

	/** Kapabilitas section yang dirender template. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String capabilityFlags;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TemplateVersion() {
	}

	public UUID getId() {
		return id;
	}

	public Template getTemplate() {
		return template;
	}

	public String getVersion() {
		return version;
	}

	public Status getStatus() {
		return status;
	}

	public String getContentSchema() {
		return contentSchema;
	}

	public String getTheme() {
		return theme;
	}

	public String getCapabilityFlags() {
		return capabilityFlags;
	}
}
