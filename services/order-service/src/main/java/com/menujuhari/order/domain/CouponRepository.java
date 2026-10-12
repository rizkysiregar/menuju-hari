package com.menujuhari.order.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

	Optional<Coupon> findByCodeIgnoreCase(String code);

	/** Lock pessimistik saat checkout agar batas penukaran tidak jebol antar request (CUP-003). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Coupon c where lower(c.code) = lower(:code)")
	Optional<Coupon> lockByCode(@Param("code") String code);
}
