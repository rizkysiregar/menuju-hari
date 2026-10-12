package com.menujuhari.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Klien catalog-service dengan timeout ketat. Catalog down -> 503 ke klien.
 */
@Component
public class HttpCatalogClient implements CatalogClient {

	private final RestClient rest;

	public HttpCatalogClient(
			@Value("${app.order.catalog-base-url:http://localhost:8083}") String baseUrl) {
		org.springframework.http.client.SimpleClientHttpRequestFactory factory =
				new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(3_000);
		factory.setReadTimeout(5_000);
		this.rest = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
	}

	@Override
	public PackageInfo getPackage(String code) {
		try {
			PackageInfo info = rest.get()
					.uri("/internal/catalog/packages/{code}", (code == null ? "" : code.trim().toLowerCase()))
					.retrieve()
					.body(PackageInfo.class);
			if (info == null) {
				throw new CatalogUnavailableException();
			}
			return info;
		}
		catch (HttpStatusCodeException e) {
			if (e.getStatusCode().value() == 404) {
				throw new PackageNotFoundException();
			}
			throw new CatalogUnavailableException();
		}
		catch (RestClientException e) {
			throw new CatalogUnavailableException();
		}
	}

	public static class PackageNotFoundException extends RuntimeException {
		public PackageNotFoundException() {
			super("Paket tidak ditemukan");
		}
	}

	public static class CatalogUnavailableException extends RuntimeException {
		public CatalogUnavailableException() {
			super("Katalog sedang tidak tersedia");
		}
	}
}
