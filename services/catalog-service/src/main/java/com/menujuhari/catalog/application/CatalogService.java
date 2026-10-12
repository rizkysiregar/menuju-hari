package com.menujuhari.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.catalog.domain.MusicTrack;
import com.menujuhari.catalog.domain.MusicTrackRepository;
import com.menujuhari.catalog.domain.Package;
import com.menujuhari.catalog.domain.PackageRepository;
import com.menujuhari.catalog.domain.Template;
import com.menujuhari.catalog.domain.TemplateRepository;
import com.menujuhari.catalog.domain.TemplateVersion;
import com.menujuhari.catalog.domain.TemplateVersionRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Pembacaan katalog (TPL-001..004). Catalog-service bersifat read-mostly;
 * mutasi data dilakukan lewat migrasi seed, bukan API.
 */
@Service
public class CatalogService {

	public static class ResourceNotFoundException extends RuntimeException {
		public ResourceNotFoundException(String message) {
			super(message);
		}
	}

	public record TemplateSummary(String code, String name, String description, String activeVersion) {
	}

	public record TemplateDetail(String code, String name, String description, String activeVersion,
			Map<String, Object> contentSchema, Map<String, Object> theme, Map<String, Object> capabilityFlags) {
	}

	public record MusicItem(String id, String title, String artist, int durationSeconds, String licenseNote) {
	}

	private final TemplateRepository templateRepository;
	private final TemplateVersionRepository versionRepository;
	private final PackageRepository packageRepository;
	private final MusicTrackRepository musicRepository;
	private final ObjectMapper objectMapper;

	public CatalogService(TemplateRepository templateRepository, TemplateVersionRepository versionRepository,
			PackageRepository packageRepository, MusicTrackRepository musicRepository, ObjectMapper objectMapper) {
		this.templateRepository = templateRepository;
		this.versionRepository = versionRepository;
		this.packageRepository = packageRepository;
		this.musicRepository = musicRepository;
		this.objectMapper = objectMapper;
	}

	public List<TemplateSummary> listTemplates() {
		return templateRepository.findByActiveTrue().stream()
				.map(template -> new TemplateSummary(template.getCode(), template.getName(),
						template.getDescription(),
						activeVersion(template.getId()).map(TemplateVersion::getVersion).orElse(null)))
				.toList();
	}

	public TemplateDetail templateDetail(String code) {
		Template template = templateRepository.findByCode(code.toLowerCase())
				.filter(Template::isActive)
				.orElseThrow(() -> new ResourceNotFoundException("Template tidak ditemukan"));
		TemplateVersion version = activeVersion(template.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Template belum punya versi aktif"));
		return new TemplateDetail(template.getCode(), template.getName(), template.getDescription(),
				version.getVersion(), json(version.getContentSchema()), json(version.getTheme()),
				json(version.getCapabilityFlags()));
	}

	public List<Package> listPackages(boolean includeInactive) {
		return includeInactive
				? packageRepository.findAllByOrderBySortOrderAsc()
				: packageRepository.findByActiveTrueOrderBySortOrderAsc();
	}

	public Package getPackage(String code) {
		return packageRepository.findByCode(code.toLowerCase())
				.orElseThrow(() -> new ResourceNotFoundException("Paket tidak ditemukan"));
	}

	public List<MusicTrack> listMusic() {
		return musicRepository.findByActiveTrueOrderByTitle();
	}

	public MusicTrack getMusicTrack(UUID id) {
		return musicRepository.findById(id)
				.filter(MusicTrack::isActive)
				.orElseThrow(() -> new ResourceNotFoundException("Track musik tidak ditemukan"));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> json(String raw) {
		return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
		});
	}

	private java.util.Optional<TemplateVersion> activeVersion(UUID templateId) {
		return versionRepository.findFirstByTemplateIdAndStatusOrderByVersionDesc(templateId,
				TemplateVersion.Status.ACTIVE);
	}
}
