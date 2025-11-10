# Multi-stage build for optimal image size
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (for better caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage - smaller image
FROM eclipse-temurin:21-jre-alpine

# Add metadata
LABEL maintainer="your-email@example.com"
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

# Expose ports
EXPOSE 8081 9091

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:9091/api/health || exit 1

# Environment variables with defaults
ENV DEMO_MODE=true \
    BOE_PORT=8081 \
    API_PORT=9091 \
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
