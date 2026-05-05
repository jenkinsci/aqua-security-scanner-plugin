#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Building plugin (in Docker) and starting Jenkins..."
cd "$SCRIPT_DIR"
docker compose up --build -d

echo ""
echo "==> Jenkins is starting up at http://localhost:8080"
echo "    Credentials: admin / admin"
echo ""
echo "    To view logs:  docker compose -f $SCRIPT_DIR/docker-compose.yml logs -f"
echo "    To stop:       docker compose -f $SCRIPT_DIR/docker-compose.yml down"
echo "    To wipe data:  docker compose -f $SCRIPT_DIR/docker-compose.yml down -v"
