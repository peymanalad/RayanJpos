#!/usr/bin/env sh
#
# wait-for-it.sh
#   Use this script to block until a host:port is available before executing a command.
#   Based on the original implementation by Giles Hall (https://github.com/vishnubob/wait-for-it)
#   and distributed under the MIT license.

set -eu

usage() {
  cat <<USAGE >&2
Usage: ${0##*/} host:port [options] [-- command [args]]

Options:
  -h, --help             Show this help message and exit
  -t, --timeout seconds  Maximum number of seconds to wait (default: 30)
  -q, --quiet            Do not output any status messages
USAGE
}

log() {
  if [ "${QUIET:-0}" -eq 0 ]; then
    printf '%s\n' "$*"
  fi
}

HOST=""
PORT=""
TIMEOUT=30
QUIET=0
COMMAND=""

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    -t|--timeout)
      if [ $# -lt 2 ]; then
        printf 'Error: --timeout requires an argument\n' >&2
        exit 1
      fi
      TIMEOUT=$2
      shift 2
      ;;
    -q|--quiet)
      QUIET=1
      shift
      ;;
    --)
      shift
      COMMAND="$*"
      break
      ;;
    *)
      if [ -z "$HOST" ]; then
        HOST=${1%%:*}
        PORT=${1##*:}
      else
        COMMAND="$*"
        break
      fi
      shift
      ;;
  esac
done

if [ -z "$HOST" ] || [ -z "$PORT" ]; then
  printf 'Error: host:port pair is required\n' >&2
  usage
  exit 1
fi

if ! echo "$PORT" | grep -Eq '^[0-9]+$'; then
  printf 'Error: invalid port "%s"\n' "$PORT" >&2
  exit 1
fi

START=$(date +%s)
while :; do
  if nc -z "$HOST" "$PORT" >/dev/null 2>&1; then
    log "${HOST}:${PORT} is available"
    if [ -n "$COMMAND" ]; then
      exec sh -c "$COMMAND"
    fi
    exit 0
  fi
  NOW=$(date +%s)
  ELAPSED=$((NOW - START))
  if [ "$TIMEOUT" -gt 0 ] && [ "$ELAPSED" -ge "$TIMEOUT" ]; then
    log "Timeout after ${TIMEOUT}s waiting for ${HOST}:${PORT}"
    exit 1
  fi
  sleep 1
done