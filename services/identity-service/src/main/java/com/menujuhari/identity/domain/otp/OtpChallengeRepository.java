package com.menujuhari.identity.domain.otp;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

	Optional<OtpChallenge> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, OtpStatus status);

	@Modifying
	@Query("update OtpChallenge o set o.attempts = o.attempts + 1 where o.id = :id")
	int incrementAttempts(@Param("id") UUID id);

	@Modifying
	@Query("update OtpChallenge o set o.status = :status where o.id = :id")
	int updateStatus(@Param("id") UUID id, @Param("status") OtpStatus status);

	@Modifying
	@Query("""
			delete from OtpChallenge o
			where o.expiresAt < :now
			   or (o.status in :finishedStatuses and o.createdAt < :cutoff)
			""")
	int cleanup(@Param("now") Instant now, @Param("finishedStatuses") Collection<OtpStatus> finishedStatuses,
			@Param("cutoff") Instant cutoff);
}
