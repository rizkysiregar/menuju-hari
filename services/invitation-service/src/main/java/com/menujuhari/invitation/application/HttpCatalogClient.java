package com.menujuhari.invitation.application;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.ObjectMapper;

/**
 * Klien catalog-service dengan cache Redis (positif 10 menit, negatif 2 menit)
 * dan timeout ketat (connect 3 s / read 5 s) sesuai prinsip panggilan
 * antar-service di PROJECT_PLAN §7.2. Catalog down -> CatalogUnavailableException
 * (503 ke klien), bukan fallback diam-diam ke allowlist.
 */
@Component
public class HttpCatalogClient implements CatalogClient {

	private static final Duration POSITIVE_TTL = Duration.ofMinutes(10);
	private static final Duration NEGATIVE_TTL = Duration.ofMinutes(2);

	private final RestClient rest;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public HttpCatalogClient(
			@Value("${app.invitation.catalog-base-url:http://localhost:8083}") String baseUrl,
			StringRedisTemplate redis, ObjectMapper objectMapper) {
		org.springframework.http.client.SimpleClientHttpRequestFactory factory =
				new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(3_000);
		factory.setReadTimeout(5_000);
		this.rest = RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	@Override
	public TemplateInfo resolveTemplate(String code) {
		String cacheKey = "catalog:tpl:" + (code == null ? "" : code.trim().toLowerCase());

		String cached = redis.opsForValue().get(cacheKey);
		if (cached != null) {
			if ("MISS".equals(cached)) {
				throw new UnknownTemplateException();
			}
			try {
				return objectMapper.readValue(cached, TemplateInfo.class);
			}
			catch (Exception e) {
				// Cache korup -> anggap tidak ada, lanjut ke HTTP
				redis.delete(cacheKey);
			}
		}

		try {
			TemplateInfo info = rest.get()
					.uri("/internal/catalog/templates/{code}", (code == null ? "" : code.trim().toLowerCase()))
					.retrieve()
					.body(TemplateInfo.class);
			if (info == null) {
				throw new CatalogUnavailableException();
			}
			redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(info), POSITIVE_TTL);
			return info;
		}
		catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				redis.opsForValue().set(cacheKey, "MISS", NEGATIVE_TTL);
				throw new UnknownTemplateException();
			}
			throw new CatalogUnavailableException();
		}
		catch (RestClientException e) {
			throw new CatalogUnavailableException();
		}
	}
}
