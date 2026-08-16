#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="$ROOT_DIR/.local-pids"

# ================================================================
# Argument parsing
# ================================================================
#
# Default (no flags): stop services, then interactively ask about infra —
# same as before this option was added. --infra/--services select a subset;
# passing both is the same as passing neither. -y skips the interactive
# infra prompt (implies "yes, stop infra too").

DO_INFRA=true
DO_SERVICES=true
ASSUME_YES=false
HAD_ARGS=false

if [ "$#" -gt 0 ]; then
    HAD_ARGS=true
fi

usage() {
    cat <<EOF
Usage: $(basename "$0") [--infra] [--services] [-y] [-h|--help]

  --infra       Stop Docker infrastructure only (skip killing local mvnw/npm processes)
  --services    Stop local mvnw/npm processes only (skip the Docker infra prompt/stop)
  -y            Don't prompt — stop infra too without asking
  (no flags)    Stop services, then prompt whether to also stop infra (default)
EOF
}

if [ "$#" -gt 0 ]; then
    DO_INFRA=false
    DO_SERVICES=false

    for arg in "$@"; do
        case "$arg" in
            --infra)
                DO_INFRA=true
                ;;
            --services)
                DO_SERVICES=true
                ;;
            -y)
                ASSUME_YES=true
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                echo "Unknown option: $arg"
                echo
                usage
                exit 1
                ;;
        esac
    done

    # -y alone (no --infra/--services) still means "stop everything, don't ask".
    if ! $DO_INFRA && ! $DO_SERVICES; then
        DO_INFRA=true
        DO_SERVICES=true
    fi
fi

if $DO_SERVICES; then

echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🛑 Stopping local applications"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

if [ ! -d "$PID_DIR" ] || ! compgen -G "$PID_DIR/*.pid" > /dev/null; then
    echo "No locally managed processes found."
    echo
else
    for pid_file in "$PID_DIR"/*.pid; do
        [ -f "$pid_file" ] || continue

        service="$(basename "$pid_file" .pid)"
        pid="$(cat "$pid_file")"

        if kill -0 "$pid" 2>/dev/null; then
            echo "  → Stopping $service (PID $pid)..."

            kill "$pid" 2>/dev/null || true

            # Give the process up to 10 seconds to exit gracefully.
            for _ in {1..10}; do
                if ! kill -0 "$pid" 2>/dev/null; then
                    break
                fi

                sleep 1
            done

            # Force kill if still running.
            if kill -0 "$pid" 2>/dev/null; then
                echo "    Force stopping $service..."
                kill -9 "$pid" 2>/dev/null || true
            fi
        else
            echo "  → $service already stopped"
        fi

        rm -f "$pid_file"
    done

    echo
    echo "✓ Applications stopped."
fi

echo

fi

# ================================================================
# Optional infrastructure shutdown
# ================================================================

if $DO_INFRA; then

    stop_infra=true

    # Only prompt on a bare, no-flags invocation (the original default behavior).
    # An explicit --infra or -y means the user already told us what they want.
    if ! $HAD_ARGS && ! $ASSUME_YES; then
        read -r -p "Stop Docker infrastructure too? [y/N] " answer
        [[ "$answer" =~ ^[Yy]$ ]] || stop_infra=false
    fi

    if $stop_infra; then

        echo
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "🐳 Stopping infrastructure"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo

        docker compose stop \
            mysql \
            mongo1 \
            mongo2 \
            mongo3 \
            mongo-rs-init \
            kafka \
            keycloak \
            mailpit \
            elasticsearch \
            redis \
            vault \
            grafana \
            kafka-ui \
            kafka-ui \
            kibana \
            loki \
            prometheus \
            tempo \
            promtail \
            vault-init

        echo
        echo "✓ Infrastructure stopped."
    fi

fi

echo
echo "Done."
echo