package com.menujuhari.shared.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuration keamanan bersama. Setiap service mendapatkan:
 * filter sesi cookie, filter Origin (mitigasi CSRF), CORS, permit-all
 * per service, dan OpenAPI (Swagger UI) dengan skema cookie.
 */
@AutoConfiguration
@EnableWebSecurity
@EnableConfigurationProperties(SecuritySharedProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SharedSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(SessionService.class)
	SessionService sessionService(StringRedisTemplate redis, ObjectMapper objectMapper,
			SecuritySharedProperties props) {
		return new SessionService(redis, objectMapper, props);
	}

	@Bean
	@ConditionalOnMissingBean(SessionCookieFactory.class)
	SessionCookieFactory sessionCookieFactory(SecuritySharedProperties props) {
		return new SessionCookieFactory(props);
	}

	@Bean
	@ConditionalOnMissingBean(SecurityFilterChain.class)
	SecurityFilterChain sharedSecurityFilterChain(HttpSecurity http, SessionService sessionService,
			SecuritySharedProperties props) throws Exception {
		var originCheckFilter = new OriginCheckFilter(props);
		var sessionAuthFilter = new SessionAuthenticationFilter(sessionService, props);

		List<String> permitAll = new ArrayList<>();
		if (props.permitAllPaths() != null) {
			permitAll.addAll(props.permitAllPaths());
		}
		if (props.apiDocsEnabled()) {
			permitAll.add("/v3/api-docs/**");
			permitAll.add("/swagger-ui/**");
			permitAll.add("/swagger-ui.html");
			permitAll.add("/docs");
		}

		http.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					if (!permitAll.isEmpty()) {
						auth.requestMatchers(permitAll.toArray(String[]::new)).permitAll();
					}
					auth.requestMatchers(HttpMethod.GET, "/").permitAll();
					auth.anyRequest().authenticated();
				})
				.exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, ex) -> {
					response.setStatus(401);
					response.setContentType("application/json");
					response.getWriter().write(
							"{\"code\":\"UNAUTHORIZED\",\"message\":\"Sesi tidak valid. Silakan masuk kembali.\"}");
				}))
				.addFilterBefore(originCheckFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	private CorsConfigurationSource corsConfigurationSource(SecuritySharedProperties props) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(props.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(Duration.ofHours(1));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	@ConditionalOnProperty(name = "mh.security.api-docs-enabled", havingValue = "true", matchIfMissing = true)
	OpenAPI sharedOpenApi(SecuritySharedProperties props) {
		String cookieName = props.cookieName();
		return new OpenAPI()
				.info(new Info()
						.title(props.openapi().title())
						.description(props.openapi().description())
						.version(props.openapi().version()))
				.components(new Components().addSecuritySchemes("sessionCookie",
						new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name(cookieName)
								.description("Sesi httpOnly. Jalankan POST /v1/auth/otp/request lalu "
										+ "/v1/auth/otp/verify di Swagger UI ini; cookie tersimpan otomatis "
										+ "di browser dan endpoint terlindungi bisa diuji langsung.")));
	}
}
