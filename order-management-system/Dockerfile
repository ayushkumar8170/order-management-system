# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies separately from source so `docker build` skips the
# download step on source-only changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /build/target/oms.jar /app/oms.jar

ENV DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=oms_db \
    DB_USER=oms_user \
    DB_PASSWORD=oms_password \
    DB_POOL_SIZE=10

ENTRYPOINT ["java", "-jar", "/app/oms.jar"]
