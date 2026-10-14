# shellcheck shell=bash
# Shared by the deploy scripts. Sourced, not executed.
APP_DIR="${APP_DIR:-/opt/apsonic}"
PROJECT="${COMPOSE_PROJECT_NAME:-apsonic}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env.production}"
RELEASE_FILE="${RELEASE_FILE:-$APP_DIR/release.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/docker-compose.prod.yml}"

compose() {
  local args=(-p "$PROJECT" --env-file "$ENV_FILE")
  [[ -f "$RELEASE_FILE" ]] && args+=(--env-file "$RELEASE_FILE")
  docker compose "${args[@]}" -f "$COMPOSE_FILE" "$@"
}

require_env_file() {
  [[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE (copy .env.production.example and fill it in)." >&2; exit 1; }
  local mode; mode=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")
  [[ "$mode" == "600" ]] || { echo "Refusing: $ENV_FILE has mode $mode; it holds secrets, run: chmod 600 $ENV_FILE" >&2; exit 1; }
}
