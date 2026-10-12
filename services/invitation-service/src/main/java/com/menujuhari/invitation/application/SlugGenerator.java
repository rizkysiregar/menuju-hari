package com.menujuhari.invitation.application;

import java.util.stream.Collectors;

/**
 * Generator slug undangan (INV-001): nama mempelai + suffix acak bila perlu.
 * Format: ^[a-z0-9]+(-[a-z0-9]+)*$, maks 160 karakter.
 */
public final class SlugGenerator {

	private static final int MAX_NAME_PART = 40;

	private SlugGenerator() {
	}

	public static String slugify(String name) {
		String slug = name == null ? "" : name.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		if (slug.length() > MAX_NAME_PART) {
			slug = slug.substring(0, MAX_NAME_PART).replaceAll("-+$", "");
		}
		return slug.isBlank() ? "pengantin" : slug;
	}

	public static String baseSlug(String brideName, String groomName) {
		return slugify(brideName) + "-" + slugify(groomName);
	}

	public static boolean valid(String slug) {
		return slug != null && slug.matches("^[a-z0-9]+(-[a-z0-9]+)*$") && slug.length() <= 160;
	}

	public static String randomSuffix() {
		// 4 karakter hex acak — cukup membedakan slug nama umum
		String suffix = java.util.UUID.randomUUID().toString().substring(0, 4);
		return suffix.chars()
				.mapToObj(c -> String.valueOf((char) c))
				.collect(Collectors.joining());
	}
}
