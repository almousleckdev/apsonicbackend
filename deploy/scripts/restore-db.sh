#!/usr/bin/env bash
# Restore a database dump into a SEPARATE database so you can check it (default name: <db>_restore_check).
#   ./restore-db.sh /var/backups/apsonic/db-20261014T020000Z.dump
# Replacing the live database is deliberately a manual, supervised step documented in RUNBOOK.md.
set -euo pipefail
. "$(dirname "$0")/_common.sh"
require_env_file
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

DUMP="${1:?usage: restore-db.sh <dump-file> [target-db]}"
LIVE_DB="${DB_NAME:-apsonic_cms}"
TARGET="${2:-${LIVE_DB}_restore_check}"
USER_="${DB_USER:-apsonic_user}"
[[ -s "$DUMP" ]] || { echo "Cannot read $DUMP" >&2; exit 1; }
[[ "$TARGET" != "$LIVE_DB" ]] || { echo "Refusing to overwrite the live database '$LIVE_DB' with this script." >&2; exit 1; }

compose exec -T postgres psql -U "$USER_" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$TARGET\"" -c "CREATE DATABASE \"$TARGET\""
compose exec -T postgres pg_restore -U "$USER_" -d "$TARGET" --no-owner --exit-on-error < "$DUMP"
echo "Restored into '$TARGET'. Row counts of the main tables:"
compose exec -T postgres psql -U "$USER_" -d "$TARGET" -At -c "SELECT 'users', count(*) FROM users UNION ALL SELECT 'products', count(*) FROM products UNION ALL SELECT 'news', count(*) FROM news UNION ALL SELECT 'flyway migrations', count(*) FROM flyway_schema_history"
echo "Drop it when done:  docker compose ... exec postgres psql -U $USER_ -d postgres -c 'DROP DATABASE \"$TARGET\"'"
