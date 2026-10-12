package com.menujuhari.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.menujuhari.media.application.MediaService;
import com.menujuhari.media.application.MusicCatalogClient;
import com.menujuhari.shared.security.SessionService;

/**
 * E2E media-service di atas PostgreSQL + Redis + MinIO nyata (Testcontainers):
 * presigned upload, verifikasi magic bytes, proses varian async, kepemilikan,
 * kuota, cleanup, dan streaming musik (katalog di-stub).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaApiTest {

	static final GenericContainer<?> MINIO = new GenericContainer<>(
			DockerImageName.parse("cgr.dev/chainguard/minio:latest"))
			.withCommand("server", "/data")
			.withEnv("MINIO_ROOT_USER", "testuser")
			.withEnv("MINIO_ROOT_PASSWORD", "testpassword")
			.withExposedPorts(9000);

	@DynamicPropertySource
	static void minioProperties(DynamicPropertyRegistry registry) {
		MINIO.start();
		registry.add("minio.endpoint", () -> "http://localhost:" + MINIO.getMappedPort(9000));
		registry.add("minio.access-key", () -> "testuser");
		registry.add("minio.secret-key", () -> "testpassword");
		registry.add("minio.bucket", () -> "menujuhari");
	}

	@Autowired
	TestRestTemplate rest;

	@Autowired
	SessionService sessionService;

	@Autowired
	MediaService mediaService;

	UUID userA;
	String cookieA;
	UUID userB;
	String cookieB;
	UUID photoId;
	byte[] jpegBytes;

	String cookie(String token) {
		return "mh_session=" + token;
	}

	@TestConfiguration
	static class StubCatalogConfig {

		@Bean
		@Primary
		MusicCatalogClient stubMusicCatalog() {
			return trackId -> new MusicCatalogClient.TrackInfo(trackId.toString(), "Canon in D",
					"music/canon-in-d.mp3", 180);
		}
	}

	@BeforeAll
	void seed() throws Exception {
		userA = UUID.randomUUID();
		userB = UUID.randomUUID();
		cookieA = cookie(sessionService.create(userA, "media-a@example.com", "CUSTOMER"));
		cookieB = cookie(sessionService.create(userB, "media-b@example.com", "CUSTOMER"));

		// JPEG asli 2000x1200 (di atas ukuran thumbnail supaya proses terlihat)
		BufferedImage image = new BufferedImage(2000, 1200, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(java.awt.Color.ORANGE);
		graphics.fillRect(0, 0, 2000, 1200);
		graphics.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		jpegBytes = out.toByteArray();
	}

	private ResponseEntity<String> post(String path, String body, String sessionCookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		if (sessionCookie != null) {
			headers.add(HttpHeaders.COOKIE, sessionCookie);
		}
		return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
	}

	private ResponseEntity<String> get(String path, String sessionCookie) {
		HttpHeaders headers = new HttpHeaders();
		if (sessionCookie != null) {
			headers.add(HttpHeaders.COOKIE, sessionCookie);
		}
		return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
	}


	@Test
	@Order(1)
	void intentDitolakUntukTipeLiar() {
		ResponseEntity<String> response = post("/v1/media/photos/upload-intent",
				"{\"filename\":\"virus.gif\",\"contentType\":\"image/gif\",\"sizeBytes\":1000}", cookieA);
		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody()).contains("PHOTO_TYPE_NOT_SUPPORTED");
	}

	@Test
	@Order(2)
	void intentDitolakUntukUkuranBesar() {
		ResponseEntity<String> response = post("/v1/media/photos/upload-intent",
				"{\"filename\":\"besar.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":99999999}", cookieA);
		assertThat(response.getStatusCode().value()).isEqualTo(422);
		assertThat(response.getBody()).contains("PHOTO_TOO_LARGE");
	}

	@Test
	@Order(3)
	void alurUnggahLengkapHinggaVarianSiap() throws Exception {
		// 1. Intent -> presigned PUT
		ResponseEntity<String> intent = post("/v1/media/photos/upload-intent",
				"{\"filename\":\"prewed.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":" + jpegBytes.length + "}",
				cookieA);
		assertThat(intent.getStatusCode().value()).isEqualTo(201);
		String body = intent.getBody();
		assertThat(body).contains("uploadUrl");
		photoId = UUID.fromString(body.replaceAll(".*\"mediaId\":\"([^\"]+)\".*", "$1"));
		String uploadUrl = body.replaceAll(".*\"uploadUrl\":\"([^\"]+)\".*", "$1").replace("\\/", "/");

		// 2. PUT langsung ke MinIO via presigned URL
		// (kirim sebagai java.net.URI agar query params presigned tidak
		//  di-encode ulang oleh URI-template handler)
		HttpHeaders putHeaders = new HttpHeaders();
		putHeaders.setContentType(MediaType.IMAGE_JPEG);
		ResponseEntity<String> put = rest.exchange(java.net.URI.create(uploadUrl), HttpMethod.PUT,
				new HttpEntity<>(jpegBytes, putHeaders), String.class);
		assertThat(put.getStatusCode().value())
				.as("PUT gagal. Status: %s, body: %s, url: %s", put.getStatusCode(), put.getBody(), uploadUrl)
				.isEqualTo(200);

		// 3. Complete -> diproses async
		ResponseEntity<String> complete = post("/v1/media/photos/" + photoId + "/upload-complete", "", cookieA);
		assertThat(complete.getStatusCode().value()).isIn(200, 202);

		// 4. Polling hingga READY
		String photo = null;
		for (int i = 0; i < 30; i++) {
			Thread.sleep(300);
			photo = get("/v1/media/photos/" + photoId, cookieA).getBody();
			if (photo != null && photo.contains("\"status\":\"READY\"")) {
				break;
			}
		}
		assertThat(photo).contains("\"status\":\"READY\"");
		assertThat(photo).contains("\"web\"");
		assertThat(photo).contains("\"thumb\"");
		assertThat(photo).contains("image/jpeg");
	}

	@Test
	@Order(4)
	void isolasiKepemilikan() {
		assertThat(get("/v1/media/photos/" + photoId, cookieB).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(5)
	void fileBukanGambarDitolakDanDitandaiFailed() throws Exception {
		ResponseEntity<String> intent = post("/v1/media/photos/upload-intent",
				"{\"filename\":\" palsu.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":100}", cookieA);
		String body = intent.getBody();
		UUID fakeId = UUID.fromString(body.replaceAll(".*\"mediaId\":\"([^\"]+)\".*", "$1"));
		String uploadUrl = body.replaceAll(".*\"uploadUrl\":\"([^\"]+)\".*", "$1").replace("\\/", "/");

		HttpHeaders putHeaders = new HttpHeaders();
		putHeaders.setContentType(MediaType.TEXT_PLAIN);
		rest.exchange(java.net.URI.create(uploadUrl), HttpMethod.PUT,
				new HttpEntity<>("ini bukan gambar".getBytes(), putHeaders), String.class);

		ResponseEntity<String> complete = post("/v1/media/photos/" + fakeId + "/upload-complete", "", cookieA);
		assertThat(complete.getStatusCode().value()).isEqualTo(422);
		assertThat(complete.getBody()).contains("PHOTO_INVALID");
	}

	@Test
	@Order(6)
	void streamingMusikMemakaiSignedUrl() {
		ResponseEntity<String> response = get("/v1/media/music/44444444-1111-1111-1111-111111111111/stream-url",
				cookieA);
		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getBody()).contains("url");
		// objectKey dari stub -> URL presigned mengandung nama objek
		assertThat(response.getBody()).contains("canon-in-d.mp3");
	}

	@Test
	@Order(7)
	void hapusFotoOlehPemilik() {
		ResponseEntity<String> delete = rest.exchange("/v1/media/photos/" + photoId, HttpMethod.DELETE,
				new HttpEntity<>(null, authHeaders(cookieA)), String.class);
		assertThat(delete.getStatusCode().value()).isEqualTo(204);
		assertThat(get("/v1/media/photos/" + photoId, cookieA).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	@Order(8)
	void cleanupMembuangPendingKedaluwarsa() {
		ResponseEntity<String> intent = post("/v1/media/photos/upload-intent",
				"{\"filename\":\"ygsalahkan.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1000}", cookieA);
		String body = intent.getBody();
		UUID staleId = UUID.fromString(body.replaceAll(".*\"mediaId\":\"([^\"]+)\".*", "$1"));

		// Panggil cleanup langsung dengan cutoff di masa lalu
		Instant past = Instant.now().plusSeconds(60);
		int removed = mediaService.cleanupExpired(past, past);
		assertThat(removed).isGreaterThanOrEqualTo(1);

		assertThat(get("/v1/media/photos/" + staleId, cookieA).getStatusCode().value()).isEqualTo(404);
	}

	private HttpHeaders authHeaders(String sessionCookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, sessionCookie);
		return headers;
	}
}
