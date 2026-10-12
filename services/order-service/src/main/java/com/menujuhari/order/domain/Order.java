package com.menujuhari.order.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(name = "order_number", nullable = false, length = 64)
	private String orderNumber;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "invitation_id", nullable = false)
	private UUID invitationId;

	@Column(name = "customer_email", nullable = false, length = 255)
	private String customerEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "subtotal_idr", nullable = false)
	private int subtotalIdr;

	@Column(name = "discount_idr", nullable = false)
	private int discountIdr;

	@Column(name = "total_idr", nullable = false)
	private int totalIdr;

	@Column(name = "coupon_code", length = 64)
	private String couponCode;

	@Column(name = "idempotency_key", length = 64)
	private String idempotencyKey;

	@Column(name = "paid_at")
	private Instant paidAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<OrderItem> items = new ArrayList<>();

	protected Order() {
	}

	public static Order newPending(String orderNumber, UUID userId, UUID invitationId, String customerEmail,
			int subtotalIdr, int discountIdr, String couponCode, String idempotencyKey) {
		Order order = new Order();
		order.orderNumber = orderNumber;
		order.userId = userId;
		order.invitationId = invitationId;
		order.customerEmail = customerEmail;
		order.status = OrderStatus.PENDING;
		order.subtotalIdr = subtotalIdr;
		order.discountIdr = discountIdr;
		order.totalIdr = subtotalIdr - discountIdr;
		order.couponCode = couponCode;
		order.idempotencyKey = idempotencyKey;
		return order;
	}

	public void addItem(OrderItem item) {
		item.setOrder(this);
		this.items.add(item);
	}

	public void markPaid(Instant paidAt) {
		this.status = OrderStatus.PAID;
		this.paidAt = paidAt;
	}

	public void markExpired() {
		this.status = OrderStatus.EXPIRED;
	}

	public void markCancelled() {
		this.status = OrderStatus.CANCELLED;
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

	public String getOrderNumber() {
		return orderNumber;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getInvitationId() {
		return invitationId;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public int getSubtotalIdr() {
		return subtotalIdr;
	}

	public int getDiscountIdr() {
		return discountIdr;
	}

	public int getTotalIdr() {
		return totalIdr;
	}

	public String getCouponCode() {
		return couponCode;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getPaidAt() {
		return paidAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<OrderItem> getItems() {
		return items;
	}
}
