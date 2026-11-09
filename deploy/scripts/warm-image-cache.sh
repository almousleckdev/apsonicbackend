#!/usr/bin/env bash
# Visits the public pages once so the picture resizer has every resized picture ready. The server (2 CPUs) resizes a
# picture the first time anyone asks for it; without this, the first visitor after a release waits for all of them.
#   ./warm-image-cache.sh https://staging.apsonic-moto.com
# Safe to run any time and to repeat: it only reads public pages. Run it right after deploy.sh.
set -euo pipefail

SITE="${1:?usage: warm-image-cache.sh <site-url>, e.g. https://staging.apsonic-moto.com}"
SITE="${SITE%/}"
PARALLEL="${PARALLEL:-2}"
PAGES=("/" "/fr" "/zh" "/products" "/services" "/news" "/contact")

urls_file="$(mktemp)"
trap 'rm -f "$urls_file"' EXIT

for page in "${PAGES[@]}"; do
  # Picture addresses in the page, as the browser would request them (HTML escapes undone).
  curl -fsS --max-time 60 "$SITE$page" 2>/dev/null \
    | grep -oE '/_next/image\?[^" ,\\]+' \
    | sed 's/&amp;/\&/g' >> "$urls_file" || echo "skipped $page (not reachable)" >&2
done

sort -u "$urls_file" -o "$urls_file"
total="$(wc -l < "$urls_file" | tr -d ' ')"
echo "Warming $total pictures on $SITE (parallel: $PARALLEL)"

# The Accept header matches what browsers send, so the same format is cached.
xargs -P "$PARALLEL" -I{} \
  curl -fsS -o /dev/null --max-time 180 -H 'Accept: image/avif,image/webp,image/*,*/*;q=0.8' "$SITE{}" < "$urls_file" \
  || echo "some pictures could not be fetched; run again if needed" >&2
echo "Done."
