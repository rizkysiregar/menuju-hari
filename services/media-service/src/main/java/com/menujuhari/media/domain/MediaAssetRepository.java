package com.menujuhari.media.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

	Optional<MediaAsset> findByIdAndUserId(UUID id, UUID userId);

	long countByUserIdAndKindAndStatusIn(UUID userId, MediaKind kind, List<MediaStatus> statuses);

	List<MediaAsset> findByStatusAndCreatedAtBefore(MediaStatus status, Instant cutoff);
}
