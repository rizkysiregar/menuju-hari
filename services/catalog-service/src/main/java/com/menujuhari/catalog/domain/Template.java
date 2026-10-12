package com.menujuhari.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "templates")
public class Template {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, length = 64)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Template() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}
}
