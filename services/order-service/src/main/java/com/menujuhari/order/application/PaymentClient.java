package com.menujuhari.order.application;

/**
 * Kontrak antar-service: order-service → payment-service.
 * Order-service TIDAK menyentuh kredensial gateway sama sekali.
 */
public interface PaymentClient {

	record PaymentSession(String gatewayOrderId, String token, String redirectUrl) {
	}

	PaymentSession createSession(String orderNumber, int grossAmountIdr, String itemName, String customerEmail);
}
