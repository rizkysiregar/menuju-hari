package com.menujuhari.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HexFormat;

/**
 * Util signature & nominal Midtrans.
 * signature_key = SHA512(order_id + status_code + gross_amount + ServerKey)
 * gross_amount berformat string 2 desimal ("49000.00").
 * Sumber: docs.midtrans.com/reference/handle-notifications.
 */
public final class MidtransSignature {

	private MidtransSignature() {
	}

	public static String compute(String orderId, String statusCode, String grossAmount, String serverKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			byte[] hashed = digest.digest(
					(orderId + statusCode + grossAmount + serverKey).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-512 tidak tersedia", e);
		}
	}

	public static boolean matches(String orderId, String statusCode, String grossAmount, String serverKey,
			String signatureKey) {
		if (orderId == null || statusCode == null || grossAmount == null || signatureKey == null) {
			return false;
		}
		return compute(orderId, statusCode, grossAmount, serverKey).equalsIgnoreCase(signatureKey);
	}

	/** "24000.00" -> 24000 */
	public static long parseGrossAmount(String grossAmount) {
		if (grossAmount == null || grossAmount.isBlank()) {
			return -1;
		}
		try {
			return new BigDecimal(grossAmount).setScale(0, RoundingMode.HALF_UP).longValue();
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}
}
