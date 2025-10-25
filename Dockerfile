# ---- Stage 1: Build with Gradle ----
FROM gradle:8.13.0-jdk21-noble AS builder

# Set working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (this layer can be cached if sources don't change)
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# Copy source code
COPY src src

# Build the application (produces spring-boot-api-2.0.0.jar)
RUN ./gradlew clean bootJar --no-daemon

# ---- Stage 2: Run with minimal JDK ----
FROM eclipse-temurin:21-jre-noble

# Install curl + tzdata (Debian-based image)
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl tzdata ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}" \
    LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appgroup && useradd -r -g appgroup appuser && chown -R appuser:appgroup /app
USER appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose port 8087
EXPOSE 8087

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]

LABEL authors="aungtuntun"