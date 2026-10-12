package com.menujuhari.payment.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {
}
