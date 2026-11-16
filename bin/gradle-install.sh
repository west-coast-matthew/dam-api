#!/usr/bin/env bash
# Build dam-api and install it into the local Maven repository (~/.m2).
# Note: despite the name, this project builds with Maven; this wraps `mvn install`.
#
# Usage: bin/gradle-install.sh [--skip-tests] [extra maven args...]
#   e.g. bin/gradle-install.sh --skip-tests -o
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

MVN_ARGS=(-B clean install)
if [[ "${1:-}" == "--skip-tests" ]]; then
  MVN_ARGS+=(-DskipTests)
  shift
fi

# Prefer an installed Maven; fall back to the project wrapper
if command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  MVN=./mvnw
fi

echo ">> $MVN ${MVN_ARGS[*]} $*"
exec "$MVN" "${MVN_ARGS[@]}" "$@"
