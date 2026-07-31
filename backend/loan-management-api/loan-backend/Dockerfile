# ---- Stage 1: Build ----
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build app
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: run as non-root
RUN addgroup -S loansaas && adduser -S loansaas -G loansaas
RUN mkdir -p /var/log/loansaas && chown loansaas:loansaas /var/log/loansaas

COPY --from=builder /app/target/*.jar app.jar
RUN chown loansaas:loansaas app.jar

USER loansaas
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
