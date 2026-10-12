package com.menujuhari.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PackageRepository extends JpaRepository<Package, UUID> {

	List<Package> findByActiveTrueOrderBySortOrderAsc();

	List<Package> findAllByOrderBySortOrderAsc();

	Optional<Package> findByCode(String code);
}
