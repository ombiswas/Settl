# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper and POM for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and build production artifact
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal runtime image using lightweight JRE
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Add unprivileged user for security
RUN addgroup -S settl && adduser -S settl -G settl
USER settl:settl

# Copy JAR from builder stage
COPY --from=builder --chown=settl:settl /build/target/*.jar app.jar

# Configuration environment variables
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
