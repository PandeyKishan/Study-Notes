# ========================================================
# Stage 1: Build the Spring Boot JAR with Maven Cache
# ========================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Copy pom.xml first to cache Maven dependency downloads
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ========================================================
# Stage 2: Extract Layered JAR (Dependencies vs App Code)
# ========================================================
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /extracted
COPY --from=builder /workspace/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract || true

# ========================================================
# Stage 3: Minimal, Hardened Production Container
# ========================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /application

# Container Security: Run as unprivileged non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy extracted layers (Unchanged layers stay cached!)
COPY --from=extractor /extracted/app.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    PORT=8081

EXPOSE 8081

# Built-in healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=20s --timeout=3s --start-period=20s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
