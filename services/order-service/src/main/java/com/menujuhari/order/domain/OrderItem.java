package com.menujuhari.order.domain;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Snapshot item saat order dibuat (ORD-001): harga TIDAK dibaca ulang
 * dari katalog saat pembayaran — nilai tersimpan adalah sumber kebenaran.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

	public enum Kind {
		PACKAGE, ADDON
	}

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Kind kind;

	@Column(name = "ref_code", nullable = false, length = 64)
	private String refCode;

	@Column(name = "name_snapshot", nullable = false, length = 120)
	private String nameSnapshot;

	@Column(name = "unit_price_idr", nullable = false)
	private int unitPriceIdr;

	@Column(nullable = false)
	private int quantity;

	protected OrderItem() {
	}

	public static OrderItem forPackage(String packageCode, String packageName, int priceIdr) {
		OrderItem item = new OrderItem();
		item.kind = Kind.PACKAGE;
		item.refCode = packageCode;
		item.nameSnapshot = packageName;
		item.unitPriceIdr = priceIdr;
		item.quantity = 1;
		return item;
	}

	void setOrder(Order order) {
		this.order = order;
	}

	public UUID getId() {
		return id;
	}

	public Kind getKind() {
		return kind;
	}

	public String getRefCode() {
		return refCode;
	}

	public String getNameSnapshot() {
		return nameSnapshot;
	}

	public int getUnitPriceIdr() {
		return unitPriceIdr;
	}

	public int getQuantity() {
		return quantity;
	}
}
