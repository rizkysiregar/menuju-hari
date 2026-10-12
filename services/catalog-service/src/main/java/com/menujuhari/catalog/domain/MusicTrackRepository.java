package com.menujuhari.catalog.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicTrackRepository extends JpaRepository<MusicTrack, UUID> {

	List<MusicTrack> findByActiveTrueOrderByTitle();
}
