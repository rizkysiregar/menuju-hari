package com.menujuhari.catalog.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

	Optional<TemplateVersion> findFirstByTemplateIdAndStatusOrderByVersionDesc(UUID templateId,
			TemplateVersion.Status status);
}
