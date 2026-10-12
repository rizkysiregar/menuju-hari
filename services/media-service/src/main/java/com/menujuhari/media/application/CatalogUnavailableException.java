package com.menujuhari.media.application;

public class CatalogUnavailableException extends RuntimeException {

	public CatalogUnavailableException() {
		super("Katalog sedang tidak tersedia");
	}
}
