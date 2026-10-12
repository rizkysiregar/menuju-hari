package com.menujuhari.invitation.domain;

/**
 * Status undangan (PROJECT_PLAN §8.3) — dipisah dari status order & pembayaran.
 * Transisi legal:
 *   DRAFT -> ELIGIBLE (event payment.settled) -> PUBLISHED (konten valid)
 *   PUBLISHED -> HIDDEN (unpublish oleh pemilik) -> PUBLISHED
 *   PUBLISHED -> EXPIRED (event_date + 30 hari [K2]) -> ARCHIVED
 * Versi ini hanya mengelola DRAFT dan ARCHIVED (hasil delete);
 * transisi pembayaran & kedaluwarsa menyusul bersama order/payment.
 */
public enum InvitationStatus {

	DRAFT, ELIGIBLE, PUBLISHED, EXPIRED, ARCHIVED, HIDDEN;

	/** Konten masih bisa diedit pemilik? */
	public boolean editable() {
		return this != ARCHIVED && this != EXPIRED;
	}

	/** Dapat dihapus: draft dihapus permanen, lainnya diarsipkan (INV-008). */
	public boolean deletionIsHard() {
		return this == DRAFT;
	}
}
