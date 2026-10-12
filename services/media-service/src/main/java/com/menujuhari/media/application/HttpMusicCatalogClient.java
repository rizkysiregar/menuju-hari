package com.menujuhari.media.application;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.ObjectMapper;

/**
 * Klien catalog-service dengan cache Redis (10 menit) dan timeout ketat,
 * pola sama dengan HttpCatalogClient di invitation-service.
 */
@Component
public class HttpMusicCatalogClient implements MusicCatalogClient {

	private static final Duration CACHE_TTL = Duration.ofMinutes(10);

	private final RestClient rest;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public HttpMusicCatalogClient(
			@Value("${app.catalog.base-url:http://localhost:8083}") String baseUrl,
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
	public TrackInfo getTrack(UUID trackId) {
		String cacheKey = "catalog:music:" + trackId;

		String cached = redis.opsForValue().get(cacheKey);
		if (cached != null) {
			try {
				return objectMapper.readValue(cached, TrackInfo.class);
			}
			catch (Exception e) {
				redis.delete(cacheKey);
			}
		}

		try {
			TrackInfo info = rest.get()
					.uri("/internal/catalog/music/{id}", trackId.toString())
					.retrieve()
					.body(TrackInfo.class);
			if (info == null) {
				throw new CatalogUnavailableException();
			}
			redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(info), CACHE_TTL);
			return info;
		}
		catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new TrackNotFoundException();
			}
			throw new CatalogUnavailableException();
		}
		catch (RestClientException e) {
			throw new CatalogUnavailableException();
		}
	}
}
