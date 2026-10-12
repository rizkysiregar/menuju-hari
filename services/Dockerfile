# syntax=docker/dockerfile:1
# Dockerfile generik untuk semua backend service Menuju Hari.
# Build dari CONTEXT = root monorepo:
#   docker build -f services/Dockerfile --build-arg SERVICE=identity -t rizkysiregar/menujuhari-identity:<tag> .

FROM maven:3.9-eclipse-temurin-25 AS build
ARG SERVICE
WORKDIR /src
COPY . .
# Cache dependensi Maven antar build (BuildKit cache mount)
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl services/${SERVICE}-service -am package -DskipTests

FROM amazoncorretto:25-alpine
ARG SERVICE
WORKDIR /app
COPY --from=build /src/services/${SERVICE}-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
