# Dockerfile generik untuk semua backend service Menuju Hari.
# Kompatibel dengan LEGACY builder (tanpa BuildKit/buildx) — dipilih agar
# berjalan di daemon Docker server Jenkins apa pun versinya.
# Build dari CONTEXT = root monorepo:
#   docker build -f services/Dockerfile --build-arg SERVICE=identity -t rizkysiregar/menujuhari-identity:<tag> .

FROM maven:3.9-eclipse-temurin-25 AS build
ARG SERVICE
WORKDIR /src

# POM dulu (layer cache): dependensi tidak diunduh ulang selama pom tak berubah
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY services/shared/security-starter/pom.xml services/shared/security-starter/
COPY services/identity-service/pom.xml services/identity-service/
COPY services/invitation-service/pom.xml services/invitation-service/
COPY services/catalog-service/pom.xml services/catalog-service/
COPY services/media-service/pom.xml services/media-service/
COPY services/order-service/pom.xml services/order-service/
COPY services/payment-service/pom.xml services/payment-service/
# Pre-download dependensi (best-effort — kegagalan tidak mematikan build)
RUN mvn -B -q -DskipTests dependency:go-offline || true

# Sisa source, lalu build module target (+ dependensi reactor via -am)
COPY . .
RUN mvn -B -q -pl services/${SERVICE}-service -am package -DskipTests

FROM amazoncorretto:25-alpine
ARG SERVICE
WORKDIR /app
COPY --from=build /src/services/${SERVICE}-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
