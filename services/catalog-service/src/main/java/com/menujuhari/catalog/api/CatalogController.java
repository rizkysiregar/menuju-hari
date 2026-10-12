package com.menujuhari.catalog.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.menujuhari.catalog.application.CatalogService;
import com.menujuhari.catalog.api.dto.PackageResponse;
import com.menujuhari.catalog.domain.MusicTrack;
import com.menujuhari.catalog.domain.Package;
import com.menujuhari.catalog.domain.Template;
import com.menujuhari.catalog.domain.TemplateVersion;

/**
 * API katalog. Paket publik (dipakai landing page); template & musik
 * menuntut sesi (TPL-001/004). Endpoint internal ada di InternalCatalogController.
 */
@RestController
@RequestMapping("/v1/catalog")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/packages")
	public ResponseEntity<List<PackageResponse>> packages(
			@RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
		return ResponseEntity.ok(catalogService.listPackages(includeInactive).stream()
				.map(pkg -> PackageResponse.from(pkg, catalogService.json(pkg.getFeatureFlags())))
				.toList());
	}

	@GetMapping("/packages/{code}")
	public ResponseEntity<PackageResponse> packageDetail(@PathVariable String code) {
		Package pkg = catalogService.getPackage(code);
		return ResponseEntity.ok(PackageResponse.from(pkg, catalogService.json(pkg.getFeatureFlags())));
	}

	@GetMapping("/templates")
	public List<CatalogService.TemplateSummary> templates() {
		return catalogService.listTemplates();
	}

	@GetMapping("/templates/{code}")
	public ResponseEntity<CatalogService.TemplateDetail> templateDetail(@PathVariable String code) {
		return ResponseEntity.ok(catalogService.templateDetail(code));
	}

	@GetMapping("/music")
	public List<CatalogService.MusicItem> music() {
		return catalogService.listMusic().stream()
				.map(CatalogController::toMusicItem)
				.toList();
	}

	private static CatalogService.MusicItem toMusicItem(MusicTrack track) {
		return new CatalogService.MusicItem(track.getId().toString(), track.getTitle(), track.getArtist(),
				track.getDurationSeconds(), track.getLicenseNote());
	}
}
