#!/usr/bin/env bash
#
# Install Git hooks for Antikythera development.
# Usage: ./scripts/install-hooks.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Installing Git hooks..."

cp "$SCRIPT_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"

echo "Done. Pre-commit hook installed."
