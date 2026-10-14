#!/usr/bin/env bash
# Return to the previous release (the one deploy.sh recorded in release.env.prev).
# NOTE: database migrations are forward-only. If the bad release applied a migration, restoring the previous
# images does not undo it; restore a backup if the old code cannot run on the new schema (see RUNBOOK.md).
set -euo pipefail
. "$(dirname "$0")/_common.sh"
require_env_file
[[ -f "$RELEASE_FILE.prev" ]] || { echo "No previous release recorded ($RELEASE_FILE.prev)." >&2; exit 1; }
mv "$RELEASE_FILE" "$RELEASE_FILE.rolledback"
mv "$RELEASE_FILE.prev" "$RELEASE_FILE"
compose up -d --remove-orphans --wait --wait-timeout "${WAIT_SECONDS:-300}"
compose ps
