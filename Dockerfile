# Multi-stage build for Spring Boot application
# Optimized for build speed and security

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ ./src/

# Build the application (skip tests for faster builds, tests should run in CI)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime image
# Option 1: Use pre-built base image with LibreOffice (faster builds, ~2-3min saved)
#   First build base: docker build -f Dockerfile.libreoffice-base -t hhmmss-libreoffice-base:latest .
#   Then build app:   docker build --build-arg BASE_IMAGE=hhmmss-libreoffice-base:latest -t hhmmss:latest .
# Option 2: Build from scratch (default, simpler, no separate base image needed)
#   Build:            docker build -t hhmmss:latest .

# Default to base JRE image (can be overridden to use LibreOffice base image)
ARG BASE_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${BASE_IMAGE} AS runtime-base

# Install LibreOffice and fonts
# This step is skipped if using hhmmss-libreoffice-base as BASE_IMAGE
# To use base image: docker build --build-arg BASE_IMAGE=hhmmss-libreoffice-base:latest .
# hadolint ignore=DL3018
RUN if ! command -v soffice >/dev/null 2>&1; then \
    apk add --no-cache libreoffice ttf-dejavu fontconfig && \
    mkdir -p /tmp/.libreoffice && chmod 1777 /tmp/.libreoffice; \
    fi

# Stage 3: Final application image
FROM runtime-base

# Install wget for healthcheck (minimal overhead)
# hadolint ignore=DL3018
RUN apk add --no-cache wget

# Create a non-root user to run the application
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built artifact from builder stage
COPY --from=builder /build/target/*.war app.war

# Create directory for uploaded files with correct permissions
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# Switch to non-root user for security
USER spring:spring

# Expose the application port
EXPOSE 8080

# Health check with optimized settings
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Optimized JVM options for containerized environment
# - UseContainerSupport: Respect container memory limits
# - MaxRAMPercentage: Use up to 75% of available memory
# - UseStringDeduplication: Reduce memory footprint
# - TieredCompilation: Balance startup time and performance
# - MinHeapFreeRatio/MaxHeapFreeRatio: Optimize heap sizing
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:MinHeapFreeRatio=20 \
    -XX:MaxHeapFreeRatio=40 \
    -Djava.security.egd=file:/dev/./urandom \
    -Djava.awt.headless=true"

# Set Spring profile to use docker-specific configuration
ENV SPRING_PROFILES_ACTIVE=docker

# Run the application with exec form for proper signal handling
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.war"]
