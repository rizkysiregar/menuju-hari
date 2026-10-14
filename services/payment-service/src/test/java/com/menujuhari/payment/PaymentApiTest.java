package com.menujuhari.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.menujuhari.payment.application.PaymentService;
import com.menujuhari.payment.application.MidtransGateway;
import com.menujuhari.payment.application.MidtransSignature;
import com.menujuhari.payment.domain.Payment;

/**
 * E2E payment-service (Testcontainers): session Snap (stub gateway),
 * webhook signature SHA512, idempotensi duplikat & out-of-order,
 * penolakan nominal beda, dan inquiry rekonsiliasi.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentApiTest {

	static final String STUB_SERVER_KEY = "SB-Mid-server-TESTKEY";
	static final AtomicReference<MidtransGateway.TransactionStatus> STUB_STATUS = new AtomicReference<>(
			new MidtransGateway.TransactionStatus("pending", null, "201", "49000.00", null));

	@Autowired
	TestRestTemplate rest;

	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@Autowired
	com.menujuhari.payment.application.PaymentService paymentService;

	@TestConfiguration
	static class StubGatewayConfig {

		@Bean
		@Primary
		MidtransGateway stubGateway() {
			return new MidtransGateway() {

				@Override
				public SnapTransaction createSnapTransaction(String gatewayOrderId, int grossAmountIdr,
						String itemName, String customerEmail) {
					return new SnapTransaction("token-" + gatewayOrderId, "https://pay.example/" + gatewayOrderId);
				}

				@Override
				public TransactionStatus getStatus(String gatewayOrderId) {
					return STUB_STATUS.get();
				}

				@Override
				public boolean verifySignature(String orderId, String statusCode, String grossAmount,
						String signatureKey) {
					return MidtransSignature.matches(orderId, statusCode, grossAmount, STUB_SERVER_KEY,
							signatureKey);
				}
			};
		}
	}

	private String signature(String orderId, String statusCode, String grossAmount) {
		return MidtransSignature.compute(orderId, statusCode, grossAmount, STUB_SERVER_KEY);
	}

	private ResponseEntity<String> webhook(String orderId, String status, String statusCode, String grossAmount,
			boolean validSignature, String fraudStatus) throws Exception {
		String sig = validSignature ? signature(orderId, statusCode, grossAmount) : "invalid-signature";
		String body = """
				{"order_id":"%s","status_code":"%s","gross_amount":"%s","signature_key":"%s",
				 "transaction_status":"%s","fraud_status":"%s","payment_type":"gopay","merchant_id":"G123"}
				""".formatted(orderId, statusCode, grossAmount, sig, status, fraudStatus);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return rest.exchange("/v1/webhooks/payments/midtrans", HttpMethod.POST,
				new HttpEntity<>(body, headers), String.class);
	}

	private String createOrder(int counter) {
		String orderNumber = "MH-TEST-" + UUID.randomUUID().toString().substring(0, 8) + "-" + counter;
		ResponseEntity<String> created = rest.exchange("/internal/payments/sessions", HttpMethod.POST,
				new HttpEntity<>("{\"orderNumber\":\"" + orderNumber + "\",\"grossAmountIdr\":49000,"
						+ "\"itemName\":\"Silver\",\"customerEmail\":\"bayar@example.com\"}",
						jsonHeaders()), String.class);
		assertThat(created.getStatusCode().value()).isEqualTo(200);
		return orderNumber;
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private long paymentsStreamLength() {
		Long size = redis().opsForStream().size("mh:stream:payments");
		return size == null ? 0 : size;
	}

	@Autowired
	org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	private StringRedisTemplate redis() {
		return redisTemplate;
	}

	@Test
	@Order(1)
	void createSessionDanReuse() {
		String orderNumber = createOrder(1);
		// pay ulang order yang sama -> token & gatewayOrderId SAMA (reuse PENDING)
		ResponseEntity<String> again = rest.exchange("/internal/payments/sessions", HttpMethod.POST,
				new HttpEntity<>("{\"orderNumber\":\"" + orderNumber + "\",\"grossAmountIdr\":49000,"
						+ "\"itemName\":\"Silver\",\"customerEmail\":\"bayar@example.com\"}", jsonHeaders()),
				String.class);
		assertThat(again.getBody()).contains("token-" + orderNumber);
	}

	@Test
	@Order(2)
	void webhookSettlementValidMenerbitkanEvent() throws Exception {
		String orderNumber = createOrder(2);
		long before = paymentsStreamLength();

		ResponseEntity<String> response = webhook(orderNumber, "settlement", "200", "49000.00", true, null);
		assertThat(response.getStatusCode().value()).isEqualTo(200);

		Payment payment = jdbcTemplate.queryForObject(
				"SELECT status FROM (SELECT gateway_order_id, status::text AS status FROM payments) t WHERE gateway_order_id = ?",
				String.class, orderNumber) == null ? null : null;
		var rows = jdbcTemplate.queryForMap(
				"SELECT status::text AS st FROM payments WHERE gateway_order_id = ?", orderNumber);
		assertThat(rows.get("st")).isEqualTo("SETTLED");
		assertThat(paymentsStreamLength()).isEqualTo(before + 1);
	}

	@Test
	@Order(3)
	void webhookDuplikatIdempoten() throws Exception {
		String orderNumber = createOrder(3);
		webhook(orderNumber, "settlement", "200", "49000.00", true, null);
		long afterFirst = paymentsStreamLength();

		// Duplikat: settlement kedua untuk order yang sudah SETTLED -> diabaikan
		webhook(orderNumber, "settlement", "200", "49000.00", true, null);
		assertThat(paymentsStreamLength()).isEqualTo(afterFirst);
	}

	@Test
	@Order(4)
	void webhookOutOfOrderTidakMundurkanStatus() throws Exception {
		String orderNumber = createOrder(4);
		webhook(orderNumber, "settlement", "200", "49000.00", true, null);

		// PENDING datang belakangan -> tetap SETTLED
		webhook(orderNumber, "pending", "201", "49000.00", true, null);
		var rows = jdbcTemplate.queryForMap(
				"SELECT status::text AS st FROM payments WHERE gateway_order_id = ?", orderNumber);
		assertThat(rows.get("st")).isEqualTo("SETTLED");
	}

	@Test
	@Order(5)
	void webhookSignatureInvalidDitolak() throws Exception {
		String orderNumber = createOrder(5);
		webhook(orderNumber, "settlement", "200", "49000.00", false, null);

		var rows = jdbcTemplate.queryForMap(
				"SELECT status::text AS st, (SELECT count(*) FROM payment_events e WHERE e.gateway_order_id = p.gateway_order_id) AS events FROM payments p WHERE gateway_order_id = ?",
				orderNumber);
		assertThat(rows.get("st")).isEqualTo("PENDING"); // tidak diproses
		assertThat(Long.valueOf(String.valueOf(rows.get("events")))).isEqualTo(1); // tercatat untuk audit
	}

	@Test
	@Order(6)
	void webhookNominalTidakCocokTidakDiproses() throws Exception {
		String orderNumber = createOrder(6);
		webhook(orderNumber, "settlement", "200", "999999.00", true, null);

		var rows = jdbcTemplate.queryForMap(
				"SELECT status::text AS st FROM payments WHERE gateway_order_id = ?", orderNumber);
		assertThat(rows.get("st")).isEqualTo("PENDING");
	}

	@Test
	@Order(7)
	void expiredPaymentLaluSessionBaruDenganSuffix() throws Exception {
		String orderNumber = createOrder(7);
		webhook(orderNumber, "expire", "407", "49000.00", true, null);

		// Setelah EXPIRED, pay ulang -> attempt baru -r2
		ResponseEntity<String> again = rest.exchange("/internal/payments/sessions", HttpMethod.POST,
				new HttpEntity<>("{\"orderNumber\":\"" + orderNumber + "\",\"grossAmountIdr\":49000,"
						+ "\"itemName\":\"Silver\",\"customerEmail\":\"bayar@example.com\"}", jsonHeaders()),
				String.class);
		assertThat(again.getBody()).contains(orderNumber + "-r2");
	}

	@Test
	@Order(8)
	void inquiryMengambilStatusTerbaru() throws Exception {
		String orderNumber = createOrder(8);
		STUB_STATUS.set(new MidtransGateway.TransactionStatus("settlement", null, "200", "49000.00", "gopay"));
		try {
			boolean changed = paymentService.inquireAndSync(orderNumber);
			assertThat(changed).isTrue();
			var rows = jdbcTemplate.queryForMap(
					"SELECT status::text AS st FROM payments WHERE gateway_order_id = ?", orderNumber);
			assertThat(rows.get("st")).isEqualTo("SETTLED");
		}
		finally {
			STUB_STATUS.set(new MidtransGateway.TransactionStatus("pending", null, "201", "49000.00", null));
		}
	}

	/** Sesuai perilaku "Test URL" Midtrans: order_id sintetis panjang (66 char). */
	@Test
	@Order(9)
	void webhookUjiMidtransDenganOrderIdPanjangTetap200() throws Exception {
		String longId = "payment_notif_test_G459997237_9923e226-5d80-49e6-a84a-581a776d9252";
		ResponseEntity<String> response = webhook(longId, "settlement", "200", "49000.00", true, null);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		var rows = jdbcTemplate.queryForMap(
				"SELECT count(*) AS c FROM payment_events WHERE gateway_order_id = ?", longId);
		assertThat(Long.valueOf(String.valueOf(rows.get("c")))).isEqualTo(1L);
	}

	/** Payload tanpa order_id tidak boleh menjatuhkan service (dulu: 500). */
	@Test
	@Order(10)
	void webhookTanpaOrderIdTetap200() {
		String sig = MidtransSignature.compute("", "200", "1.00", STUB_SERVER_KEY);
		ResponseEntity<String> response = rest.exchange("/v1/webhooks/payments/midtrans", HttpMethod.POST,
				new HttpEntity<>("{\"status_code\":\"200\",\"gross_amount\":\"1.00\",\"signature_key\":\""
						+ sig + "\",\"transaction_status\":\"settlement\"}", jsonHeaders()), String.class);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
	}
}
