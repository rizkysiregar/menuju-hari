package com.menujuhari.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.menujuhari.order.application.CatalogClient;
import com.menujuhari.order.application.OrderService;
import com.menujuhari.order.application.PaymentClient;
import com.menujuhari.shared.security.SessionService;

/**
 * E2E order-service (Testcontainers): checkout idempoten + snapshot harga,
 * kupon (min/per-user), pay via payment-client stub, konsumsi event
 * payment.settled -> PAID, dan expiry job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderApiTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	SessionService sessionService;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	OrderService orderService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	String cookieA;
	String cookieB;
	UUID invitationA = UUID.randomUUID();
	String firstOrderNumber;

	private static final AtomicReference<PaymentClient.PaymentSession> STUB_SESSION =
			new AtomicReference<>(new PaymentClient.PaymentSession("gw-1", "token-1", "https://pay.example/1"));

	@TestConfiguration
	static class StubConfig {

		@Bean
		@Primary
		CatalogClient stubCatalog() {
			return code -> switch (code) {
				case "silver" -> new CatalogClient.PackageInfo("silver", "Silver", 49000, true);
				case "free" -> new CatalogClient.PackageInfo("free", "Gratis", 0, true);
				default -> new CatalogClient.PackageInfo("gold", "Gold", 99000, true);
			};
		}

		@Bean
		@Primary
		PaymentClient stubPayment() {
			return (orderNumber, gross, item, email) -> STUB_SESSION.get();
		}
	}

	@BeforeAll
	void seed() {
		cookieA = "mh_session=" + sessionService.create(UUID.randomUUID(), "order-a@example.com", "CUSTOMER");
		cookieB = "mh_session=" + sessionService.create(UUID.randomUUID(), "order-b@example.com", "CUSTOMER");
	}

	private ResponseEntity<String> post(String path, String body, String cookie, String idempotencyKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		if (idempotencyKey != null) {
			headers.add("Idempotency-Key", idempotencyKey);
		}
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String path, String cookie) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
	}

	@Test
	@Order(1)
	void checkoutDenganKuponPercentMenghasilkanSnapshot() {
		ResponseEntity<String> response = post("/v1/checkout",
				"{\"invitationId\":\"" + invitationA + "\",\"packageCode\":\"silver\",\"couponCode\":\"TEST10\"}",
				cookieA, "idem-1");
		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody()).contains("\"status\":\"PENDING\"");
		assertThat(response.getBody()).contains("\"subtotalIdr\":49000");
		assertThat(response.getBody()).contains("\"discountIdr\":4900");
		assertThat(response.getBody()).contains("\"totalIdr\":44100");
		assertThat(response.getBody()).contains("\"couponCode\":\"TEST10\"");
		firstOrderNumber = response.getBody().replaceAll(".*\"orderNumber\":\"([^\"]+)\".*", "$1");
	}

	@Test
	@Order(2)
	void checkoutIdempotenMengembalikanOrderSama() {
		ResponseEntity<String> response = post("/v1/checkout",
				"{\"invitationId\":\"" + invitationA + "\",\"packageCode\":\"silver\",\"couponCode\":\"TEST10\"}",
				cookieA, "idem-1");
		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody()).contains(firstOrderNumber);
	}

	@Test
	@Order(3)
	void kuponDenganMinimumTransaksiDitolak() {
		ResponseEntity<String> response = post("/v1/checkout",
				"{\"invitationId\":\"" + UUID.randomUUID() + "\",\"packageCode\":\"free\",\"couponCode\":\"HEMAT5000\"}",
				cookieA, "idem-2");
		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody()).contains("COUPON_MIN_TRANSACTION");
	}

	@Test
	@Order(4)
	void kuponTidakDikenalDitolak() {
		ResponseEntity<String> response = post("/v1/checkout",
				"{\"invitationId\":\"" + UUID.randomUUID() + "\",\"packageCode\":\"silver\",\"couponCode\":\"TIDAKADA\"}",
				cookieA, "idem-3");
		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody()).contains("COUPON_INVALID");
	}

	@Test
	@Order(5)
	void batasPerUserKuponDipakai() {
		// TEST10 max_per_user = 2; user A sudah 1 -> satu lagi ok, yang ketiga ditolak
		ResponseEntity<String> second = post("/v1/checkout",
				"{\"invitationId\":\"" + UUID.randomUUID() + "\",\"packageCode\":\"silver\",\"couponCode\":\"TEST10\"}",
				cookieA, "idem-4");
		assertThat(second.getStatusCode().value()).isEqualTo(201);

		ResponseEntity<String> third = post("/v1/checkout",
				"{\"invitationId\":\"" + UUID.randomUUID() + "\",\"packageCode\":\"silver\",\"couponCode\":\"TEST10\"}",
				cookieA, "idem-5");
		assertThat(third.getStatusCode().value()).isEqualTo(422);
		assertThat(third.getBody()).contains("COUPON_PER_USER_LIMIT");
	}

	@Test
	@Order(6)
	void payMenghasilkanRedirectUrlDanDitolakJikaSudahPaid() {
		ResponseEntity<String> pay = post("/v1/orders/" + firstOrderNumber + "/pay", "", cookieA, null);
		assertThat(pay.getStatusCode().value()).isEqualTo(200);
		assertThat(pay.getBody()).contains("\"redirectUrl\":\"https://pay.example/1\"");
	}

	@Test
	@Order(7)
	void eventPaymentSettledMembuatOrderPaid() throws Exception {
		redis.opsForStream().add("mh:stream:payments", java.util.Map.of(
				"type", "payment.settled",
				"orderNumber", firstOrderNumber,
				"paymentType", "gopay",
				"paidAt", java.time.Instant.now().toString()));

		ResponseEntity<String> response = null;
		for (int i = 0; i < 30; i++) {
			Thread.sleep(300);
			response = get("/v1/orders/" + firstOrderNumber, cookieA);
			if (response.getBody() != null && response.getBody().contains("\"status\":\"PAID\"")) {
				break;
			}
		}
		assertThat(response).isNotNull();
		assertThat(response.getBody()).contains("\"status\":\"PAID\"");
		assertThat(response.getBody()).contains("\"paidAt\"");

		// sudah PAID -> pay ditolak 409
		ResponseEntity<String> payAgain = post("/v1/orders/" + firstOrderNumber + "/pay", "", cookieA, null);
		assertThat(payAgain.getStatusCode().value()).isEqualTo(409);
	}

	@Test
	@Order(8)
	void isolationAntarUser() {
		assertThat(get("/v1/orders/" + firstOrderNumber, cookieB).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(9)
	void orderPendingLamaJadiExpiredDanKuponLepas() {
		UUID invitation = UUID.randomUUID();
		ResponseEntity<String> created = post("/v1/checkout",
				"{\"invitationId\":\"" + invitation + "\",\"packageCode\":\"silver\",\"couponCode\":\"TEST10\"}",
				cookieB, "idem-exp");
		assertThat(created.getStatusCode().value()).isEqualTo(201);
		String orderNumber = created.getBody().replaceAll(".*\"orderNumber\":\"([^\"]+)\".*", "$1");

		jdbcTemplate.update(
				"UPDATE orders SET created_at = now() - interval '25 hours' WHERE order_number = ?", orderNumber);

		int expired = orderService.expireStaleOrders();
		assertThat(expired).isGreaterThanOrEqualTo(1);

		ResponseEntity<String> after = get("/v1/orders/" + orderNumber, cookieB);
		assertThat(after.getBody()).contains("\"status\":\"EXPIRED\"");
	}
}
