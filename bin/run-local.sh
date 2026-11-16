#!/usr/bin/env bash
# Run dam-api locally with Maven (no jar build), using the local config file
# that holds the MariaDB credentials.
#
# Config: /etc/app-configs/dam-poc/application.properties
#   (override with DAM_CONFIG=/path/to/application.properties)
#
# Usage: bin/run-local.sh [extra spring args...]
#   e.g. bin/run-local.sh --server.port=9090
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_FILE="${DAM_CONFIG:-/etc/app-configs/dam-poc/application.properties}"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "ERROR: config file not found: $CONFIG_FILE" >&2
  echo "Create it from the sample: $PROJECT_DIR/config/application.properties.sample" >&2
  exit 1
fi
if [[ ! -r "$CONFIG_FILE" ]]; then
  echo "ERROR: config file not readable: $CONFIG_FILE" >&2
  exit 1
fi

cd "$PROJECT_DIR"

# Prefer an installed Maven; fall back to the project wrapper
if command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  MVN=./mvnw
fi

# additional-location keeps the bundled defaults and layers the external file on top
RUN_ARGS="--spring.config.additional-location=file:$CONFIG_FILE"
for arg in "$@"; do
  RUN_ARGS="$RUN_ARGS,$arg"
done

echo ">> Running dam-api with config $CONFIG_FILE"
exec "$MVN" -B spring-boot:run -Dspring-boot.run.arguments="$RUN_ARGS"
