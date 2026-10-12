package com.menujuhari.order.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Jejak penukaran kupon — sumber hitungan batas global & per-user. */
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coupon_id", nullable = false)
	private Coupon coupon;

	@Column(name = "order_id", nullable = false)
	private UUID orderId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "discount_idr", nullable = false)
	private int discountIdr;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CouponRedemption() {
	}

	public static CouponRedemption of(Coupon coupon, UUID orderId, UUID userId, int discountIdr) {
		CouponRedemption redemption = new CouponRedemption();
		redemption.coupon = coupon;
		redemption.orderId = orderId;
		redemption.userId = userId;
		redemption.discountIdr = discountIdr;
		return redemption;
	}

	void setCoupon(Coupon coupon) {
		this.coupon = coupon;
	}

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getUserId() {
		return userId;
	}

	public int getDiscountIdr() {
		return discountIdr;
	}
}
