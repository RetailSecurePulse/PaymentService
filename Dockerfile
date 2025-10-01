# ---- Stage 1: Build with Gradle ----
FROM gradle:8.13.0-jdk23 AS builder

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
FROM eclipse-temurin:23-jdk-alpine

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# # Change ownership to non-root user
# RUN chown appuser:appgroup app.jar

# # Switch to non-root user
# USER appuser

# Expose port 8085
EXPOSE 8085

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]