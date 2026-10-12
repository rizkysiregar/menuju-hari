package com.menujuhari.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class HttpPaymentClient implements PaymentClient {

	private final RestClient rest;

	public HttpPaymentClient(
			@Value("${app.order.payment-base-url:http://localhost:8086}") String baseUrl) {
		org.springframework.http.client.SimpleClientHttpRequestFactory factory =
				new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5_000);
		factory.setReadTimeout(10_000);
		this.rest = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
	}

	@Override
	public PaymentSession createSession(String orderNumber, int grossAmountIdr, String itemName,
			String customerEmail) {
		try {
			PaymentSession session = rest.post()
					.uri("/internal/payments/sessions")
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.body(Map.of(
							"orderNumber", orderNumber,
							"grossAmountIdr", grossAmountIdr,
							"itemName", itemName,
							"customerEmail", customerEmail == null ? "" : customerEmail))
					.retrieve()
					.body(PaymentSession.class);
			if (session == null || session.redirectUrl() == null) {
				throw new PaymentUnavailableException();
			}
			return session;
		}
		catch (RestClientException e) {
			throw new PaymentUnavailableException();
		}
	}

	public static class PaymentUnavailableException extends RuntimeException {
		public PaymentUnavailableException() {
			super("Layanan pembayaran sedang tidak tersedia");
		}
	}
}
