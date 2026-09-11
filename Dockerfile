# --- Frontend build stage ---
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run lint && npm run format:check && npm test && npm run build

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
COPY --from=frontend /frontend/dist ./frontend/dist
RUN mvn -B clean package -DskipTests -Dfrontend.skip=true

# --- Runtime stage ---
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
