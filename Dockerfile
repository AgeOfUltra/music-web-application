# ============================================
# Multi-Stage Dockerfile for Spring Boot
# Music Web Application with S3, Redis, PostgreSQL
# ============================================

# Stage 1: BUILD
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./


RUN chmod +x mvnw
# Download dependencies (cached if pom.xml unchanged)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests

# Stage 2: RUNTIME
FROM eclipse-temurin:21-jre

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Set working directory
WORKDIR /app

# Create directory for song cache
RUN mkdir -p /app/song-cache && chown -R spring:spring /app/song-cache

# Copy JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown spring:spring app.jar

# Environment variables (can be overridden by docker-compose)
ENV SPRING_PROFILES_ACTIVE=machine1
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose port
EXPOSE 8080

# Switch to non-root user
USER spring:spring

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
