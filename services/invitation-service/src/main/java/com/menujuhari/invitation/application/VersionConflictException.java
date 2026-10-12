package com.menujuhari.invitation.application;

/**
 * Optimistic concurrency (INV-004): konten yang dikirim dibuat dari versi
 * yang sudah usang. Membawa currentVersion agar klien bisa memutuskan
 * (muat ulang / paksa tulis).
 */
public class VersionConflictException extends RuntimeException {

	private final int currentVersion;

	public VersionConflictException(int currentVersion) {
		super("Konten telah berubah di tempat lain");
		this.currentVersion = currentVersion;
	}

	public int getCurrentVersion() {
		return currentVersion;
	}
}
