package com.menujuhari.payment.domain;

/**
 * Status pembayaran (PROJECT_PLAN §8.3) — dipetakan dari transaction_status
 * Midtrans. Transisi legal (PAY-002/004): maju saja, tidak mundur.
 */
public enum PaymentStatus {
	PENDING, SETTLED, DENIED, CANCELLED, EXPIRED, REFUNDED;

	/** Settlement terlambat boleh menggantikan EXPIRED (uang nyata masuk). */
	public boolean canTransitionTo(PaymentStatus target) {
		if (this == target) {
			return false; // idempoten — tidak ada perubahan
		}
		if (this == PENDING) {
			return true;
		}
		if (this == EXPIRED) {
			return target == SETTLED || target == REFUNDED;
		}
		if (this == SETTLED) {
			return target == REFUNDED;
		}
		return false; // DENIED/CANCELLED/REFUNDED terminal
	}
}
