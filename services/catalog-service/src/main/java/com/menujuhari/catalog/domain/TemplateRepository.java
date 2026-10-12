package com.menujuhari.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

	Optional<Template> findByCode(String code);

	List<Template> findByActiveTrue();
}
