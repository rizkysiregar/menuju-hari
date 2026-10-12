package com.menujuhari.identity.application;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.menujuhari.identity.domain.otp.OtpChallengeRepository;
import com.menujuhari.identity.domain.otp.OtpStatus;

/**
 * Housekeeping: buang challenge kedaluwarsa & selesai lebih tua dari 1 hari.
 * (Job mandiri di service ini; boleh dipindah ke job infrastruktur nanti.)
 */
@Component
public class OtpCleanupJob {

	private final OtpChallengeRepository otpRepository;

	public OtpCleanupJob(OtpChallengeRepository otpRepository) {
		this.otpRepository = otpRepository;
	}

	@Scheduled(fixedDelay = 3600_000, initialDelay = 600_000)
	@Transactional
	public void cleanup() {
		Instant now = Instant.now();
		int removed = otpRepository.cleanup(now, java.util.List.of(OtpStatus.VERIFIED, OtpStatus.EXPIRED,
				OtpStatus.EXHAUSTED), now.minus(Duration.ofDays(1)));
		if (removed > 0) {
			org.slf4j.LoggerFactory.getLogger(OtpCleanupJob.class).info("Cleanup OTP: {} baris dihapus", removed);
		}
	}
}
