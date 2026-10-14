#!/usr/bin/env bash
# Deploy a release: ./deploy.sh <backend-image> <frontend-image>
#   e.g. ./deploy.sh registry.example.com/apsonic/backend:3f9c2ab registry.example.com/apsonic/frontend:3f9c2ab
# Images are used as-is if they are already on the host (docker load) and pulled otherwise.
# If the new release does not become healthy, the previous release is restored automatically.
set -euo pipefail
. "$(dirname "$0")/_common.sh"
require_env_file

BACKEND_IMAGE="${1:?usage: deploy.sh <backend-image> <frontend-image>}"
FRONTEND_IMAGE="${2:?usage: deploy.sh <backend-image> <frontend-image>}"
WAIT_SECONDS="${WAIT_SECONDS:-300}"

[[ ! -f "$RELEASE_FILE" ]] || cp -p "$RELEASE_FILE" "$RELEASE_FILE.prev"
umask 077
printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$BACKEND_IMAGE" "$FRONTEND_IMAGE" > "$RELEASE_FILE"

echo "==> Validating configuration"
compose config -q

echo "==> Starting release: $BACKEND_IMAGE / $FRONTEND_IMAGE"
if compose up -d --pull missing --remove-orphans --wait --wait-timeout "$WAIT_SECONDS"; then
  echo "==> Healthy."
  compose ps
  exit 0
fi

echo "!! New release did not become healthy." >&2
compose logs --tail 60 backend frontend >&2 || true
if [[ -f "$RELEASE_FILE.prev" ]]; then
  echo "!! Rolling back to the previous release." >&2
  mv "$RELEASE_FILE.prev" "$RELEASE_FILE"
  compose up -d --remove-orphans --wait --wait-timeout "$WAIT_SECONDS" && echo "!! Rolled back; the previous release is running." >&2
else
  echo "!! No previous release to roll back to (first deployment). Fix the problem and run again." >&2
fi
exit 1
