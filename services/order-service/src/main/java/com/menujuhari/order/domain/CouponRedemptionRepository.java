package com.menujuhari.order.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

	long countByCouponId(UUID couponId);

	long countByCouponIdAndUserId(UUID couponId, UUID userId);

	void deleteByOrderId(UUID orderId);
}
