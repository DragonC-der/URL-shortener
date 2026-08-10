# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies separately from source changes for faster rebuilds
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/url-shortener-1.0.0.jar app.jar

# Render assigns the actual port via the PORT env var at runtime -
# application.yml already reads server.port: ${PORT:8080}, so no
# hardcoded port here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
