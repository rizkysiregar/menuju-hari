package com.menujuhari.payment.application;

/**
 * Koneksi ke Midtrans. Implementasi produksi: HttpMidtransGateway
 * (Basic auth Base64(serverKey+":"), docs.midtrans.com).
 */
public interface MidtransGateway {

	record SnapTransaction(String token, String redirectUrl) {
	}

	record TransactionStatus(
			String transactionStatus,
			String fraudStatus,
			String statusCode,
			String grossAmount,
			String paymentType) {
	}

	/** POST /snap/v1/transactions (Basic auth ServerKey). */
	SnapTransaction createSnapTransaction(String gatewayOrderId, int grossAmountIdr, String itemName,
			String customerEmail);

	/** GET /v2/{order_id}/status — untuk rekonsiliasi (PAY-003). */
	TransactionStatus getStatus(String gatewayOrderId);

	/**
	 * Verifikasi signature webhook:
	 * SHA512(order_id + status_code + gross_amount + ServerKey).
	 */
	boolean verifySignature(String orderId, String statusCode, String grossAmount, String signatureKey);
}
