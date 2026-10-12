package com.menujuhari.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.menujuhari.shared.security.SessionService;

/**
 * E2E catalog-service di atas PostgreSQL nyata (Testcontainers),
 * termasuk kebijakan akses (packages publik; templates & musik perlu sesi)
 * dan endpoint internal antar-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogApiTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	SessionService sessionService;

	String cookie;

	@BeforeAll
	void seedSession() {
		cookie = "mh_session=" + sessionService.create(UUID.randomUUID(), "katalog@example.com", "CUSTOMER");
	}

	private ResponseEntity<String> get(String path) {
		return rest.getForEntity(path, String.class);
	}

	private ResponseEntity<String> getAuth(String path) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
	}

	@Test
	@Order(1)
	void packagesPublikTanpaSesi() {
		ResponseEntity<String> response = get("/v1/catalog/packages");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("\"code\":\"free\"");
		assertThat(response.getBody()).contains("\"code\":\"silver\"");
		assertThat(response.getBody()).contains("\"priceIdr\":49000");
		// Platinum inactive tidak ikut default listing
		assertThat(response.getBody()).doesNotContain("\"code\":\"platinum\"");
	}

	@Test
	@Order(2)
	void detailPaketPublik() {
		ResponseEntity<String> response = get("/v1/catalog/packages/gold");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("\"rsvp\":true");
	}

	@Test
	@Order(3)
	void templatesButuhSesi() {
		assertThat(get("/v1/catalog/templates").getStatusCode().value()).isEqualTo(401);
		assertThat(get("/v1/catalog/templates/aurora").getStatusCode().value()).isEqualTo(401);
	}

	@Test
	@Order(4)
	void daftarTemplateDenganSesi() {
		ResponseEntity<String> response = getAuth("/v1/catalog/templates");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("\"code\":\"aurora\"");
		assertThat(response.getBody()).contains("\"code\":\"lentera\"");
		assertThat(response.getBody()).contains("\"activeVersion\":\"1.0.0\"");
	}

	@Test
	@Order(5)
	void detailTemplateMemuatSkemaDanTema() {
		ResponseEntity<String> response = getAuth("/v1/catalog/templates/aurora");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("contentSchema");
		assertThat(response.getBody()).contains("maxItems");
		assertThat(response.getBody()).contains("Playfair Display");
	}

	@Test
	@Order(6)
	void templateTidakDikenal404() {
		assertThat(getAuth("/v1/catalog/templates/tidak-ada").getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(7)
	void katalogMusikBerlisensi() {
		ResponseEntity<String> response = getAuth("/v1/catalog/music");
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("Canon in D");
		assertThat(response.getBody()).contains("licenseNote");
	}

	@Test
	@Order(8)
	void endpointInternalUntukServiceLain() {
		ResponseEntity<String> exists = get("/internal/catalog/templates/aurora");
		assertThat(exists.getStatusCode().value()).isEqualTo(200);
		assertThat(exists.getBody()).contains("\"activeVersion\":\"1.0.0\"");

		assertThat(get("/internal/catalog/templates/tidak-ada").getStatusCode().value()).isEqualTo(404);

		ResponseEntity<String> pkg = get("/internal/catalog/packages/gold");
		assertThat(pkg.getStatusCode().value()).isEqualTo(200);
		assertThat(pkg.getBody()).contains("\"priceIdr\":99000");

		ResponseEntity<String> music = get("/internal/catalog/music/44444444-1111-1111-1111-111111111111");
		assertThat(music.getStatusCode().value()).isEqualTo(200);
		assertThat(music.getBody()).contains("\"objectKey\":\"music/canon-in-d.mp3\"");

		assertThat(get("/internal/catalog/music/00000000-0000-0000-0000-000000000000")
				.getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(9)
	void swaggerUiTersedia() {
		assertThat(get("/v3/api-docs").getStatusCode().value()).isEqualTo(200);
		assertThat(get("/docs").getStatusCode().value()).isEqualTo(200);
	}
}
