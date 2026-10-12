package com.menujuhari.invitation.application;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.invitation.domain.Invitation;
import com.menujuhari.invitation.domain.InvitationRepository;
import com.menujuhari.invitation.domain.InvitationStatus;
import com.menujuhari.invitation.infra.config.InvitationProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Logika undangan sesuai INV-001..008 di PROJECT_PLAN.md:
 * kuota draft, slug unik, optimistic concurrency, aturan tanggal acara,
 * siklus hidup, dan keamanan kepemilikan (user dari sesi, bukan request).
 */
@Service
public class InvitationService {

	private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
	private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
	private static final long VALIDITY_DAYS = 30; // [K2] tanggal acara + 30 hari

	private final InvitationRepository repository;
	private final InvitationProperties props;
	private final ObjectMapper objectMapper;
	private final CatalogClient catalogClient;

	public InvitationService(InvitationRepository repository, InvitationProperties props, ObjectMapper objectMapper,
			CatalogClient catalogClient) {
		this.repository = repository;
		this.props = props;
		this.objectMapper = objectMapper;
		this.catalogClient = catalogClient;
	}

	@Transactional
	public Invitation create(UUID userId, String templateCode, String brideName, String groomName,
			LocalDate eventDate) {
		// Validasi template via catalog-service (TPL-003); versi aktif
		// dibawa masuk agar undangan ter-lock ke versi yang benar (TPL-002).
		CatalogClient.TemplateInfo template = catalogClient.resolveTemplate(templateCode);
		if (eventDate.isBefore(LocalDate.now(WIB))) {
			throw new EventDateInvalidException();
		}
		if (repository.countByUserIdAndStatus(userId, InvitationStatus.DRAFT) >= props.maxActiveDrafts()) {
			throw new DraftQuotaExceededException();
		}

		String slug = uniqueSlug(brideName, groomName);
		Invitation invitation = repository.save(Invitation.newDraft(userId, template.code(),
				template.activeVersion(), slug, brideName, groomName, eventDate));
		log.info("Undangan draft dibuat: {} template {} v{} (user {})", invitation.getSlug(),
				template.code(), template.activeVersion(), userId);
		return invitation;
	}

	public Page<Invitation> list(UUID userId, InvitationStatus status, int page, int size) {
		int safeSize = Math.min(Math.max(size, 1), 50);
		Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
		if (status == null) {
			return repository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, InvitationStatus.ARCHIVED,
					pageable);
		}
		return repository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
	}

	public Invitation getOwned(UUID userId, UUID id) {
		return repository.findByIdAndUserIdAndStatusNot(id, userId, InvitationStatus.ARCHIVED)
				.orElseThrow(InvitationNotFoundException::new);
	}

	@Transactional
	public Invitation patchMeta(UUID userId, UUID id, String newSlug, LocalDate newEventDate) {
		Invitation invitation = getOwned(userId, id);
		if (newSlug == null && newEventDate == null) {
			return invitation;
		}
		if (!invitation.getStatus().editable()) {
			throw new InvitationNotEditableException();
		}
		if (newSlug != null && !newSlug.equals(invitation.getSlug())) {
			if (!SlugGenerator.valid(newSlug)) {
				throw new SlugInvalidException();
			}
			if (repository.existsBySlug(newSlug)) {
				throw new SlugAlreadyTakenException();
			}
			invitation.setSlug(newSlug);
		}
		if (newEventDate != null && !newEventDate.equals(invitation.getEventDate())) {
			if (newEventDate.isBefore(LocalDate.now(WIB))) {
				throw new EventDateInvalidException();
			}
			invitation.setEventDate(newEventDate);
			// [K3] tanggal acara berubah -> masa aktif ikut bergeser
			if (invitation.getExpiresAt() != null) {
				invitation.setExpiresAt(expiresFor(newEventDate));
			}
		}
		log.info("Meta undangan {} diperbarui (user {})", id, userId);
		return invitation;
	}

	@Transactional
	public Invitation patchContent(UUID userId, UUID id, Map<String, Object> content, int requestVersion) {
		Invitation invitation = getOwned(userId, id);
		if (!invitation.getStatus().editable()) {
			throw new InvitationNotEditableException();
		}
		if (requestVersion != invitation.getContentVersion()) {
			throw new VersionConflictException(invitation.getContentVersion());
		}
		if (content == null || content.isEmpty()) {
			throw new ContentInvalidException();
		}
		String json;
		try {
			json = objectMapper.writeValueAsString(content);
		}
		catch (IllegalArgumentException e) {
			throw new ContentInvalidException();
		}
		if (json.length() > props.contentMaxChars()) {
			throw new ContentTooLargeException();
		}
		invitation.setContent(json);
		invitation.bumpContentVersion();
		return invitation;
	}

	@Transactional
	public void delete(UUID userId, UUID id) {
		Invitation invitation = getOwned(userId, id);
		if (invitation.getStatus().deletionIsHard()) {
			repository.delete(invitation);
			log.info("Draft undangan dihapus permanen: {} (user {})", id, userId);
		}
		else {
			invitation.archive();
			log.info("Undangan diarsipkan: {} (user {})", id, userId);
		}
	}

	/**
	 * PUB-001: dipanggil konsumen event order.paid — publikasi otomatis
	 * bila tanggal acara masih di masa depan; idempoten.
	 */
	@Transactional
	public void publishAfterPayment(UUID userId, UUID invitationId) {
		Invitation invitation = repository.findById(invitationId).orElse(null);
		if (invitation == null || !invitation.getUserId().equals(userId)) {
			log.warn("order.paid untuk undangan tak cocok: invitation {} user {}", invitationId, userId);
			return;
		}
		if (invitation.getStatus() != InvitationStatus.DRAFT
				&& invitation.getStatus() != InvitationStatus.ELIGIBLE) {
			log.info("Publikasi dilewati, status {} (invitation {})", invitation.getStatus(), invitationId);
			return;
		}
		if (invitation.getEventDate().isBefore(LocalDate.now(WIB))) {
			log.warn("Publikasi ditunda — tanggal acara sudah lewat (invitation {})", invitationId);
			return;
		}
		invitation.publish();
		log.info("Undangan terpublikasi: {} s/d {}", invitation.getSlug(), invitation.getExpiresAt());
	}

	private String uniqueSlug(String brideName, String groomName) {
		String base = SlugGenerator.baseSlug(brideName, groomName);
		if (!repository.existsBySlug(base)) {
			return base;
		}
		for (int attempt = 0; attempt < props.slugMaxAttempts(); attempt++) {
			String candidate = base + "-" + SlugGenerator.randomSuffix();
			if (!repository.existsBySlug(candidate)) {
				return candidate;
			}
		}
		throw new SlugAlreadyTakenException();
	}

	private static java.time.Instant expiresFor(LocalDate eventDate) {
		return eventDate.plusDays(VALIDITY_DAYS).atTime(23, 59, 59).atZone(WIB).toInstant();
	}

	public Map<String, Object> parseContent(Invitation invitation) {
		if (invitation.getContent() == null || invitation.getContent().isBlank()) {
			return new HashMap<>();
		}
		return objectMapper.readValue(invitation.getContent(),
				new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
				});
	}
}
