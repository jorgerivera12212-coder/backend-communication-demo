# Backend Communication Demo

Dos backends en tecnologías distintas que se comunican por HTTP en ambos sentidos:

```text
                         Backend A (Python + FastAPI, :8000)
Client ──GET /profile──▶      │  ▲
                 HTTP GET /user  │  │  HTTP GET /health
                              ▼  │
Client ──GET /backend-a-status──▶ Backend B (Java 21 + Spring Boot, :8080)
```

- **Backend A** es el servicio principal. `GET /profile` llama a `GET /user` de Backend B con `httpx.AsyncClient`.
- **Backend B** devuelve datos hardcodeados. Sin base de datos, sin capas de servicio/repositorio.
- **Sentido inverso (ejemplo):** `GET /backend-a-status` de Backend B llama a `GET /health`
  de Backend A con `RestClient` (incluido en Spring Web). Solo consulta `/health`, que no
  vuelve a llamar a Backend B, así que no hay bucles.

## Ambientes y formas de ejecución

Son dos ideas independientes:

- **Ambiente** (`dev`, `staging`, `prod`): qué configuración usa la aplicación. Llega en la
  variable `APP_ENV` y en el resto de variables del archivo de ese ambiente.
- **Forma de ejecución** (IDE o Docker): cómo se arranca el proceso.

| Forma de ejecución | Archivo de configuración | Ambientes posibles |
|---|---|---|
| IDE / terminal, sin Docker | `backend-a/.env.local`, `backend-b/.env.local` | `dev` |
| Docker, construyendo desde el código (`docker-compose.yml`) | `env/<DEPLOY_ENV>/backend-*.env` | `dev` (por defecto), también `staging` o `prod` para probar su configuración |
| Docker, con imágenes publicadas (`compose.deploy.yml`) | `env/<DEPLOY_ENV>/backend-*.env` | `staging`, `prod` |

Todos los archivos usan **los mismos nombres de variables**; solo cambian los valores.
Ninguno está en las imágenes: la configuración se inyecta al arrancar el contenedor, así que
la misma imagen sirve para staging y prod.

### Variables de cada backend

Las dos aplicaciones leen las mismas variables comunes:

| Variable      | Efecto                                                                        |
|---------------|-------------------------------------------------------------------------------|
| `APP_NAME`    | Nombre del servicio: lo devuelve `/health` y aparece en los logs              |
| `APP_ENV`     | Ambiente (`dev`, `staging`, `prod`): se muestra en el log de arranque         |
| `LOG_LEVEL`   | Nivel de log (`DEBUG`, `INFO`, `WARN`, `ERROR`). `INFO` en todos los ambientes para empezar |
| `SERVER_PORT` | Puerto en el que escucha el proceso (dentro del contenedor, en Docker)        |

| backend-a           | IDE (`.env.local`)      | Docker (`env/*/backend-a.env`) |
|---------------------|-------------------------|--------------------------------|
| `BACKEND_B_URL`     | `http://localhost:8080` | `http://backend-b:8080`        |
| `BACKEND_B_TIMEOUT` | `5.0`                   | `5.0`                          |

| backend-b           | IDE (`.env.local`)      | Docker (`env/*/backend-b.env`) |
|---------------------|-------------------------|--------------------------------|
| `SERVER_ADDRESS`    | `0.0.0.0`               | `0.0.0.0`                      |
| `BACKEND_A_URL`     | `http://localhost:8000` | `http://backend-a:8000`        |
| `BACKEND_A_TIMEOUT` | `5.0`                   | `5.0`                          |

- backend-a las lee con `pydantic-settings` ([app/config.py](backend-a/app/config.py)).
- backend-b las lee en [application.properties](backend-b/src/main/resources/application.properties)
  con placeholders `${VARIABLE:valor_por_defecto}`; el `.env.local` se importa con
  `spring.config.import=optional:file:.env.local[.properties]`.
- Una variable de entorno real siempre tiene prioridad sobre `.env.local`.
- `SERVER_PORT` en Docker debe coincidir con el puerto interno de `ports:` (8000 / 8080).
  Para cambiar el puerto **del host** usa `BACKEND_A_HOST_PORT` / `BACKEND_B_HOST_PORT`.
- No hay perfiles de Spring: todo lo que varía por ambiente son valores de variables.

### Variables de Compose vs. variables de los contenedores

Hay dos grupos de variables y no se mezclan:

| | Variables de Compose | Variables de la aplicación |
|---|---|---|
| Para qué | Resolver `${...}` dentro de los YAML de Compose | Configurar cada backend |
| Ejemplos | `DEPLOY_ENV`, `BACKEND_A_IMAGE`, `BACKEND_B_IMAGE`, `BACKEND_A_HOST_PORT`, `BACKEND_B_HOST_PORT`, `COMPOSE_PROJECT_NAME` | `APP_ENV`, `LOG_LEVEL`, `BACKEND_B_URL`, ... |
| De dónde salen | La shell (`DEPLOY_ENV=prod docker compose ...`) o el archivo `.env` junto al YAML (plantilla: [.env.example](.env.example)) | `env/<DEPLOY_ENV>/backend-*.env`, que Compose inyecta con `env_file:` |
| ¿Llegan al contenedor? | No | Sí |

`DEPLOY_ENV` es del primer grupo: solo decide **qué archivo** carga `env_file`. El valor de
`APP_ENV` que ve la aplicación sale de ese archivo; Compose no lo fija.

Comprueba el resultado con:

```bash
docker compose config            # muestra el YAML ya resuelto, con las variables de env_file
```

## Qué archivos crear

Solo se versionan los `*.example`. Los archivos reales tienen el mismo nombre sin `.example`,
están en `.gitignore` y en los `.dockerignore`, y **nunca** deben contener secretos que se
suban al repositorio. Hoy ninguna variable es secreta; si en el futuro alguna lo es, va solo
en el archivo real del servidor.

**Computadora de desarrollo**

```bash
# Ejecución desde el IDE
cp backend-a/.env.local.example backend-a/.env.local
cp backend-b/.env.local.example backend-b/.env.local

# Docker en dev
cp env/dev/backend-a.env.example env/dev/backend-a.env
cp env/dev/backend-b.env.example env/dev/backend-b.env
```

El `.env` de la raíz no hace falta en desarrollo (todo tiene valor por defecto).

**Servidor de staging** (un ambiente por servidor)

Solo necesita `compose.deploy.yml` y la configuración, no el código:

```text
backend-communication-demo/
├── compose.deploy.yml
├── .env                        # DEPLOY_ENV=staging + imágenes (desde .env.example)
└── env/staging/
    ├── backend-a.env           # desde env/staging/backend-a.env.example
    └── backend-b.env           # desde env/staging/backend-b.env.example
```

**Servidor de prod**: igual, con `DEPLOY_ENV=prod` y `env/prod/`.

## Ejecución con Docker en desarrollo (`docker-compose.yml`)

Construye las imágenes desde el código local. Desde `backend-communication-demo/`:

```bash
docker compose up -d --build       # dev (DEPLOY_ENV por defecto)
```

Comprobar:

```bash
curl http://localhost:8080/health
curl http://localhost:8080/user
curl http://localhost:8000/health
curl http://localhost:8000/profile          # A → B
curl http://localhost:8080/backend-a-status # B → A
```

Para probar localmente la configuración de otro ambiente (requiere crear sus `env/<ambiente>/*.env`):

```bash
DEPLOY_ENV=staging docker compose up -d --build
```

## Despliegue en staging y prod (`compose.deploy.yml`)

`compose.deploy.yml` no tiene `build:`: usa `image:` con las imágenes indicadas en
`BACKEND_A_IMAGE` y `BACKEND_B_IMAGE`, así que el servidor no compila código. Las imágenes las publica en
Docker Hub el workflow de release (ver [CI/CD](#cicd)); el despliegue sigue siendo manual.

### Definir las imágenes

En el `.env` del servidor (junto a `compose.deploy.yml`):

```bash
DEPLOY_ENV=staging
BACKEND_A_IMAGE=registry.example.com/demo/backend-a:1.0.0
BACKEND_B_IMAGE=registry.example.com/demo/backend-b:1.0.0
```

- Nombre completo del registro **con tag fijo** (`1.0.0`), no `latest`: así se sabe qué
  versión corre y se puede volver atrás.
- `DEPLOY_ENV` y las dos imágenes son obligatorias: si falta alguna, Compose se detiene con
  un error en vez de usar un valor por defecto.
- **Promoción staging → prod:** en el servidor de prod se ponen **los mismos**
  `BACKEND_A_IMAGE` / `BACKEND_B_IMAGE` validados en staging. Solo cambian `DEPLOY_ENV=prod`
  y `env/prod/*.env`.

### Comandos por ambiente

Se ejecutan en el servidor, en el directorio de `compose.deploy.yml`. Son iguales en staging
y prod: el ambiente lo decide el `.env` del servidor.

| Acción | Comando |
|---|---|
| Levantar | `docker compose -f compose.deploy.yml pull && docker compose -f compose.deploy.yml up -d` |
| Actualizar a otra versión | Cambiar los tags en `.env`, luego `docker compose -f compose.deploy.yml pull && docker compose -f compose.deploy.yml up -d` |
| Aplicar cambios de `env/<ambiente>/*.env` | `docker compose -f compose.deploy.yml up -d --force-recreate` |
| Estado | `docker compose -f compose.deploy.yml ps` |
| Logs | `docker compose -f compose.deploy.yml logs -f --tail 100` (añadir `backend-a` o `backend-b` para uno solo) |
| Ver configuración resuelta | `docker compose -f compose.deploy.yml config` |
| Detener sin borrar | `docker compose -f compose.deploy.yml stop` |
| Detener y borrar contenedores | `docker compose -f compose.deploy.yml down` |

Sin `.env` en el servidor, las variables se pasan en la línea de comandos:

```bash
DEPLOY_ENV=prod \
BACKEND_A_IMAGE=registry.example.com/demo/backend-a:1.0.0 \
BACKEND_B_IMAGE=registry.example.com/demo/backend-b:1.0.0 \
docker compose -f compose.deploy.yml up -d
```

(Hay que repetirlas en **todos** los comandos, incluidos `logs`, `ps` y `down`, porque Compose
necesita resolver el YAML; por eso se recomienda el `.env`.)

### Comandos en desarrollo

Desde `backend-communication-demo/`. Con `DEPLOY_ENV=<ambiente>` delante se aplican a otro ambiente.

| Acción | Comando |
|---|---|
| Levantar | `docker compose up -d --build` |
| Actualizar tras cambiar código, `requirements.txt`, `pom.xml` o un `Dockerfile` | `docker compose up -d --build` (solo uno: `docker compose up -d --build backend-a`) |
| Aplicar cambios de `env/dev/*.env` o del YAML | `docker compose up -d --force-recreate` |
| En primer plano con logs | `docker compose up --build` (`Ctrl+C` detiene) |
| Estado | `docker compose ps` |
| Logs | `docker compose logs -f --tail 50` (añadir `backend-a` o `backend-b` para uno solo) |
| CPU y RAM | `docker stats` |
| Terminal dentro del contenedor | `docker compose exec backend-a sh` |
| Reiniciar sin aplicar cambios | `docker compose restart` |
| Detener sin borrar | `docker compose stop` (se reanuda con `docker compose start`) |
| Detener y borrar contenedores y red | `docker compose down` (`--rmi local` borra también las imágenes construidas) |

### Varios ambientes en la misma máquina

Se asume **un ambiente por servidor**. Para ejecutar dos a la vez en la misma máquina, cada uno
necesita otro nombre de proyecto (si no, Compose trata ambos como el mismo y reemplaza los
contenedores) y otros puertos del host:

```bash
DEPLOY_ENV=staging COMPOSE_PROJECT_NAME=demo-staging \
BACKEND_A_HOST_PORT=18000 BACKEND_B_HOST_PORT=18080 \
docker compose -f compose.deploy.yml up -d
```

Lo más cómodo es tener un directorio por ambiente, cada uno con su `.env`.

## Configuración operativa

Común a ambos servicios en los dos archivos Compose (bloque `x-common`) más los límites de cada servicio.

### Límites de recursos (`deploy.resources.limits`)

| Servicio  | RAM    | CPU         |
|-----------|--------|-------------|
| backend-a | 256 MB | 0.5 núcleos |
| backend-b | 512 MB | 0.5 núcleos |

Si un contenedor supera su RAM, Docker lo mata (OOM). La CPU no mata el contenedor: solo lo frena.

### Reinicio automático y logs

- `restart: unless-stopped`: Docker vuelve a arrancar el contenedor si el proceso termina
  (caída, OOM, salida limpia...) o si se reinicia Docker. Si lo paraste a mano con `stop` o
  `down`, sigue parado aunque se reinicie Docker; para volver a levantarlo usa `up -d` o `start`.
- Logs con rotación (`json-file`): como máximo 3 archivos de 10 MB por contenedor
  (30 MB en total); los más antiguos se borran solos.

Para que los contenedores vuelvan tras reiniciar el servidor, Docker debe arrancar con el sistema:

```bash
sudo systemctl enable docker
```

Ver cuántas veces se ha reiniciado un contenedor:

```bash
docker inspect -f '{{.RestartCount}}' $(docker compose -f compose.deploy.yml ps -q backend-a)
```

### ¿Por qué `backend-b:8080` y no `localhost:8080`?

Dentro de un contenedor, `localhost` es el propio contenedor. Docker Compose crea una red
interna donde cada servicio se resuelve por su nombre, así que en `env/*/backend-a.env`
`BACKEND_B_URL=http://backend-b:8080` y en `env/*/backend-b.env`
`BACKEND_A_URL=http://backend-a:8000`.

Solo backend-a tiene `depends_on: backend-b`. Compose no admite dependencias circulares, y
`depends_on` solo ordena el arranque, no espera a que el otro servicio esté listo. Por eso
cada backend devuelve 502 si el otro todavía no responde, y el siguiente intento funciona.

## Endpoints

| Servicio  | Endpoint       | Respuesta                                                        |
|-----------|----------------|------------------------------------------------------------------|
| backend-a | `GET /health`  | `{"status": "ok", "service": "backend-a"}`                       |
| backend-a | `GET /profile` | `{"message": "Profile retrieved from backend-b", "user": {...}}` |
| backend-b | `GET /health`  | `{"status": "ok", "service": "backend-b"}`                       |
| backend-b | `GET /user`    | `{"id": 1, "name": "John Doe", "email": "john@example.com"}`     |
| backend-b | `GET /backend-a-status` | `{"message": "Status retrieved from backend-a", "backendA": {...}}` |

Si Backend B no responde, `/profile` devuelve `502 Error communicating with backend-b`.
Si Backend A no responde, `/backend-a-status` devuelve `502 Error communicating with backend-a`.

Swagger de FastAPI: http://localhost:8000/docs

## Ejecución desde el IDE (sin Docker)

Requisitos: Python 3.12, Java 21 y Maven. Cada backend lee su `.env.local` (ambiente `dev`).

### Backend B

```bash
cd backend-b
cp .env.local.example .env.local
mvn spring-boot:run
```

### Backend A

```bash
cd backend-a
cp .env.local.example .env.local
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app
```

Para desarrollo con recarga automática también se puede usar
`uvicorn app.main:app --reload --port 8000` (en ese caso el puerto lo indica el comando).

```bash
curl http://localhost:8000/health
curl http://localhost:8000/profile
```

## Tests

### Backend A

Los tests no necesitan que Backend B esté levantado: la llamada HTTP se simula con
`httpx.MockTransport`.

```bash
cd backend-a
pytest
```

### Backend B

```bash
cd backend-b
mvn test
```

Sin Java 21 instalado localmente, se pueden ejecutar con Docker:

```bash
cd backend-b
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

## CI/CD

Dos workflows de GitHub Actions en `.github/workflows/`:

- **`ci.yml`**: se ejecuta en cada push a `main` y en cada pull request hacia `main`.
  Corre los tests de Backend A (`pytest`) y de Backend B (`mvn --batch-mode verify`) en
  paralelo; si ambos pasan, construye las dos imágenes Docker solo para validar los
  `Dockerfile`. No publica nada.
- **`release.yml`**: se ejecuta solo al hacer push de un tag de Git que empiece por `v`
  (`v1.0.0`, `v1.1.0`...). Construye ambas imágenes y las publica en Docker Hub.

Secrets necesarios (GitHub → Settings → Secrets and variables → Actions):

| Secret | Valor |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Access token de Docker Hub (Account settings → Personal access tokens), con permiso de escritura |

Publicar una nueva versión:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Esto publica automáticamente:

```text
<DOCKERHUB_USERNAME>/backend-a:v1.0.0   y   <DOCKERHUB_USERNAME>/backend-a:latest
<DOCKERHUB_USERNAME>/backend-b:v1.0.0   y   <DOCKERHUB_USERNAME>/backend-b:latest
```

Para desplegar esa versión con `compose.deploy.yml`, usa el tag fijo (no `latest`), p. ej.
`BACKEND_A_IMAGE=<DOCKERHUB_USERNAME>/backend-a:v1.0.0`.

## Estructura

```text
.
├── .github/workflows/      # ci.yml (tests + build) y release.yml (Docker Hub)
├── backend-a/              # Python + FastAPI
│   ├── app/main.py
│   ├── app/__main__.py     # python -m app: arranca Uvicorn en SERVER_PORT
│   ├── app/config.py       # Settings desde variables de entorno / .env.local
│   ├── tests/test_main.py
│   ├── .env.local.example  # ejecución desde el IDE
│   ├── requirements.txt
│   └── Dockerfile
├── backend-b/              # Java 21 + Spring Boot
│   ├── src/main/java/com/example/backendb/
│   │   ├── BackendBApplication.java
│   │   └── controller/
│   │       ├── UserController.java       # /health, /user
│   │       └── BackendAController.java   # /backend-a-status (llama a backend-a)
│   ├── src/main/resources/application.properties
│   ├── src/test/java/com/example/backendb/controller/
│   ├── .env.local.example  # ejecución desde el IDE
│   ├── pom.xml
│   └── Dockerfile          # multi-stage: Maven build → JRE runtime
├── env/                    # configuración de los contenedores por ambiente
│   ├── dev/backend-a.env.example, backend-b.env.example
│   ├── staging/backend-a.env.example, backend-b.env.example
│   └── prod/backend-a.env.example, backend-b.env.example
├── .env.example            # variables de Compose (DEPLOY_ENV, imágenes, puertos)
├── docker-compose.yml      # desarrollo: construye desde el código
└── compose.deploy.yml      # staging / prod: imágenes versionadas, sin build
```
