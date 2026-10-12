package com.menujuhari.invitation.application;

/**
 * Kontrak antar-service: invitation-service → catalog-service (TPL-003).
 * Implementasi produksi: HttpCatalogClient (cache Redis + timeout ketat).
 */
public interface CatalogClient {

	record TemplateInfo(String code, String name, String activeVersion) {
	}

	/**
	 * Validasi template & ambil versi aktifnya. Melempar
	 * UnknownTemplateException (404) atau CatalogUnavailableException (503).
	 */
	TemplateInfo resolveTemplate(String code);
}
