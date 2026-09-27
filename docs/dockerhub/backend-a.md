# backend-a

Servicio principal del proyecto
[backend-communication-demo](https://github.com/jorgerivera12212-coder/backend-communication-demo):
una API HTTP que obtiene el perfil de usuario consultando a
[`jorgeriveracoder/backend-b`](https://hub.docker.com/r/jorgeriveracoder/backend-b).

## Responsabilidad

- Expone `GET /health` para comprobar que el servicio está vivo.
- Expone `GET /profile`, que llama a `GET /user` de **backend-b** y devuelve la respuesta
  envuelta en un mensaje.
- Si backend-b no responde o devuelve un error, `/profile` responde `502`.

No tiene base de datos ni guarda estado.

## Tecnología

| Componente | Detalle |
|---|---|
| Lenguaje | Python 3.12 (imagen base `python:3.12-slim`) |
| Framework | FastAPI, servido con Uvicorn |
| Cliente HTTP | `httpx.AsyncClient` |
| Configuración | `pydantic-settings` (variables de entorno) |
| Arranque | `python -m app` (Uvicorn en `0.0.0.0:${SERVER_PORT}`) |

## Imagen y tags

```text
jorgeriveracoder/backend-a
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
docker pull jorgeriveracoder/backend-a:v1.0.0
# o la última versión publicada
docker pull jorgeriveracoder/backend-a:latest
```

## Puertos

| Puerto del contenedor | Protocolo | Uso |
|---|---|---|
| `8000` | HTTP | API (`EXPOSE 8000`) |

## Variables de entorno

La imagen no incluye ningún archivo de configuración: todo se pasa al arrancar el contenedor.
Todas las variables tienen valor por defecto, así que el contenedor arranca sin ninguna, pero
**`BACKEND_B_URL` es obligatoria en la práctica** si quieres que `/profile` funcione: su valor
por defecto (`http://localhost:8080`) apunta al propio contenedor, no a backend-b.

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `BACKEND_B_URL` | **Sí** (para `/profile`) | `http://localhost:8080` | URL base de backend-b. En Docker, el nombre del contenedor o servicio: `http://backend-b:8080`. |
| `BACKEND_B_TIMEOUT` | No | `5.0` | Tiempo máximo (segundos) de la llamada a backend-b. |
| `APP_NAME` | No | `backend-a` | Nombre del servicio; lo devuelve `/health` y aparece en los logs. |
| `APP_ENV` | No | `dev` | Ambiente (`dev`, `staging`, `prod`); se muestra en el log de arranque. |
| `LOG_LEVEL` | No | `INFO` | Nivel de log (`DEBUG`, `INFO`, `WARNING`, `ERROR`). |
| `SERVER_PORT` | No | `8000` | Puerto en el que escucha Uvicorn dentro del contenedor. Déjalo en `8000`; para cambiar el puerto del host usa `-p <host>:8000`. |

Hoy ninguna variable es secreta.

## Ejecutar y comprobar

### Solo backend-a

```bash
docker run -d --name backend-a -p 8000:8000 jorgeriveracoder/backend-a:v1.0.0

curl http://localhost:8000/health
# {"status":"ok","service":"backend-a"}

docker logs backend-a
# ... INFO [backend-a] Starting backend-a (env=dev, backend_b_url=http://localhost:8080)
```

Sin backend-b, `GET /profile` devuelve `502`:

```bash
curl -i http://localhost:8000/profile
# HTTP/1.1 502 Bad Gateway
# {"detail":"Error communicating with backend-b"}
```

La documentación interactiva de FastAPI (Swagger) está en <http://localhost:8000/docs>.

### Con backend-b usando `docker run`

Los dos contenedores tienen que estar en la misma red de Docker para resolverse por nombre:

```bash
docker network create backend-demo

docker run -d --name backend-b --network backend-demo -p 8080:8080 \
  -e BACKEND_A_URL=http://backend-a:8000 \
  jorgeriveracoder/backend-b:v1.0.0

docker run -d --name backend-a --network backend-demo -p 8000:8000 \
  -e BACKEND_B_URL=http://backend-b:8080 \
  jorgeriveracoder/backend-a:v1.0.0

curl http://localhost:8000/profile
```

## Endpoints

| Método | Ruta | Respuesta correcta | Error |
|---|---|---|---|
| `GET` | `/health` | `200` `{"status": "ok", "service": "backend-a"}` | — |
| `GET` | `/profile` | `200` (ver abajo) | `502` `{"detail": "Error communicating with backend-b"}` |
| `GET` | `/docs` | Swagger UI de FastAPI | — |

Ejemplo de `GET /profile` con backend-b disponible:

```json
{
  "message": "Profile retrieved from backend-b",
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

## Comunicación con backend-b

La comunicación es HTTP en ambos sentidos:

```text
Cliente ──GET /profile──────────▶ backend-a ──GET /user────▶ backend-b
Cliente ──GET /backend-a-status─▶ backend-b ──GET /health──▶ backend-a
```

- **backend-a → backend-b:** `GET /profile` llama a `${BACKEND_B_URL}/user`.
- **backend-b → backend-a:** `GET /backend-a-status` de backend-b llama a `GET /health` de
  backend-a (que no vuelve a llamar a backend-b, así que no hay bucles).

Dentro de un contenedor, `localhost` es el propio contenedor. Por eso `BACKEND_B_URL` debe
usar el nombre del servicio o contenedor de backend-b (`http://backend-b:8080`).

### Docker Compose con ambos servicios

`/profile` necesita backend-b, así que lo más cómodo es levantar los dos juntos. Guarda esto
como `compose.yml`:

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

curl http://localhost:8000/health
curl http://localhost:8000/profile          # backend-a → backend-b
curl http://localhost:8080/backend-a-status # backend-b → backend-a
```

`depends_on` solo ordena el arranque, no espera a que backend-b esté listo (Spring Boot tarda
unos segundos). Si `/profile` devuelve `502` justo después de arrancar, espera un momento y
vuelve a intentarlo. Con imágenes que incluyen healthcheck (ver abajo) puedes usar
`depends_on: backend-b: condition: service_healthy` y `docker compose up -d --wait`.

## Healthcheck y usuario

A partir de la primera versión publicada después de `v2.0.0`:

- La imagen incluye `HEALTHCHECK`: consulta su propio `GET /health` cada 10 s (con `urllib`
  de Python; la imagen no trae curl). No llama a backend-b, así que el estado `healthy` no
  depende del otro servicio. Ver el estado: `docker ps` o
  `docker inspect --format '{{json .State.Health}}' backend-a`.
- El proceso corre como el usuario sin privilegios `app` (uid 10001), no como root.
- Si backend-b responde con un cuerpo inválido (vacío, HTML, JSON mal formado o que no es un
  objeto), `/profile` devuelve `502` en lugar de `500`.

`v1.0.0` y `v2.0.0` no tienen healthcheck y corren como root.

## Código fuente

<https://github.com/jorgerivera12212-coder/backend-communication-demo> (directorio `backend-a/`).
