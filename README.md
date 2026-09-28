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
| IDE / terminal, sin Docker | Ninguno: valores por defecto del código | `dev` |
| Docker, construyendo desde el código (`docker-compose.yml`) | `env/<DEPLOY_ENV>/backend-*.env` | `dev` (por defecto), también `staging` o `prod` para probar su configuración |
| Docker, con imágenes publicadas (`compose.deploy.yml`) | `env/<DEPLOY_ENV>/backend-*.env` | `staging`, `prod` |

La configuración se escribe en **un solo lugar por ambiente**: `env/<ambiente>/`. Los valores
por defecto del código ya son los de `dev` fuera de Docker (URLs a `localhost`), así que desde
el IDE no hace falta ningún archivo.
Ninguna configuración está en las imágenes: la configuración se inyecta al arrancar el contenedor, así que
la misma imagen sirve para staging y prod.

### Variables de cada backend

Las dos aplicaciones leen las mismas variables comunes:

| Variable      | Efecto                                                                        |
|---------------|-------------------------------------------------------------------------------|
| `APP_NAME`    | Nombre del servicio: lo devuelve `/health` y aparece en los logs              |
| `APP_ENV`     | Ambiente (`dev`, `staging`, `prod`): se muestra en el log de arranque         |
| `LOG_LEVEL`   | Nivel de log (`DEBUG`, `INFO`, `WARN`, `ERROR`). `INFO` en todos los ambientes para empezar |
| `SERVER_PORT` | Puerto en el que escucha el proceso (dentro del contenedor, en Docker)        |

| backend-a           | IDE (valor por defecto) | Docker (`env/*/backend-a.env`) |
|---------------------|-------------------------|--------------------------------|
| `BACKEND_B_URL`     | `http://localhost:8080` | `http://backend-b:8080`        |
| `BACKEND_B_TIMEOUT` | `5.0`                   | `5.0`                          |

| backend-b           | IDE (valor por defecto) | Docker (`env/*/backend-b.env`) |
|---------------------|-------------------------|--------------------------------|
| `SERVER_ADDRESS`    | `0.0.0.0`               | `0.0.0.0`                      |
| `BACKEND_A_URL`     | `http://localhost:8000` | `http://backend-a:8000`        |
| `BACKEND_A_TIMEOUT` | `5.0`                   | `5.0`                          |

- backend-a las lee con `pydantic-settings` ([app/config.py](backend-a/app/config.py)).
- backend-b las lee en [application.properties](backend-b/src/main/resources/application.properties)
  con placeholders `${VARIABLE:valor_por_defecto}`.
- Para cambiar algo puntual desde el IDE (p. ej. `LOG_LEVEL=DEBUG`), defínelo como variable de
  entorno en la configuración de ejecución del IDE o en la terminal.
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

Hay solo dos tipos de archivo de configuración, y los dos están fuera de `backend-a/` y
`backend-b/`:

| Archivo | Para qué |
|---|---|
| `.env` (raíz) | Variables de Compose: ambiente, imágenes, puertos del host |
| `env/<ambiente>/backend-*.env` | Configuración de cada backend en ese ambiente |

Solo se versionan los `*.example`. Los archivos reales tienen el mismo nombre sin `.example`,
están en `.gitignore` y **nunca** deben contener secretos que se suban al repositorio. Hoy
ninguna variable es secreta; si en el futuro alguna lo es, va solo en el archivo real del
servidor.

Ninguno entra en las imágenes: el contexto de build es `backend-a/` o `backend-b/`, donde no
hay archivos `.env`, y además sus `.dockerignore` excluyen `.env*` y `*.env` por si alguien
crea uno ahí.

**Computadora de desarrollo**

```bash
# Docker en dev (desde el IDE no hace falta crear nada)
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
docker compose up -d --build --wait   # dev (DEPLOY_ENV por defecto)
```

`--wait` no devuelve el control hasta que los healthchecks de ambos contenedores pasan
(ver [Healthchecks](#healthchecks-y-orden-de-arranque)); sin él, `up -d` vuelve en cuanto
se crean los contenedores, aunque Spring Boot todavía esté arrancando.

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
BACKEND_A_IMAGE=jorgeriveracoder/backend-a:v1.0.0
BACKEND_B_IMAGE=jorgeriveracoder/backend-b:v1.0.0
```

- Nombre completo de la imagen **con tag fijo** (`v1.0.0`, con la `v` del tag de Git), no
  `latest`: así se sabe qué versión corre y se puede volver atrás.
- **Las imágenes deben incluir `HEALTHCHECK`** (publicadas después de `v2.0.0`):
  `compose.deploy.yml` arranca backend-a con `condition: service_healthy`, y con `v1.0.0` o
  `v2.0.0`, que no tienen healthcheck, Compose no puede levantar backend-a. El `v1.0.0` de los
  ejemplos solo ilustra el formato.
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
| Levantar | `docker compose -f compose.deploy.yml pull && docker compose -f compose.deploy.yml up -d --wait` |
| Actualizar a otra versión | Cambiar los tags en `.env`, luego `docker compose -f compose.deploy.yml pull && docker compose -f compose.deploy.yml up -d --wait` |
| Aplicar cambios de `env/<ambiente>/*.env` | `docker compose -f compose.deploy.yml up -d --force-recreate` |
| Estado (incluye `healthy` / `unhealthy`) | `docker compose -f compose.deploy.yml ps` |
| Logs | `docker compose -f compose.deploy.yml logs -f --tail 100` (añadir `backend-a` o `backend-b` para uno solo) |
| Ver configuración resuelta | `docker compose -f compose.deploy.yml config` |
| Detener sin borrar | `docker compose -f compose.deploy.yml stop` |
| Detener y borrar contenedores | `docker compose -f compose.deploy.yml down` |

Sin `.env` en el servidor, las variables se pasan en la línea de comandos:

```bash
DEPLOY_ENV=prod \
BACKEND_A_IMAGE=jorgeriveracoder/backend-a:v1.0.0 \
BACKEND_B_IMAGE=jorgeriveracoder/backend-b:v1.0.0 \
docker compose -f compose.deploy.yml up -d --wait
```

(Hay que repetirlas en **todos** los comandos, incluidos `logs`, `ps` y `down`, porque Compose
necesita resolver el YAML; por eso se recomienda el `.env`.)

### Comandos en desarrollo

Desde `backend-communication-demo/`. Con `DEPLOY_ENV=<ambiente>` delante se aplican a otro ambiente.

| Acción | Comando |
|---|---|
| Levantar | `docker compose up -d --build --wait` |
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

### Healthchecks y orden de arranque

Cada imagen incluye un `HEALTHCHECK` (en su `Dockerfile`), así que funciona igual con
`docker-compose.yml`, con `compose.deploy.yml` y con `docker run`:

| Servicio  | Comprobación | Intervalo / timeout | Margen de arranque |
|-----------|--------------|---------------------|--------------------|
| backend-a | `GET /health` propio con `urllib` de Python (la imagen slim no trae curl) | 10 s / 3 s | 10 s |
| backend-b | `GET /health` propio con `curl` (instalado en la imagen) | 10 s / 3 s | 30 s (Spring Boot) |

- **Sin dependencias circulares:** cada healthcheck consulta solo el `/health` de su propio
  servicio, y ninguno de los dos `/health` llama al otro backend. Si el healthcheck de A
  dependiera de B y el de B de A, ninguno llegaría nunca a `healthy`.
- **Orden de arranque en un solo sentido:** backend-a tiene
  `depends_on: backend-b: condition: service_healthy`, así que se crea cuando B ya responde.
  backend-b no tiene `depends_on` (Compose no admite ciclos). Por eso `/backend-a-status`
  puede devolver 502 durante los primeros segundos, hasta que A arranca.
- `docker compose up -d --wait` espera a que **los dos** estén `healthy` y falla si alguno no
  llega a estarlo; es lo que usa la [prueba de integración](#integración-ambos-backends-con-docker).
- `docker compose ps` muestra el estado (`healthy`, `unhealthy`, `starting`). Detalle del último
  resultado: `docker inspect --format '{{json .State.Health}}' <contenedor>`.
- Docker **no** reinicia un contenedor `unhealthy` por sí solo (`restart` actúa solo cuando el
  proceso termina). El healthcheck sirve para detectarlo y para ordenar el arranque.

### Contenedores sin root

Los dos procesos corren como el usuario sin privilegios `app` (uid 10001). El código de la
imagen pertenece a root y es de solo lectura para la aplicación, que no necesita escribir en
disco (Tomcat usa `/tmp`). Comprobar: `docker compose exec backend-a id`.

## Endpoints

| Servicio  | Endpoint       | Respuesta                                                        |
|-----------|----------------|------------------------------------------------------------------|
| backend-a | `GET /health`  | `{"status": "ok", "service": "backend-a"}`                       |
| backend-a | `GET /profile` | `{"message": "Profile retrieved from backend-b", "user": {...}}` |
| backend-b | `GET /health`  | `{"status": "ok", "service": "backend-b"}`                       |
| backend-b | `GET /user`    | `{"id": 1, "name": "John Doe", "email": "john@example.com"}`     |
| backend-b | `GET /backend-a-status` | `{"message": "Status retrieved from backend-a", "backendA": {...}}` |

Si Backend B no responde, responde con un error o con un cuerpo inválido (vacío, HTML, JSON
mal formado o JSON que no es un objeto), `/profile` devuelve `502 Error communicating with backend-b`.
Si Backend A no responde, responde con un error o con un cuerpo vacío o que no es JSON,
`/backend-a-status` devuelve `502 Error communicating with backend-a`.

Swagger de FastAPI: http://localhost:8000/docs

## Ejecución desde el IDE (sin Docker)

Requisitos: Python 3.12, Java 21 y Maven. No hace falta ningún archivo de configuración: se
usan los valores por defecto del código (ambiente `dev`, el otro backend en `localhost`).

### Backend B

```bash
cd backend-b
mvn spring-boot:run
```

### Backend A

```bash
cd backend-a
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt   # runtime + pytest
python -m app
```

Para desarrollo con recarga automática también se puede usar
`uvicorn app.main:app --reload --port 8000` (en ese caso el puerto lo indica el comando).

```bash
curl http://localhost:8000/health
curl http://localhost:8000/profile
```

## Dependencias

Las versiones están fijadas para que dos builds del mismo commit sean idénticos:

- **backend-a:** [requirements.txt](backend-a/requirements.txt) tiene **todas** las
  dependencias de runtime (también las transitivas) con `==`, y es lo único que se instala en la
  imagen. [requirements-dev.txt](backend-a/requirements-dev.txt) añade pytest para tests y CI.
  Para actualizar: instalar las dependencias directas en un venv limpio y copiar `pip freeze`
  a `requirements.txt` (sin pytest), o aceptar el PR de Dependabot.
- **backend-b:** las versiones las fija el parent `spring-boot-starter-parent` de
  [pom.xml](backend-b/pom.xml).
- **Dependabot** ([.github/dependabot.yml](.github/dependabot.yml)) abre cada semana PRs
  para las actions, pip, Maven y las imágenes base; cada PR pasa por CI antes de fusionarse.

## Tests

### Backend A

Los tests no necesitan que Backend B esté levantado: la llamada HTTP se simula con
`httpx.MockTransport` (respuesta correcta, error HTTP, B caído y cuerpos inválidos).

```bash
cd backend-a
pip install -r requirements-dev.txt
pytest
```

### Backend B

Los tests sustituyen a backend-a por un servidor HTTP falso del JDK (respuesta correcta,
error HTTP, JSON inválido, contenido no JSON y cuerpo vacío).

```bash
cd backend-b
mvn test
```

Sin Java 21 instalado localmente, se pueden ejecutar con Docker:

```bash
cd backend-b
docker run --rm -v "$PWD":/app -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```

### Integración: ambos backends con Docker

[scripts/integration-test.sh](scripts/integration-test.sh) levanta los dos backends, espera a
los healthchecks (`up --wait`) y comprueba con los contenedores reales:

- `/health` de ambos servicios,
- **A → B:** `GET /profile` de A devuelve el usuario de B,
- **B → A:** `GET /backend-a-status` de B devuelve el `/health` de A,
- que ningún proceso corre como root.

Tiene dos modos (requiere docker con Compose v2, curl y jq):

```bash
# Código local: construye con docker-compose.yml
scripts/integration-test.sh

# Imágenes ya construidas (lo que hace CI): compose.deploy.yml, sin build ni pull
BACKEND_A_IMAGE=backend-a:candidate BACKEND_B_IMAGE=backend-b:candidate scripts/integration-test.sh
```

En el segundo modo usa `--pull never`: si la imagen no está en el Docker local falla, en vez de
descargar otra con el mismo nombre. Así se prueba exactamente la imagen indicada y, de paso, el
mismo `compose.deploy.yml` que se usa en staging y prod.

Usa su propio proyecto de Compose (`demo-integration`) y los puertos 18000 / 18080, así que
no interfiere con un stack de desarrollo levantado; al terminar borra sus contenedores. Si no
existen `env/dev/*.env`, los crea desde los `*.example`. Si algo falla, muestra `ps` y los
logs de ambos contenedores.

## CI/CD

Dos workflows de GitHub Actions en `.github/workflows/`, ambos con permisos mínimos
(`contents: read`):

- **`ci.yml`**: se ejecuta en cada push a `main`, en cada pull request hacia `main` y, como
  workflow reutilizable, desde `release.yml`. En paralelo corre los tests de Backend A
  (`pytest`), los de Backend B (`mvn --batch-mode verify`) y el **build de las dos imágenes**,
  que se guardan como artefacto del run (`images`, se borra al día siguiente). Si los tres
  pasan, la [prueba de integración](#integración-ambos-backends-con-docker) carga ese
  artefacto y prueba la comunicación real con esas imágenes. No publica nada.
- **`release.yml`**: se ejecuta al hacer push de un tag de Git que empiece por `v`. Tiene tres
  etapas y cada una solo empieza si la anterior pasó:
  1. **Validar tag.** El filtro `v*` del disparador no valida nada (también dispararía con
     `v1`, `v1.0.0-rc1` o un tag en otra rama); la validación real es explícita:
     - SemVer estricto `vMAYOR.MENOR.PARCHE` (`v1.2.3`; sin ceros a la izquierda ni sufijos
       como `-rc1`);
     - el commit del tag está en la historia de `main` (no se publica código de otra rama).
  2. **CI completo** (el mismo `ci.yml`: tests, build e integración) sobre el commit del tag.
  3. **Publicar** en Docker Hub las imágenes del artefacto de CI: se cargan, se etiquetan y se
     suben, **sin volver a construir**. Primero la versión de las dos y después `latest`.

  Un tag que no pase la validación hace fallar el workflow de forma visible y no publica
  nada. Si hay que corregirlo: `git push --delete origin <tag>` y crear el tag correcto.

**Build once: se publica exactamente lo que se probó.** Las imágenes se construyen una sola
vez por run. Si publish volviera a construir, una imagen base o un paquete podría cambiar entre
la prueba y la publicación, y se publicaría algo que nadie probó. Para comprobarlo, el resumen
del run muestra el ID de cada imagen en *Construir imágenes* y en *Publicar*: tiene que ser el
mismo.

```text
tests A ─┐
tests B ─┼─▶ integración (artefacto) ─▶ publicar (el mismo artefacto)
build  ──┘   (solo en release.yml)
```

**Concurrencia y tiempos máximos:**

| | CI | Release |
|---|---|---|
| Ejecuciones a la vez | Una por rama / PR / tag | Una en todo el repositorio |
| ¿Se cancela la anterior? | Solo en PRs (en `main` cada commit conserva su resultado) | Nunca: el nuevo espera a que termine el que está en curso |
| `timeout-minutes` | 10 (Python), 15 (Java), 20 (build), 10 (integración) | 5 (validar tag), 30 (publicar) |

Release no se cancela porque cortar una publicación a medias podría dejar backend-a publicado y
backend-b no, y es global porque todos los releases escriben `latest`. GitHub solo deja **un**
release en espera: si llega un tercero, el que esperaba se cancela y hay que relanzarlo
(*Re-run jobs*).

**No hay despliegue automático (CD).** El release solo publica imágenes; el despliegue en los
servidores sigue siendo manual con `compose.deploy.yml`.

Configuración necesaria (GitHub → Settings → Secrets and variables → Actions):

| Secret | Valor |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Access token de Docker Hub (Account settings → Personal access tokens), con permiso de escritura |

Si falta alguno de los dos, el job de publicación falla al principio con un mensaje que lo indica.
Como el usuario es un secret, GitHub lo muestra como `***` en los logs (también dentro de los
nombres de imagen); es solo visual, la imagen se publica con el nombre real.

Publicar una nueva versión (desde un commit que ya esté en `main`):

```bash
git switch main && git pull
git tag v1.0.0
git push origin v1.0.0
```

Si todo pasa, esto publica:

```text
<DOCKERHUB_USERNAME>/backend-a:v1.0.0   y   <DOCKERHUB_USERNAME>/backend-a:latest
<DOCKERHUB_USERNAME>/backend-b:v1.0.0   y   <DOCKERHUB_USERNAME>/backend-b:latest
```

Para desplegar esa versión con `compose.deploy.yml`, usa el tag fijo (no `latest`), p. ej.
`BACKEND_A_IMAGE=<DOCKERHUB_USERNAME>/backend-a:v1.0.0`.

## Estructura

```text
.
├── .github/
│   ├── workflows/          # ci.yml (tests + build + integración) y release.yml (validación + CI + publicar el artefacto)
│   └── dependabot.yml      # PRs semanales de actualización de dependencias
├── backend-a/              # Python + FastAPI
│   ├── app/main.py
│   ├── app/__main__.py     # python -m app: arranca Uvicorn en SERVER_PORT
│   ├── app/config.py       # Settings desde variables de entorno (defaults = dev local)
│   ├── tests/test_main.py
│   ├── requirements.txt    # runtime, versiones fijas (va en la imagen)
│   ├── requirements-dev.txt # runtime + pytest
│   ├── .dockerignore       # fuera de la imagen: tests, venv, cachés y cualquier .env
│   └── Dockerfile          # usuario sin root + HEALTHCHECK
├── backend-b/              # Java 21 + Spring Boot
│   ├── src/main/java/com/example/backendb/
│   │   ├── BackendBApplication.java
│   │   └── controller/
│   │       ├── UserController.java       # /health, /user
│   │       └── BackendAController.java   # /backend-a-status (llama a backend-a)
│   ├── src/main/resources/application.properties
│   ├── src/test/java/com/example/backendb/controller/
│   ├── pom.xml
│   ├── .dockerignore       # fuera de la imagen: target/ y cualquier .env
│   └── Dockerfile          # multi-stage: Maven build → JRE runtime, sin root + HEALTHCHECK
├── scripts/integration-test.sh  # prueba de integración A ↔ B con Docker Compose
├── env/                    # configuración de los contenedores por ambiente
│   ├── dev/backend-a.env.example, backend-b.env.example
│   ├── staging/backend-a.env.example, backend-b.env.example
│   └── prod/backend-a.env.example, backend-b.env.example
├── .env.example            # variables de Compose (DEPLOY_ENV, imágenes, puertos)
├── docker-compose.yml      # desarrollo: construye desde el código
└── compose.deploy.yml      # staging / prod: imágenes versionadas, sin build
```
