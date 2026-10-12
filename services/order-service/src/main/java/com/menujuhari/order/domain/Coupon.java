package com.menujuhari.order.domain;

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
import jakarta.persistence.Table;

/**
 * Kupon diskon (CUP-001..003): persen atau nilai tetap, dengan batas
 * jendela waktu, batas global, batas per-user, dan paket yang diizinkan.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

	public enum Kind {
		PERCENT, FIXED
	}

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, length = 64)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Kind kind;

	/** PERCENT: 1-100; FIXED: nilai rupiah. */
	@Column(nullable = false)
	private int value;

	@Column(name = "max_discount_idr")
	private Integer maxDiscountIdr;

	@Column(name = "min_transaction_idr", nullable = false)
	private int minTransactionIdr;

	@Column(name = "max_redemptions")
	private Integer maxRedemptions;

	@Column(name = "max_per_user", nullable = false)
	private int maxPerUser;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	/** Daftar kode paket yang diizinkan; null = semua paket. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "allowed_packages", columnDefinition = "jsonb")
	private String allowedPackages;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Coupon() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public Kind getKind() {
		return kind;
	}

	public int getValue() {
		return value;
	}

	public Integer getMaxDiscountIdr() {
		return maxDiscountIdr;
	}

	public int getMinTransactionIdr() {
		return minTransactionIdr;
	}

	public Integer getMaxRedemptions() {
		return maxRedemptions;
	}

	public int getMaxPerUser() {
		return maxPerUser;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public String getAllowedPackages() {
		return allowedPackages;
	}

	public boolean isActive() {
		return active;
	}
}
