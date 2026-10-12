package com.menujuhari.media.application;

import java.util.UUID;

/**
 * Kontrak antar-service: media-service → catalog-service (MEDIA-005).
 * Mengambil object key track musik dari katalog berlisensi.
 */
public interface MusicCatalogClient {

	record TrackInfo(String id, String title, String objectKey, int durationSeconds) {
	}

	TrackInfo getTrack(UUID trackId);
}
