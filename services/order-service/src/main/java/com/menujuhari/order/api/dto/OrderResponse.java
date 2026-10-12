package com.menujuhari.order.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.menujuhari.order.domain.Order;
import com.menujuhari.order.domain.OrderItem;

public record OrderResponse(
		UUID id,
		String orderNumber,
		UUID invitationId,
		String status,
		int subtotalIdr,
		int discountIdr,
		int totalIdr,
		String couponCode,
		List<Item> items,
		Instant paidAt,
		Instant createdAt) {

	public record Item(String kind, String refCode, String name, int unitPriceIdr, int quantity) {
	}

	public static OrderResponse from(Order order) {
		return new OrderResponse(order.getId(), order.getOrderNumber(), order.getInvitationId(),
				order.getStatus().name(), order.getSubtotalIdr(), order.getDiscountIdr(), order.getTotalIdr(),
				order.getCouponCode(),
				order.getItems().stream()
						.map(item -> new Item(item.getKind().name(), item.getRefCode(), item.getNameSnapshot(),
								item.getUnitPriceIdr(), item.getQuantity()))
						.toList(),
				order.getPaidAt(), order.getCreatedAt());
	}
}
