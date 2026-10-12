package com.menujuhari.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.menujuhari.identity.application.OtpMailer;

/**
 * E2E alur otentikasi di atas PostgreSQL + Redis nyata (Testcontainers):
 * request OTP -> kode salah -> kode benar -> me -> logout -> 401.
 * Mailer produksi digantikan recording mailer (kode ditangkap di memori).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

	private static final String EMAIL = "e2e-flow@example.com";
	private static final AtomicReference<String> LAST_CODE = new AtomicReference<>();

	@Autowired
	TestRestTemplate rest;

	@TestConfiguration
	static class TestMailConfig {

		@Bean
		@Primary
		OtpMailer recordingOtpMailer() {
			return (to, code) -> LAST_CODE.set(code);
		}
	}

	private ResponseEntity<String> post(String path, String body, HttpHeaders extraHeaders) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (extraHeaders != null) {
			headers.addAll(extraHeaders);
		}
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String path, HttpHeaders extraHeaders) {
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, extraHeaders), String.class);
	}

	private String awaitOtpCode() throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			String code = LAST_CODE.get();
			if (code != null) {
				return code;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Kode OTP tidak diterima dalam 5 detik");
	}

	private String sessionCookieFrom(ResponseEntity<String> response) {
		List<String> setCookie = response.getHeaders().get(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).isNotNull();
		return setCookie.stream()
				.filter(c -> c.startsWith("mh_session=") && !c.contains("mh_session=;"))
				.findFirst()
				.map(c -> c.substring(0, c.indexOf(';')))
				.orElseThrow(() -> new AssertionError("Cookie mh_session tidak ditemukan: " + setCookie));
	}

	@Test
	@Order(1)
	void alurPenuhOtentikasi() throws InterruptedException {
		// 1. Minta OTP
		ResponseEntity<String> requestResponse = post("/v1/auth/otp/request",
				"{\"email\":\"" + EMAIL + "\"}", null);
		assertThat(requestResponse.getStatusCode().value()).isEqualTo(202);

		// 2. Verifikasi dengan kode salah -> 400 generik
		String code = awaitOtpCode();
		String wrongCode = code.equals("000000") ? "999999" : "000000";
		ResponseEntity<String> wrongResponse = post("/v1/auth/otp/verify",
				"{\"email\":\"" + EMAIL + "\",\"code\":\"" + wrongCode + "\"}", null);
		assertThat(wrongResponse.getStatusCode().value()).isEqualTo(400);
		assertThat(wrongResponse.getBody()).contains("OTP_INVALID");

		// 3. Verifikasi dengan kode benar -> 200 + cookie sesi
		ResponseEntity<String> verifyResponse = post("/v1/auth/otp/verify",
				"{\"email\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}", null);
		assertThat(verifyResponse.getStatusCode().value()).isEqualTo(200);
		String cookie = sessionCookieFrom(verifyResponse);
		assertThat(verifyResponse.getBody()).contains(EMAIL);

		// 4. /me dengan sesi -> 200
		HttpHeaders authHeaders = new HttpHeaders();
		authHeaders.add(HttpHeaders.COOKIE, cookie);
		ResponseEntity<String> meResponse = get("/v1/auth/me", authHeaders);
		assertThat(meResponse.getStatusCode().value()).isEqualTo(200);
		assertThat(meResponse.getBody()).contains("\"role\":\"CUSTOMER\"");

		// 5. /me tanpa sesi -> 401
		ResponseEntity<String> anonymous = get("/v1/auth/me", null);
		assertThat(anonymous.getStatusCode().value()).isEqualTo(401);

		// 6. Logout -> 204, lalu /me dengan cookie lama -> 401
		ResponseEntity<String> logoutResponse = rest.exchange("/v1/auth/logout", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders), String.class);
		assertThat(logoutResponse.getStatusCode().value()).isEqualTo(204);
		ResponseEntity<String> afterLogout = get("/v1/auth/me", authHeaders);
		assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
	}

	@Test
	@Order(2)
	void kirimUlangCepatDitolakCooldown() {
		ResponseEntity<String> first = post("/v1/auth/otp/request",
				"{\"email\":\"cooldown@example.com\"}", null);
		assertThat(first.getStatusCode().value()).isEqualTo(202);

		ResponseEntity<String> second = post("/v1/auth/otp/request",
				"{\"email\":\"cooldown@example.com\"}", null);
		assertThat(second.getStatusCode().value()).isEqualTo(429);
	}

	@Test
	@Order(3)
	void emailTidakValidDitolak400() {
		ResponseEntity<String> response = post("/v1/auth/otp/request", "{\"email\":\"bukan-email\"}", null);
		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).contains("VALIDATION_FAILED");
	}
}
