package com.menujuhari.payment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

	List<Payment> findByOrderNumberOrderByAttemptDesc(String orderNumber);

	List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, java.time.Instant cutoff);
}
