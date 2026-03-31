# ---- Stage 1: Build ----
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Cache dependencies first
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre

# Install dcmtk (provides dcmcjpls)
RUN apt-get update && \
    apt-get install -y --no-install-recommends dcmtk && \
    rm -rf /var/lib/apt/lists/*

# Verify dcmcjpls is available
RUN which dcmcjpls && dcmcjpls --version || true

WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/*.jar app.jar

# Copy frontend (served by Spring Boot or a reverse proxy)
COPY frontend ./frontend

# Create directories the app expects
RUN mkdir -p /app/temp /app/archive /app/tools

# Expose the application port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
