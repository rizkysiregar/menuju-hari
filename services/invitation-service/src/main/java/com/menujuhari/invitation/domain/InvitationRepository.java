package com.menujuhari.invitation.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	Page<Invitation> findByUserIdAndStatusNotOrderByCreatedAtDesc(UUID userId, InvitationStatus excluded,
			Pageable pageable);

	Page<Invitation> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, InvitationStatus status,
			Pageable pageable);

	long countByUserIdAndStatus(UUID userId, InvitationStatus status);

	boolean existsBySlug(String slug);

	Optional<Invitation> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, InvitationStatus excluded);
}
