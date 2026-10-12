package com.menujuhari.invitation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.menujuhari.invitation.domain.Invitation;
import com.menujuhari.invitation.domain.InvitationRepository;
import com.menujuhari.invitation.domain.InvitationStatus;
import com.menujuhari.invitation.infra.config.InvitationProperties;
import tools.jackson.databind.ObjectMapper;

class InvitationServiceTest {

	InvitationRepository repository = mock(InvitationRepository.class);

	CatalogClient catalogClient = mock(CatalogClient.class);

	InvitationService service = new InvitationService(repository,
			new InvitationProperties(3, 131072, 5), new ObjectMapper(), catalogClient);

	UUID userId = UUID.randomUUID();
	LocalDate eventDate = LocalDate.now().plusMonths(2);

	@BeforeEach
	void stubSave() {
		when(repository.save(any(Invitation.class))).thenAnswer(inv -> inv.getArgument(0));
		when(catalogClient.resolveTemplate(any())).thenReturn(new CatalogClient.TemplateInfo("aurora", "Aurora", "1.0.0"));
	}

	@Test
	void createSuksesMenyimpanDraftDenganSlug() {
		when(repository.countByUserIdAndStatus(userId, InvitationStatus.DRAFT)).thenReturn(0L);
		when(repository.existsBySlug("sari-budi")).thenReturn(false);

		Invitation created = service.create(userId, "aurora", "Sari", "Budi", eventDate);

		assertThat(created.getStatus()).isEqualTo(InvitationStatus.DRAFT);
		assertThat(created.getSlug()).isEqualTo("sari-budi");
		assertThat(created.getTemplateVersion()).isEqualTo("1.0.0");
		assertThat(created.getContentVersion()).isEqualTo(1);
		assertThat(created.getContent()).isEqualTo("{}");
	}

	@Test
	void createDenganTemplateAsingDitolak() {
		when(catalogClient.resolveTemplate("template-hack")).thenThrow(new UnknownTemplateException());

		assertThatThrownBy(() -> service.create(userId, "template-hack", "Sari", "Budi", eventDate))
				.isInstanceOf(UnknownTemplateException.class);
		verify(repository, never()).save(any(Invitation.class));
	}

	@Test
	void createDenganTanggalLampauDitolak() {
		assertThatThrownBy(() -> service.create(userId, "aurora", "Sari", "Budi", LocalDate.now().minusDays(1)))
				.isInstanceOf(EventDateInvalidException.class);
	}

	@Test
	void createMelebihiKuotaDraftDitolak() {
		when(repository.countByUserIdAndStatus(userId, InvitationStatus.DRAFT)).thenReturn(3L);

		assertThatThrownBy(() -> service.create(userId, "aurora", "Sari", "Budi", eventDate))
				.isInstanceOf(DraftQuotaExceededException.class);
	}

	@Test
	void slugBentrokDiberiSuffixAcakHinggaUnik() {
		when(repository.countByUserIdAndStatus(userId, InvitationStatus.DRAFT)).thenReturn(0L);
		when(repository.existsBySlug("sari-budi")).thenReturn(true);
		when(repository.existsBySlug(org.mockito.ArgumentMatchers.startsWith("sari-budi-"))).thenReturn(false);

		Invitation created = service.create(userId, "aurora", "Sari", "Budi", eventDate);

		ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
		verify(repository).save(captor.capture());
		assertThat(created.getSlug()).startsWith("sari-budi-");
	}

	@Test
	void patchContentDenganVersiUsangDitolak() {
		Invitation existing = Invitation.newDraft(userId, "aurora", "1.0.0", "sari-budi", "Sari", "Budi", eventDate);
		when(repository.findByIdAndUserIdAndStatusNot(existing.getId(), userId, InvitationStatus.ARCHIVED))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.patchContent(userId, existing.getId(), Map.of("kunci", "nilai"), 5))
				.isInstanceOf(VersionConflictException.class)
				.satisfies(e -> assertThat(((VersionConflictException) e).getCurrentVersion()).isEqualTo(1));
	}

	@Test
	void patchContentKontenKosongDitolak() {
		Invitation existing = Invitation.newDraft(userId, "aurora", "1.0.0", "sari-budi", "Sari", "Budi", eventDate);
		when(repository.findByIdAndUserIdAndStatusNot(existing.getId(), userId, InvitationStatus.ARCHIVED))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.patchContent(userId, existing.getId(), Map.of(), 1))
				.isInstanceOf(ContentInvalidException.class);
	}

	@Test
	void patchContentTerlaluBesarDitolak() {
		InvitationService smallProps = new InvitationService(repository,
				new InvitationProperties(3, 10, 5), new ObjectMapper(), catalogClient);
		Invitation existing = Invitation.newDraft(userId, "aurora", "1.0.0", "sari-budi", "Sari", "Budi", eventDate);
		when(repository.findByIdAndUserIdAndStatusNot(existing.getId(), userId, InvitationStatus.ARCHIVED))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(
				() -> smallProps.patchContent(userId, existing.getId(), Map.of("kunci", "nilai-panjang"), 1))
				.isInstanceOf(ContentTooLargeException.class);
	}

	@Test
	void patchContentVersiCocokMenambahVersi() {
		Invitation existing = Invitation.newDraft(userId, "aurora", "1.0.0", "sari-budi", "Sari", "Budi", eventDate);
		when(repository.findByIdAndUserIdAndStatusNot(existing.getId(), userId, InvitationStatus.ARCHIVED))
				.thenReturn(Optional.of(existing));

		Invitation updated = service.patchContent(userId, existing.getId(),
				Map.of("cover", Map.of("salam", "Assalamu'alaikum")), 1);

		assertThat(updated.getContentVersion()).isEqualTo(2);
		assertThat(updated.getContent()).contains("Assalamu'alaikum");
	}
}
