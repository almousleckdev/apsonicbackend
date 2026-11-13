#!/usr/bin/env bash
# Remove old release images so the disk does not fill up: ./prune-images.sh [--dry-run]
# Always kept: images used by a running container, the current release, and the previous release (the rollback target).
# Everything else tagged under the same repositories (e.g. apsonic-backend, apsonic-frontend) is removed, plus untagged leftovers.
# Never touches volumes, so the database and uploads are safe.
set -euo pipefail
. "$(dirname "$0")/_common.sh"

DRY_RUN=0
[[ "${1:-}" != "--dry-run" ]] || DRY_RUN=1

# Image names recorded in a release file (BACKEND_IMAGE=..., FRONTEND_IMAGE=...).
release_images() { if [[ -f "$1" ]]; then sed -n 's/^[A-Z_]*IMAGE=//p' "$1"; fi; }

recorded="$(release_images "$RELEASE_FILE"; release_images "$RELEASE_FILE.prev")"
running="$(docker ps --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Image}}')"
keep="$(printf '%s\n%s\n' "$recorded" "$running" | sed '/^$/d' | sort -u)"
repos="$(printf '%s\n' "$recorded" | sed '/^$/d; s/:[^:/]*$//' | sort -u)"

if [[ -z "$repos" ]]; then
  echo "No release recorded in $RELEASE_FILE; nothing to prune." >&2
  exit 0
fi

removed=0
while IFS= read -r repo; do
  while IFS= read -r image; do
    [[ -n "$image" && "$image" != *"<none>"* ]] || continue
    if printf '%s\n' "$keep" | grep -Fxq -- "$image"; then continue; fi
    if [[ "$DRY_RUN" == 1 ]]; then
      echo "would remove $image"
    elif docker rmi "$image" >/dev/null; then
      echo "removed $image"
    else
      echo "kept $image (still in use)" >&2
      continue
    fi
    removed=$((removed + 1))
  done < <(docker images --format '{{.Repository}}:{{.Tag}}' "$repo")
done <<< "$repos"

[[ "$DRY_RUN" == 1 ]] || docker image prune -f >/dev/null
echo "Kept: $(printf '%s' "$keep" | tr '\n' ' ')"
if [[ "$DRY_RUN" == 1 ]]; then echo "Old images that would be removed: $removed"; else echo "Old images removed: $removed"; fi
df -h / | tail -1
