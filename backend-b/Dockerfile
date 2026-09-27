# Etapa 1: compilar el jar con Maven + Java 21
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests


# Etapa 2: solo el runtime de Java 21, sin Maven
FROM eclipse-temurin:21-jre

# curl para el HEALTHCHECK (se instala explícitamente en vez de depender de la imagen base)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuario sin privilegios; Tomcat solo escribe en /tmp, que es escribible por todos
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

USER app

EXPOSE 8080

# Solo consulta el /health propio (no llama a backend-a), así no hay dependencias circulares
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/health" > /dev/null || exit 1

CMD ["java", "-jar", "app.jar"]
