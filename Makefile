# Menuju Hari — perintah pengembangan lokal

.PHONY: deps-up deps-down identity-run identity-test

deps-up:
	docker compose -f deploy/docker-compose.yml up -d

deps-down:
	docker compose -f deploy/docker-compose.yml down

identity-run: deps-up
	cd services/identity-service && ./mvnw spring-boot:run -q

identity-test:
	cd services/identity-service && ./mvnw test -q
