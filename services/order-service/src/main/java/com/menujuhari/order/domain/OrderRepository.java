package com.menujuhari.order.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	Optional<Order> findByOrderNumber(String orderNumber);

	Optional<Order> findByOrderNumberAndUserId(String orderNumber, UUID userId);

	Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, java.time.Instant cutoff);
}
