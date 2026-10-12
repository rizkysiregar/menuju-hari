package com.menujuhari.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.menujuhari.invitation.application.CatalogClient;
import com.menujuhari.invitation.application.UnknownTemplateException;
import com.menujuhari.shared.security.SessionService;

/**
 * E2E invitation-service di atas PostgreSQL + Redis nyata (Testcontainers),
 * termasuk otorisasi kepemilikan, optimistic concurrency, kuota draft,
 * dan ketersediaan Swagger UI (mh security-starter).
 * CatalogClient di-stub — validasi template nyata diuji di CatalogApiTest
 * dan smoke E2E lintas service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationApiTest {

	@Autowired
	TestRestTemplate rest;

	@Autowired
	SessionService sessionService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	UUID userA;
	String cookieA;
	UUID userB;
	String cookieB;
	UUID invitationId;

	LocalDate eventDate = LocalDate.now().plusMonths(2);

	@TestConfiguration
	static class StubCatalogConfig {

		@Bean
		@org.springframework.context.annotation.Primary
		CatalogClient stubCatalogClient() {
			return code -> switch (code) {
				case "aurora", "lentera" -> new CatalogClient.TemplateInfo(code, code, "1.0.0");
				default -> throw new UnknownTemplateException();
			};
		}
	}

	private HttpHeaders auth(String cookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.add(HttpHeaders.COOKIE, cookie);
		return headers;
	}

	private ResponseEntity<String> post(String path, String body, HttpHeaders headers) {
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> patch(String path, String body, HttpHeaders headers) {
		return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String path, HttpHeaders headers) {
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
	}

	@BeforeAll
	void seedSessions() {
		userA = UUID.randomUUID();
		cookieA = "mh_session=" + sessionService.create(userA, "user-a@example.com", "CUSTOMER");
		userB = UUID.randomUUID();
		cookieB = "mh_session=" + sessionService.create(userB, "user-b@example.com", "CUSTOMER");
	}

	@Test
	@Order(1)
	void buatUndanganSukses() {
		ResponseEntity<String> response = post("/v1/invitations",
				"{\"templateCode\":\"aurora\",\"brideName\":\"Sari\",\"groomName\":\"Budi\",\"eventDate\":\""
						+ eventDate + "\"}",
				auth(cookieA));

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(response.getBody()).contains("\"status\":\"DRAFT\"");
		assertThat(response.getBody()).contains("\"slug\":\"sari-budi\"");
		assertThat(response.getBody()).contains("\"contentVersion\":1");
		invitationId = UUID.fromString(response.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
	}

	@Test
	@Order(2)
	void daftarUndanganMemuatDraft() {
		ResponseEntity<String> response = get("/v1/invitations", auth(cookieA));
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("sari-budi");
	}

	@Test
	@Order(3)
	void autosaveKontenDanVersiNaik() {
		ResponseEntity<String> response = patch("/v1/invitations/" + invitationId + "/content",
				"{\"content\":{\"cover\":{\"salam\":\"Assalamu'alaikum\"}},\"contentVersion\":1}",
				auth(cookieA));

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("\"contentVersion\":2");

		// Pastikan tersimpan sebagai JSONB objek (bukan string berkutip)
		String raw = jdbcTemplate.queryForObject(
				"SELECT content::text FROM invitations WHERE id = ?", String.class, invitationId);
		assertThat(raw).startsWith("{").contains("Assalamu'alaikum");
	}

	@Test
	@Order(4)
	void autosaveDenganVersiUsangDitolak409() {
		ResponseEntity<String> response = patch("/v1/invitations/" + invitationId + "/content",
				"{\"content\":{\"cover\":{}},\"contentVersion\":1}", auth(cookieA));

		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody()).contains("VERSION_CONFLICT");
		assertThat(response.getBody()).contains("\"currentVersion\":\"2\"");
	}

	@Test
	@Order(5)
	void gantiSlugDanTolakDuplikat() {
		ResponseEntity<String> renamed = patch("/v1/invitations/" + invitationId,
				"{\"slug\":\"sari-dan-budi\"}", auth(cookieA));
		assertThat(renamed.getStatusCode().value()).isEqualTo(200);
		assertThat(renamed.getBody()).contains("\"slug\":\"sari-dan-budi\"");

		// Draft kedua untuk user A
		ResponseEntity<String> second = post("/v1/invitations",
				"{\"templateCode\":\"lentera\",\"brideName\":\"Dewi\",\"groomName\":\"Rian\",\"eventDate\":\""
						+ eventDate + "\"}",
				auth(cookieA));
		assertThat(second.getStatusCode().value()).isEqualTo(201);
		UUID secondId = UUID.fromString(second.getBody().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

		// Coba pakai slug milik undangan pertama -> 409 SLUG_TAKEN
		ResponseEntity<String> duplicate = patch("/v1/invitations/" + secondId,
				"{\"slug\":\"sari-dan-budi\"}", auth(cookieA));
		assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
		assertThat(duplicate.getBody()).contains("SLUG_TAKEN");
	}

	@Test
	@Order(6)
	void isolasiKepemilikanAntarUser() {
		ResponseEntity<String> foreignGet = get("/v1/invitations/" + invitationId, auth(cookieB));
		assertThat(foreignGet.getStatusCode().value()).isEqualTo(404);

		ResponseEntity<String> foreignPatch = patch("/v1/invitations/" + invitationId + "/content",
				"{\"content\":{},\"contentVersion\":2}", auth(cookieB));
		assertThat(foreignPatch.getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(7)
	void tanggalAcaraLampauDitolak() {
		ResponseEntity<String> response = patch("/v1/invitations/" + invitationId,
				"{\"eventDate\":\"2020-01-01\"}", auth(cookieA));
		assertThat(response.getStatusCode().value()).isEqualTo(422);
	}

	@Test
	@Order(8)
	void kuotaTigaDraftAktif() {
		// user A sudah punya 2 draft (sari-dan-budi + draft kedua) -> buat 1 lagi
		ResponseEntity<String> third = post("/v1/invitations",
				"{\"templateCode\":\"aurora\",\"brideName\":\"Mira\",\"groomName\":\"Aan\",\"eventDate\":\""
						+ eventDate + "\"}",
				auth(cookieA));
		assertThat(third.getStatusCode().value()).isEqualTo(201);

		ResponseEntity<String> fourth = post("/v1/invitations",
				"{\"templateCode\":\"aurora\",\"brideName\":\"Nia\",\"groomName\":\"Oka\",\"eventDate\":\""
						+ eventDate + "\"}",
				auth(cookieA));
		assertThat(fourth.getStatusCode().value()).isEqualTo(422);
		assertThat(fourth.getBody()).contains("DRAFT_QUOTA_EXCEEDED");
	}

	@Test
	@Order(9)
	void hapusDraftMenyusulBisaBuatLagi() {
		// hapus satu draft user A -> kuota longgar lagi
		String listBody = get("/v1/invitations", auth(cookieA)).getBody();
		String firstId = listBody.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
		ResponseEntity<String> delete = rest.exchange("/v1/invitations/" + firstId, HttpMethod.DELETE,
				new HttpEntity<>(null, auth(cookieA)), String.class);
		assertThat(delete.getStatusCode().value()).isEqualTo(204);

		ResponseEntity<String> reCreate = post("/v1/invitations",
				"{\"templateCode\":\"lentera\",\"brideName\":\"Nia\",\"groomName\":\"Oka\",\"eventDate\":\""
						+ eventDate + "\"}",
				auth(cookieA));
		assertThat(reCreate.getStatusCode().value()).isEqualTo(201);
	}

	@Test
	@Order(10)
	void tanpaSesiDitolak401() {
		ResponseEntity<String> response = get("/v1/invitations", null);
		assertThat(response.getStatusCode().value()).isEqualTo(401);
	}

	@Test
	@Order(11)
	void swaggerUiTersedia() {
		ResponseEntity<String> apiDocs = get("/v3/api-docs", null);
		assertThat(apiDocs.getStatusCode().value()).isEqualTo(200);
		assertThat(apiDocs.getBody()).contains("\"openapi\"");
		assertThat(apiDocs.getBody()).contains("Invitation Service API");

		ResponseEntity<String> docs = get("/docs", null);
		assertThat(docs.getStatusCode().value()).isEqualTo(200);
		assertThat(docs.getBody()).contains("swagger");
	}
}
