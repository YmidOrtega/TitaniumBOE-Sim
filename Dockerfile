# Multi-stage build for optimal image size
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml ./

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source and frontend
COPY src ./src
COPY frontend ./frontend

# Build: Astro frontend (via frontend-maven-plugin) + fat JAR
RUN mvn clean package -DskipTests -B

# Runtime stage - smaller image
FROM eclipse-temurin:21-jre-alpine

# Add metadata
LABEL maintainer="yortegap7920@gmail.com"
LABEL description="CBOE BOE Protocol Simulator"
LABEL version="1.0.0"

# Install curl for healthchecks
RUN apk add --no-cache curl

WORKDIR /app

# Copy only the JAR file from builder
COPY --from=builder /app/target/boe-simulator-*.jar /app/boe-simulator.jar

# Create data directory for RocksDB persistence
RUN mkdir -p /app/data && \
    chmod 755 /app/data

# Create non-root user for security
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose ports (REST API/dashboard + BOE binary protocol)
EXPOSE 8081 9090

# Health check against the API port (override with API_PORT or PORT env var)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${API_PORT:-8081}/api/health || exit 1

# Environment variables with defaults
ENV DEMO_MODE=true \
    API_PORT=8081 \
    LOG_LEVEL=INFO

# Run the application
ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-jar", \
            "boe-simulator.jar"]

# Optional: Add JVM debugging (uncomment if needed)
# ENTRYPOINT ["java", \
#             "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
#             "-jar", \
#             "boe-simulator.jar"]
# EXPOSE 5005
