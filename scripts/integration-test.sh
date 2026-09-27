#!/usr/bin/env bash
# Prueba de integración: levanta los dos backends con docker-compose.yml (build desde el
# código) y comprueba la comunicación real en ambos sentidos. La usan CI y desarrollo local.
#
#   scripts/integration-test.sh
#
# Usa su propio proyecto de Compose y otros puertos del host, así que no toca un stack de
# desarrollo que ya esté levantado. Al terminar (bien o mal) borra sus contenedores y su red.
# Requisitos: docker con Compose v2, curl y jq.
set -euo pipefail

cd "$(dirname "$0")/.."

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-demo-integration}"
export DEPLOY_ENV="${DEPLOY_ENV:-dev}"
export BACKEND_A_HOST_PORT="${BACKEND_A_HOST_PORT:-18000}"
export BACKEND_B_HOST_PORT="${BACKEND_B_HOST_PORT:-18080}"

A="http://localhost:${BACKEND_A_HOST_PORT}"
B="http://localhost:${BACKEND_B_HOST_PORT}"

for tool in docker curl jq; do
  command -v "$tool" >/dev/null || { echo "Falta '$tool'" >&2; exit 1; }
done

# docker-compose.yml exige env/<DEPLOY_ENV>/*.env; si no existen se crean desde los *.example
for service in backend-a backend-b; do
  file="env/${DEPLOY_ENV}/${service}.env"
  if [[ ! -f "$file" ]]; then
    cp "${file}.example" "$file"
    echo "Creado $file desde ${file}.example"
  fi
done

cleanup() {
  local status=$?
  if [[ $status -ne 0 ]]; then
    echo "--- La prueba falló: estado y logs de los contenedores" >&2
    docker compose ps -a >&2 || true
    docker compose logs --no-color --tail 100 >&2 || true
  fi
  docker compose down --remove-orphans >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

# --wait: espera a que los healthchecks de ambos servicios pasen (falla si no en 120 s)
docker compose up -d --build --wait --wait-timeout 120

failures=0

# check <descripción> <url> <filtro jq que debe ser true>
check() {
  local name=$1 url=$2 filter=$3 body
  if body=$(curl -fsS --max-time 10 "$url") && jq -e "$filter" <<<"$body" >/dev/null 2>&1; then
    echo "OK   $name"
  else
    echo "FAIL $name -> ${body:0:200}" >&2
    failures=$((failures + 1))
  fi
}

check "backend-a /health" "$A/health" '.status == "ok" and .service == "backend-a"'
check "backend-b /health" "$B/health" '.status == "ok" and .service == "backend-b"'
check "A -> B  /profile"  "$A/profile" '.user.id == 1 and .user.name == "John Doe"'
check "B -> A  /backend-a-status" "$B/backend-a-status" \
  '.backendA.status == "ok" and .backendA.service == "backend-a"'

for service in backend-a backend-b; do
  uid=$(docker compose exec -T "$service" id -u)
  if [[ "$uid" != "0" ]]; then
    echo "OK   $service corre sin root (uid $uid)"
  else
    echo "FAIL $service corre como root" >&2
    failures=$((failures + 1))
  fi
done

if [[ $failures -ne 0 ]]; then
  echo "$failures comprobación(es) fallida(s)" >&2
  exit 1
fi
echo "Integración OK"
