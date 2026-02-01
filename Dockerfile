# ============================================
# Multi-Stage Dockerfile for Spring Boot
# Music Web Application with S3, Redis, PostgreSQL
# ============================================

# Stage 1: BUILD
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (cached if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application (skip tests for faster builds)
RUN mvn clean package -DskipTests

# Stage 2: RUNTIME
FROM eclipse-temurin:21-jre-jammy

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Set working directory
WORKDIR /app

# Create directories with correct ownership in one layer
RUN mkdir -p /app/song-cache && \
    chown -R spring:spring /app

# Copy JAR from builder stage with correct ownership
COPY --from=builder --chown=spring:spring /build/target/*.jar app.jar

# Environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=70.0 \
  -XX:+UseG1GC \
  -XX:G1HeapRegionSize=16m \
  -Djava.security.egd=file:/dev/./urandom"

# Expose port
EXPOSE 8080

# Switch to non-root user
USER spring:spring

# Health check - localhost only, never hardcode IP
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]