#!/bin/sh
# Run with sh; no Python, OpenSSL, GPG or jq installation needed on the host.
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
root="$PWD"
action="${1:-}"
file="${2:-}"
case "$action" in comprobar|renovar-certificado|respaldar|restaurar) ;; *) echo 'Uso: sh scripts/operar.sh comprobar|renovar-certificado|respaldar|restaurar [archivo.gpg]' >&2; exit 1;; esac
LOCAL_UID="${LOCAL_UID:-$(id -u)}"; LOCAL_GID="${LOCAL_GID:-$(id -g)}"
export LOCAL_UID LOCAL_GID
compose() { docker compose --ansi never --progress plain -f compose.yaml "$@"; }
tool() {
  docker run --rm --network none --user "$LOCAL_UID:$LOCAL_GID" --workdir /workspace \
    --mount "type=bind,source=$root,target=/workspace" --entrypoint python \
    control-ppp-tools:local /tools/maintenance.py "$@"
}
readtool() {
  docker run --rm --network none --user "$LOCAL_UID:$LOCAL_GID" --workdir /workspace \
    --mount "type=bind,source=$root,target=/workspace,readonly" --entrypoint python \
    control-ppp-tools:local /tools/maintenance.py "$@"
}
jsonpath() { docker run --rm -i --network none --entrypoint python control-ppp-tools:local /tools/maintenance.py json-path "$@"; }
healthy() {
  for service in oracle backend frontend; do
    cid="$(compose ps -q "$service")"
    [ -n "$cid" ] || { echo "Falta $service; seguir el inicio del README." >&2; return 1; }
    state="$(docker inspect --format '{{.State.Running}}/{{.State.Health.Status}}' "$cid")"
    [ "$state" = 'true/healthy' ] || { echo "$service no está saludable; consultar registros." >&2; return 1; }
  done
}
schema() { compose exec -T oracle bash /operations/check-schema.sh; }
live() {
  cid="$(compose ps -q frontend)"
  docker run --rm --network "container:$cid" --user "$LOCAL_UID:$LOCAL_GID" --workdir /workspace \
    --mount "type=bind,source=$root,target=/workspace,readonly" --entrypoint python \
    control-ppp-tools:local /tools/maintenance.py live-check
}
docker image inspect control-ppp-tools:local >/dev/null
if [ "$action" = restaurar ]; then
  [ -n "$file" ] && [ "$file" = "$(basename -- "$file")" ] || { echo 'Indicar solo el nombre dentro de backups/.' >&2; exit 1; }
  [ ! -e .env ] && [ ! -e secrets ] || { echo 'La recuperación requiere un destino sin .env ni secrets.' >&2; exit 1; }
  [ -z "${ORACLE_IMAGE:-}" ] || { echo 'Quitar ORACLE_IMAGE del entorno.' >&2; exit 1; }
  [ -z "$(compose ps -aq)" ] || { echo 'El proyecto ya tiene contenedores; usar otra instalación vacía.' >&2; exit 1; }
elif [ "$action" = comprobar ]; then
  readtool check
fi
case "$action" in
  comprobar)
    healthy; schema; live
    echo 'Diagnóstico completado. No se modificaron datos.' ;;
  renovar-certificado)
    tool renew
    compose up -d --no-deps --force-recreate --wait --wait-timeout 120 frontend
    readtool check; live ;;
  respaldar)
    healthy; schema
    volume="$(compose config --format json | jsonpath volumes oracle-data name)"
    project="$(compose config --format json | jsonpath name)"
    owner="$(docker volume inspect "$volume" | jsonpath 0 Labels com.docker.compose.project)"
    [ "$owner" = "$project" ] || { echo 'El volumen no pertenece al proyecto.' >&2; exit 1; }
    cid="$(compose ps -q oracle)"
    image="$(docker inspect --format '{{.Image}}' "$cid")"
    digest="$(docker image inspect --format '{{index .RepoDigests 0}}' "$image")"
    commit="$(git rev-parse HEAD 2>/dev/null || printf no-disponible)"
    echo 'Se detendrán temporalmente los tres servicios para un respaldo consistente.'
    restart() { code=$?; trap - EXIT HUP INT TERM; compose up -d --wait --wait-timeout 900 || exit 1; exit "$code"; }
    trap restart EXIT
    trap 'exit 1' HUP INT TERM
    compose stop frontend backend oracle
    [ -z "$(compose ps --status running -q)" ] || { echo 'Hay servicios activos; respaldo cancelado.' >&2; exit 1; }
    docker run --rm -it --network none --user 0:0 --workdir /workspace \
      --mount "type=bind,source=$root,target=/workspace" --mount "type=volume,source=$volume,target=/oracle-data,readonly" \
      -e "LOCAL_UID=$LOCAL_UID" -e "LOCAL_GID=$LOCAL_GID" -e "BACKUP_ORACLE_IMAGE=$digest" -e "BACKUP_GIT_COMMIT=$commit" \
      --entrypoint python control-ppp-tools:local /tools/maintenance.py backup ;;
  restaurar)
    volume="$(compose config --format json | jsonpath volumes oracle-data name)"
    project="$(compose config --format json | jsonpath name)"
    docker volume create --label "com.docker.compose.project=$project" --label com.docker.compose.volume=oracle-data "$volume" >/dev/null
    owner="$(docker volume inspect "$volume" | jsonpath 0 Labels com.docker.compose.project)"
    [ "$owner" = "$project" ] || { echo 'El volumen no pertenece al proyecto.' >&2; exit 1; }
    docker run --rm -it --network none --user 0:0 --workdir /workspace \
      --mount "type=bind,source=$root,target=/workspace" --mount "type=volume,source=$volume,target=/oracle-data" \
      -e "LOCAL_UID=$LOCAL_UID" -e "LOCAL_GID=$LOCAL_GID" --entrypoint python \
      control-ppp-tools:local /tools/maintenance.py restore --file "$file"
    echo 'Restaurado. Continuar con up --build y comprobar, según README.' ;;
esac
