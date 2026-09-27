# backend-b

Servicio de datos de usuario del proyecto
[backend-communication-demo](https://github.com/jorgerivera12212-coder/backend-communication-demo).
Lo consume [`jorgeriveracoder/backend-a`](https://hub.docker.com/r/jorgeriveracoder/backend-a)
y, en sentido inverso, puede consultar el estado de backend-a.

## Responsabilidad

- Expone `GET /health` para comprobar que el servicio está vivo.
- Expone `GET /user`, que devuelve un usuario de ejemplo con datos fijos (sin base de datos).
  Es el endpoint que usa backend-a en su `GET /profile`.
- Expone `GET /backend-a-status`, que llama a `GET /health` de **backend-a** y devuelve la
  respuesta. Si backend-a no responde, devuelve `502`.

No tiene base de datos ni guarda estado.

## Tecnología

| Componente | Detalle |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5 (Spring Web) |
| Cliente HTTP | `RestClient` de Spring |
| Build | Maven, en una imagen multi-stage (`maven:3.9-eclipse-temurin-21`) |
| Runtime | `eclipse-temurin:21-jre`, arranca con `java -jar app.jar` |

## Imagen y tags

```text
jorgeriveracoder/backend-b
```

El workflow de release de GitHub Actions publica la imagen cada vez que se hace push de un tag
de Git que empieza por `v`, con dos tags:

| Tag | Significado |
|---|---|
| `vX.Y.Z` (p. ej. `v1.0.0`) | Versión fija, igual al tag de Git. El tag incluye la `v`. |
| `latest` | Última versión publicada. |

Para despliegues usa un tag fijo (`v1.0.0`) en lugar de `latest`, así sabes qué versión corre y
puedes volver atrás.

## Descargar la imagen

```bash
docker pull jorgeriveracoder/backend-b:v1.0.0
# o la última versión publicada
docker pull jorgeriveracoder/backend-b:latest
```

## Puertos

| Puerto del contenedor | Protocolo | Uso |
|---|---|---|
| `8080` | HTTP | API (`EXPOSE 8080`) |

## Variables de entorno

La imagen no incluye ningún archivo de configuración: todo se pasa al arrancar el contenedor.
Todas las variables tienen valor por defecto, así que el contenedor arranca sin ninguna y
`/health` y `/user` funcionan. **`BACKEND_A_URL` es obligatoria en la práctica** solo si vas a
usar `/backend-a-status`: su valor por defecto (`http://localhost:8000`) apunta al propio
contenedor, no a backend-a.

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `BACKEND_A_URL` | **Sí** (para `/backend-a-status`) | `http://localhost:8000` | URL base de backend-a. En Docker, el nombre del contenedor o servicio: `http://backend-a:8000`. |
| `BACKEND_A_TIMEOUT` | No | `5.0` | Tiempo máximo (segundos) de conexión y de lectura en la llamada a backend-a. |
| `APP_NAME` | No | `backend-b` | Nombre del servicio (`spring.application.name`); lo devuelve `/health`. |
| `APP_ENV` | No | `dev` | Ambiente (`dev`, `staging`, `prod`); se muestra en el log de arranque. |
| `LOG_LEVEL` | No | `INFO` | Nivel de log raíz (`DEBUG`, `INFO`, `WARN`, `ERROR`). |
| `SERVER_ADDRESS` | No | `0.0.0.0` | Dirección en la que escucha el servidor. |
| `SERVER_PORT` | No | `8080` | Puerto en el que escucha dentro del contenedor. Déjalo en `8080`; para cambiar el puerto del host usa `-p <host>:8080`. |

Hoy ninguna variable es secreta.

## Ejecutar y comprobar

### Solo backend-b

backend-b funciona sin backend-a (excepto `/backend-a-status`):

```bash
docker run -d --name backend-b -p 8080:8080 jorgeriveracoder/backend-b:v1.0.0

curl http://localhost:8080/health
# {"status":"ok","service":"backend-b"}

curl http://localhost:8080/user
# {"id":1,"name":"John Doe","email":"john@example.com"}

docker logs backend-b
# ... Starting backend-b (env=dev, port=8080)
```

Spring Boot tarda unos segundos en arrancar; si `curl` falla al principio, espera a ver el
mensaje `Starting backend-b ...` en los logs.

Sin backend-a, `GET /backend-a-status` devuelve `502 Bad Gateway`:

```bash
curl -i http://localhost:8080/backend-a-status
# HTTP/1.1 502
```

### Con backend-a usando `docker run`

Los dos contenedores tienen que estar en la misma red de Docker para resolverse por nombre:

```bash
docker network create backend-demo

docker run -d --name backend-b --network backend-demo -p 8080:8080 \
  -e BACKEND_A_URL=http://backend-a:8000 \
  jorgeriveracoder/backend-b:v1.0.0

docker run -d --name backend-a --network backend-demo -p 8000:8000 \
  -e BACKEND_B_URL=http://backend-b:8080 \
  jorgeriveracoder/backend-a:v1.0.0

curl http://localhost:8080/backend-a-status
```

## Endpoints

| Método | Ruta | Respuesta correcta | Error |
|---|---|---|---|
| `GET` | `/health` | `200` `{"status": "ok", "service": "backend-b"}` | — |
| `GET` | `/user` | `200` `{"id": 1, "name": "John Doe", "email": "john@example.com"}` | — |
| `GET` | `/backend-a-status` | `200` (ver abajo) | `502` si backend-a no responde, devuelve error o un cuerpo vacío o que no es JSON |

Ejemplo de `GET /backend-a-status` con backend-a disponible:

```json
{
  "message": "Status retrieved from backend-a",
  "backendA": {
    "status": "ok",
    "service": "backend-a"
  }
}
```

## Comunicación con backend-a

La comunicación es HTTP en ambos sentidos:

```text
Cliente ──GET /profile──────────▶ backend-a ──GET /user────▶ backend-b
Cliente ──GET /backend-a-status─▶ backend-b ──GET /health──▶ backend-a
```

- **backend-a → backend-b:** `GET /profile` de backend-a llama a `GET /user` de backend-b.
  backend-b no necesita ninguna configuración para esto.
- **backend-b → backend-a:** `GET /backend-a-status` llama a `${BACKEND_A_URL}/health`. Solo
  consulta `/health`, que no vuelve a llamar a backend-b, así que no hay bucles.

Dentro de un contenedor, `localhost` es el propio contenedor. Por eso `BACKEND_A_URL` debe
usar el nombre del servicio o contenedor de backend-a (`http://backend-a:8000`).

### Docker Compose con ambos servicios

Guarda esto como `compose.yml`:

```yaml
services:
  backend-b:
    image: jorgeriveracoder/backend-b:v1.0.0
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      APP_ENV: dev
      BACKEND_A_URL: http://backend-a:8000
      BACKEND_A_TIMEOUT: "5.0"

  backend-a:
    image: jorgeriveracoder/backend-a:v1.0.0
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      APP_ENV: dev
      BACKEND_B_URL: http://backend-b:8080
      BACKEND_B_TIMEOUT: "5.0"
    depends_on:
      - backend-b
```

```bash
docker compose up -d

curl http://localhost:8080/health
curl http://localhost:8080/user
curl http://localhost:8080/backend-a-status # backend-b → backend-a
curl http://localhost:8000/profile          # backend-a → backend-b
```

Solo backend-a tiene `depends_on` (Compose no admite dependencias circulares), y `depends_on`
no espera a que el otro servicio esté listo. Si alguno de los endpoints que cruzan servicios
devuelve `502` justo después de arrancar, espera unos segundos y vuelve a intentarlo. Con
imágenes que incluyen healthcheck (ver abajo) puedes usar
`depends_on: backend-b: condition: service_healthy` en backend-a y `docker compose up -d --wait`.

## Healthcheck y usuario

A partir de la primera versión publicada después de `v2.0.0`:

- La imagen incluye `HEALTHCHECK`: consulta su propio `GET /health` cada 10 s con `curl`
  (instalado en la imagen), con 30 s de margen para que arranque Spring Boot. No llama a
  backend-a, así que el estado `healthy` no depende del otro servicio. Ver el estado:
  `docker ps` o `docker inspect --format '{{json .State.Health}}' backend-b`.
- El proceso corre como el usuario sin privilegios `app` (uid 10001), no como root.
- Si backend-a responde `200` sin cuerpo, `/backend-a-status` devuelve `502` en lugar de `500`.

`v1.0.0` y `v2.0.0` no tienen healthcheck y corren como root.

## Código fuente

<https://github.com/jorgerivera12212-coder/backend-communication-demo> (directorio `backend-b/`).
