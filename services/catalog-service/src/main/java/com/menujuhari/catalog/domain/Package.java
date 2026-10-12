package com.menujuhari.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "packages")
public class Package {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, length = 32)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(name = "price_idr", nullable = false)
	private int priceIdr;

	/** Feature flags yang membaca UI editor & renderer (TPL-003). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String featureFlags;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Package() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public int getPriceIdr() {
		return priceIdr;
	}

	public String getFeatureFlags() {
		return featureFlags;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return active;
	}
}
