package com.menujuhari.payment.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.menujuhari.payment.infra.config.MidtransProperties;

/**
 * Integrasi Midtrans (PAY-001):
 * - Snap: POST {snapBaseUrl}/snap/v1/transactions, Authorization Basic Base64(serverKey + ":")
 * - Status: GET {apiBaseUrl}/v2/{order_id}/status
 * Sumber: docs.midtrans.com (diverifikasi 12 Okt 2026).
 */
@Component
public class HttpMidtransGateway implements MidtransGateway {

	private final RestClient snapRest;
	private final RestClient apiRest;
	private final String serverKey;

	public HttpMidtransGateway(MidtransProperties props) {
		String auth = Base64.getEncoder()
				.encodeToString((props.serverKey() + ":").getBytes(StandardCharsets.UTF_8));
		this.serverKey = props.serverKey();
		this.snapRest = RestClient.builder()
				.baseUrl(props.snapBaseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
		this.apiRest = RestClient.builder()
				.baseUrl(props.apiBaseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public SnapTransaction createSnapTransaction(String gatewayOrderId, int grossAmountIdr, String itemName,
			String customerEmail) {
		Map<String, Object> payload = Map.of(
				"transaction_details", Map.of(
						"order_id", gatewayOrderId,
						"gross_amount", grossAmountIdr),
				"item_details", java.util.List.of(Map.of(
						"id", gatewayOrderId,
						"price", grossAmountIdr,
						"quantity", 1,
						"name", itemName)),
				"customer_details", Map.of(
						"email", customerEmail == null ? "" : customerEmail),
				"expiry", Map.of(
						"unit", "hour",
						"duration", 24));

		try {
			Map<String, Object> response = snapRest.post()
					.uri("/snap/v1/transactions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (response == null || response.get("token") == null || response.get("redirect_url") == null) {
				throw new GatewayException("Respons Snap tidak lengkap");
			}
			return new SnapTransaction(String.valueOf(response.get("token")),
					String.valueOf(response.get("redirect_url")));
		}
		catch (Exception e) {
			throw new GatewayException("Gagal membuat transaksi Snap: " + e.getMessage(), e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public TransactionStatus getStatus(String gatewayOrderId) {
		try {
			Map<String, Object> response = apiRest.get()
					.uri("/v2/{orderId}/status", gatewayOrderId)
					.retrieve()
					.body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (response == null) {
				throw new GatewayException("Respons status kosong");
			}
			return new TransactionStatus(
					String.valueOf(response.getOrDefault("transaction_status", "")),
					response.get("fraud_status") == null ? null : String.valueOf(response.get("fraud_status")),
					String.valueOf(response.getOrDefault("status_code", "")),
					String.valueOf(response.getOrDefault("gross_amount", "")),
					response.get("payment_type") == null ? null : String.valueOf(response.get("payment_type")));
		}
		catch (Exception e) {
			throw new GatewayException("Gagal membaca status: " + e.getMessage(), e);
		}
	}

	/** Verifikasi signature_key = SHA512(order_id + status_code + gross_amount + serverKey). */
	@Override
	public boolean verifySignature(String orderId, String statusCode, String grossAmount, String signatureKey) {
		return MidtransSignature.matches(orderId, statusCode, grossAmount, serverKey, signatureKey);
	}

	/** "24000.00" -> 24000 (format gross_amount Midtrans punya 2 desimal). */
	public static long parseGrossAmount(String grossAmount) {
		if (grossAmount == null || grossAmount.isBlank()) {
			return -1;
		}
		try {
			return new java.math.BigDecimal(grossAmount).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	public static class GatewayException extends RuntimeException {
		public GatewayException(String message) {
			super(message);
		}

		public GatewayException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
