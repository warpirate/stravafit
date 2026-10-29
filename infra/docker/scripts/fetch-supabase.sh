#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Vendors the official Supabase self-host stack (the `docker/` directory of
# github.com/supabase/supabase) into infra/docker/supabase at a pinned commit.
# The directory is git-ignored; re-run this script to upgrade (change the ref,
# read the upstream changelog, then `docker compose pull && docker compose up -d`).
#
# Usage: scripts/fetch-supabase.sh [git-ref]
set -euo pipefail

# Pinned upstream commit (2026-10-29). Bump deliberately, never track a branch.
REF="${1:-${SUPABASE_REF:-9eaae4cd2cf8e67f9aeadab044e9a14429331c13}}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$HERE/supabase"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git -C "$TMP" init -q
git -C "$TMP" remote add origin https://github.com/supabase/supabase.git
git -C "$TMP" sparse-checkout set docker
git -C "$TMP" fetch -q --depth 1 --filter=blob:none origin "$REF"
git -C "$TMP" checkout -q FETCH_HEAD

rm -rf "$DEST"
cp -R "$TMP/docker" "$DEST"
echo "$REF" > "$DEST/.supabase-ref"
echo "Supabase self-host stack at $REF -> $DEST"
