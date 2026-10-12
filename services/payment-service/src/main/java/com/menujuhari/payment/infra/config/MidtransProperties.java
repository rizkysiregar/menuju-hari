package com.menujuhari.payment.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kredensial Midtrans TIDAK punya default — wajib lewat .env (tidak pernah di-commit).
 * Sandbox: ServerKey berawalan SB-Mid-server-...; produksi: Mid-server-...
 * Sumber: Dashboard Midtrans -> Settings -> Access Keys.
 */
@ConfigurationProperties(prefix = "midtrans")
public record MidtransProperties(
		Boolean isSandbox,
		String serverKey,
		String clientKey,
		String merchantId) {

	public MidtransProperties {
		// Default sandbox agar dev lokal aman tanpa konfigurasi.
		isSandbox = isSandbox == null || isSandbox;
	}

	public String snapBaseUrl() {
		return isSandbox ? "https://app.sandbox.midtrans.com" : "https://app.midtrans.com";
	}

	public String apiBaseUrl() {
		return isSandbox ? "https://api.sandbox.midtrans.com" : "https://api.midtrans.com";
	}
}
