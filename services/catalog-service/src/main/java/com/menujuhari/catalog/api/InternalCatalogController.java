package com.menujuhari.catalog.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.catalog.application.CatalogService;
import com.menujuhari.catalog.domain.Package;

/**
 * Endpoint antar-service. TIDAK terekspos internet di produksi:
 * Ingress hanya me-route /api/** publik; /internal/** hanya ClusterIP.
 * Locally dipakai invitation-service (validasi template & paket).
 */
@RestController
@RequestMapping("/internal/catalog")
public class InternalCatalogController {

	private final CatalogService catalogService;

	public InternalCatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	public record TemplateInfo(String code, String name, String activeVersion) {
	}

	public record PackageInfo(String code, String name, int priceIdr, Map<String, Object> featureFlags,
			boolean active) {
	}

	public record MusicInfo(String id, String title, String objectKey, int durationSeconds) {
	}

	@GetMapping("/templates/{code}")
	public ResponseEntity<TemplateInfo> template(@PathVariable String code) {
		var detail = catalogService.templateDetail(code);
		return ResponseEntity.ok(new TemplateInfo(detail.code(), detail.name(), detail.activeVersion()));
	}

	@GetMapping("/packages/{code}")
	public ResponseEntity<PackageInfo> pkg(@PathVariable String code) {
		Package pkg = catalogService.getPackage(code);
		return ResponseEntity.ok(new PackageInfo(pkg.getCode(), pkg.getName(), pkg.getPriceIdr(),
				catalogService.json(pkg.getFeatureFlags()), pkg.isActive()));
	}

	@GetMapping("/music/{id}")
	public ResponseEntity<MusicInfo> music(@PathVariable java.util.UUID id) {
		var track = catalogService.getMusicTrack(id);
		return ResponseEntity.ok(new MusicInfo(track.getId().toString(), track.getTitle(),
				track.getObjectKey(), track.getDurationSeconds()));
	}
}
