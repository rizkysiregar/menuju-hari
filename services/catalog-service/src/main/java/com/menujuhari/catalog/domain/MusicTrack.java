package com.menujuhari.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "music_tracks")
public class MusicTrack {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 200)
	private String artist;

	@Column(name = "object_key", nullable = false, length = 300)
	private String objectKey;

	/** Kewajiban legal [K13]: setiap track wajib punya catatan lisensi. */
	@Column(name = "license_note", nullable = false, length = 500)
	private String licenseNote;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MusicTrack() {
	}

	public UUID getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getArtist() {
		return artist;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public String getLicenseNote() {
		return licenseNote;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public boolean isActive() {
		return active;
	}
}
