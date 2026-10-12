package com.menujuhari.shared.security;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Penemu .env yang tahan-banting (menggantikan spring.config.import relatif).
 *
 * Masalah yang diselesaikan: file .env sebelumnya di-resolve RELATIF terhadap
 * working directory aplikasi. Bila aplikasi dijalankan dari IDE dengan working
 * directory yang bukan root repo / directory service, .env tidak ditemukan dan
 * aplikasi diam-diam jalan dengan default (password DB kosong -> error SCRAM).
 *
 * Mekanisme: berjalan dari user.dir lalu NAIK ke atas directory tree sampai
 * menemukan (a) <dir>/<mh.env.file> atau (b) <dir>/.env. File pertama yang
 * ditemukan dimuat sebagai property source dengan urutan:
 *   OS env vars  >  .env  >  application.yml
 * sehingga override lewat env var sistem (mis. MAIL_CONSOLE_FALLBACK=true)
 * tetap berlaku.
 *
 * Registrasi: META-INF/spring.factories (kontrak EnvironmentPostProcessor).
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

	static final String SOURCE_NAME_PREFIX = "mhDotEnv:";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		MutablePropertySources sources = environment.getPropertySources();

		boolean alreadyLoaded = sources.stream().anyMatch(source -> source.getName().startsWith(SOURCE_NAME_PREFIX));
		if (alreadyLoaded) {
			return;
		}
		if (environment.getProperty("mh.env.disabled", Boolean.class, false)) {
			return;
		}

		// Path target relatif dari root repo, diatur per service (mh.env.file).
		String relative = environment.getProperty("mh.env.file", String.class);

		Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (true) {
			List<Path> candidates = new ArrayList<>();
			if (relative != null && !relative.isBlank()) {
				candidates.add(dir.resolve(relative));
			}
			candidates.add(dir.resolve(".env"));

			for (Path candidate : candidates) {
				if (Files.isRegularFile(candidate)) {
					register(environment, candidate);
					return;
				}
			}

			Path parent = dir.getParent();
			if (parent == null || parent.equals(dir)) {
				return;
			}
			dir = parent;
		}
	}

	private void register(ConfigurableEnvironment environment, Path file) {
		Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			properties.load(in);
		}
		catch (Exception e) {
			// .env tidak valid -> biarkan default; jangan gagalkan startup di sini.
			return;
		}

		Map<String, Object> values = new LinkedHashMap<>();
		properties.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));

		MapPropertySource source = new MapPropertySource(SOURCE_NAME_PREFIX + file, values);
		MutablePropertySources sources = environment.getPropertySources();
		if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
			sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
		}
		else {
			sources.addLast(source);
		}
	}
}
