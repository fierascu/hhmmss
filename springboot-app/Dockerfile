# Multi-stage build for the Spring Boot app module.
# Build context must be the repository root (so this stage can see the
# reactor pom.xml and the sibling core/ module), not this springboot-app/ directory.

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-26-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and reactor pom.xml first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY core/pom.xml core/pom.xml
COPY springboot-app/pom.xml springboot-app/pom.xml

# Download dependencies (this layer will be cached if the pom.xml files don't change)
RUN ./mvnw -pl springboot-app -am dependency:go-offline -B

# Copy source code
COPY core/src/ core/src/
COPY springboot-app/src/ springboot-app/src/

# Build the application module and its core dependency (skip tests for faster builds, tests should run in CI)
RUN ./mvnw -pl springboot-app -am clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:26-jre-alpine

# Install LibreOffice for PDF conversion via JodConverter
RUN apk add --no-cache \
    libreoffice \
    ttf-dejavu \
    fontconfig

# Create a non-root user to run the application
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built artifact from builder stage
COPY --from=builder /build/springboot-app/target/*.war app.war

# Create directory for uploaded files
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Set Spring profile to use docker-specific configuration
ENV SPRING_PROFILES_ACTIVE=docker

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
