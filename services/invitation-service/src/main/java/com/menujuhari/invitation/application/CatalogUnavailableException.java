package com.menujuhari.invitation.application;

public class CatalogUnavailableException extends RuntimeException {

	public CatalogUnavailableException() {
		super("Katalog sedang tidak tersedia");
	}
}
