package com.menujuhari.identity.domain.otp;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

	@Id
	@UuidGenerator
	private UUID id;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(nullable = false, length = 32)
	private String salt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OtpStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected OtpChallenge() {
	}

	public OtpChallenge(String email, String codeHash, String salt, Instant expiresAt) {
		this.email = email;
		this.codeHash = codeHash;
		this.salt = salt;
		this.status = OtpStatus.ACTIVE;
		this.attempts = 0;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public String getSalt() {
		return salt;
	}

	public OtpStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
