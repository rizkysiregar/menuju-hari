package com.menujuhari.order.application;

/**
 * Kontrak antar-service: order-service → catalog-service (harga paket).
 * Backend = sumber kebenaran harga (ORD-001): snapshot saat checkout.
 */
public interface CatalogClient {

	record PackageInfo(String code, String name, int priceIdr, boolean active) {
	}

	PackageInfo getPackage(String code);
}
