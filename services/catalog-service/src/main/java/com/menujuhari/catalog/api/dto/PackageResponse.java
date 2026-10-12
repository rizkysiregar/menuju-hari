package com.menujuhari.catalog.api.dto;

import java.util.Map;

import com.menujuhari.catalog.domain.Package;

public record PackageResponse(String code, String name, int priceIdr, Map<String, Object> featureFlags,
		boolean active) {

	public static PackageResponse from(Package pkg, Map<String, Object> flags) {
		return new PackageResponse(pkg.getCode(), pkg.getName(), pkg.getPriceIdr(), flags, pkg.isActive());
	}
}
