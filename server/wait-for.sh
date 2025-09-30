#!/usr/bin/env sh
# wait-for.sh host:port [timeout seconds]
set -e

if [ $# -lt 1 ]; then
  echo "Usage: $0 host:port [timeout]" >&2
  exit 1
fi

HOSTPORT=$1
TIMEOUT=${2:-60}
HOST=$(echo "$HOSTPORT" | cut -d: -f1)
PORT=$(echo "$HOSTPORT" | cut -d: -f2)

if [ -z "$HOST" ] || [ -z "$PORT" ]; then
  echo "Invalid host:port '$HOSTPORT'" >&2
  exit 1
fi

START=$(date +%s)
while true; do
  if nc -z "$HOST" "$PORT" >/dev/null 2>&1; then
    echo "$HOSTPORT is available"
    exit 0
  fi
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))
  if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
    echo "Timeout after ${TIMEOUT}s waiting for $HOSTPORT" >&2
    exit 1
  fi
  sleep 1
done