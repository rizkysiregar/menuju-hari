package com.menujuhari.media.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cleanup terjadwal (MEDIA-004): buang PENDING yang tak pernah selesai (>24 jam)
 * dan FAILED lama (>7 hari).
 * Catatan batas MVP: pembersihan aset yang sudah tak direferensikan konten
 * undangan membutuhkan data invitation-service — menyusul via event.
 */
@Component
public class MediaCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

	private final MediaService mediaService;

	public MediaCleanupJob(MediaService mediaService) {
		this.mediaService = mediaService;
	}

	@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Jakarta")
	@Transactional
	public void cleanupDaily() {
		Instant now = Instant.now();
		int removed = mediaService.cleanupExpired(now.minusSeconds(86_400), now.minusSeconds(7 * 86_400));
		if (removed > 0) {
			log.info("Cleanup media: {} aset dihapus", removed);
		}
	}
}
