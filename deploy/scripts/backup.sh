#!/usr/bin/env bash
# Back up the database and uploaded files. Run daily from cron as the deploy user (see RUNBOOK.md).
#
# IMPORTANT: a backup on the same disk as the data does not survive losing the server. Set OFFSITE_CMD to copy each
# finished file somewhere else (e.g. an Alibaba Cloud OSS bucket), e.g.:
#   OFFSITE_CMD='ossutil cp {} oss://my-backup-bucket/apsonic/'      ({} is replaced by the file path)
set -euo pipefail
. "$(dirname "$0")/_common.sh"
require_env_file
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

BACKUP_DIR="${BACKUP_DIR:-/var/backups/apsonic}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
umask 077
mkdir -p "$BACKUP_DIR"

DB_FILE="$BACKUP_DIR/db-$STAMP.dump"
echo "==> Database -> $DB_FILE"
compose exec -T postgres pg_dump -U "${DB_USER:-apsonic_user}" -d "${DB_NAME:-apsonic_cms}" -Fc > "$DB_FILE.partial"
mv "$DB_FILE.partial" "$DB_FILE"
[[ -s "$DB_FILE" ]] || { echo "Backup file is empty" >&2; exit 1; }

UPLOADS_FILE="$BACKUP_DIR/uploads-$STAMP.tar.gz"
echo "==> Uploads -> $UPLOADS_FILE"
# Written through the host shell (not inside the container) so the file gets this script's umask (600), like the DB dump.
docker run --rm -v "${PROJECT}_uploads:/data:ro" alpine:3 tar czf - -C /data . > "$UPLOADS_FILE.partial"
mv "$UPLOADS_FILE.partial" "$UPLOADS_FILE"

if [[ -n "${OFFSITE_CMD:-}" ]]; then
  for f in "$DB_FILE" "$UPLOADS_FILE"; do
    echo "==> Off-site copy: $f"
    bash -c "${OFFSITE_CMD//\{\}/$f}"
  done
else
  echo "!! OFFSITE_CMD is not set: these backups exist only on this server." >&2
fi

echo "==> Removing local backups older than $KEEP_DAYS days"
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'db-*.dump' -o -name 'uploads-*.tar.gz' \) -mtime "+$KEEP_DAYS" -print -delete
echo "Done."
