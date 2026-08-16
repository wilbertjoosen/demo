#!/usr/bin/env bash
#
# Starts/stops the local k3d cluster ("demo") together with the docker-compose infra it depends
# on (pods reach MySQL/Mongo/Kafka/Redis/etc. via host.k3d.internal — see k8s/configmap-common.yaml
# and docker-compose.yml). Wraps the manual sequence:
#
#   k3d cluster stop demo && docker compose stop
#   docker compose up -d && k3d cluster start demo && kubectl get pods -A -w
#
# See start-local.sh/stop-local.sh for the non-k8s (mvnw/npm) local dev flow instead.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="demo"

usage() {
    cat <<EOF
Usage: $(basename "$0") <start|stop|restart|status> [--no-watch]

  start      docker compose up -d, then k3d cluster start $CLUSTER_NAME
  stop       k3d cluster stop $CLUSTER_NAME, then docker compose stop
  restart    stop, then start
  status     k3d cluster list + kubectl get pods -A

  --no-watch   After "start" or "restart", don't tail pod status
               (by default, start/restart end with 'kubectl get pods -A -w')
EOF
}

if [ "$#" -eq 0 ]; then
    usage
    exit 1
fi

COMMAND="$1"
shift

WATCH=true

for arg in "$@"; do
    case "$arg" in
        --no-watch)
            WATCH=false
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

cluster_exists() {
    k3d cluster list -o json 2>/dev/null | grep -q "\"name\":\"$CLUSTER_NAME\""
}

do_stop() {
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🛑 Stopping k3d cluster ($CLUSTER_NAME)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    if cluster_exists; then
        k3d cluster stop "$CLUSTER_NAME"
    else
        echo "  → Cluster '$CLUSTER_NAME' not found, skipping."
    fi

    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🐳 Stopping infrastructure"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    (cd "$ROOT_DIR" && docker compose stop)

    echo
    echo "✓ Stopped."
    echo
}

do_start() {
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🐳 Starting infrastructure"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    (cd "$ROOT_DIR" && docker compose up -d)

    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "☸️  Starting k3d cluster ($CLUSTER_NAME)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    if ! cluster_exists; then
        echo "  → Cluster '$CLUSTER_NAME' does not exist yet."
        echo "    Create it first — see README.md's 'Kubernetes / GitOps' section:"
        echo "    k3d cluster create $CLUSTER_NAME --servers 1 --agents 2 -p \"18090:80@loadbalancer\" -p \"18453:443@loadbalancer\" --api-port 6550"
        exit 1
    fi

    k3d cluster start "$CLUSTER_NAME"

    echo
    echo "✓ Started."
    echo

    if $WATCH; then
        echo "Watching pods (Ctrl+C to stop watching — the cluster keeps running):"
        echo
        kubectl get pods -A -w
    fi
}

do_status() {
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "☸️  k3d cluster status"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    k3d cluster list

    if cluster_exists; then
        echo
        kubectl get pods -A
    fi
}

case "$COMMAND" in
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    restart)
        do_stop
        do_start
        ;;
    status)
        do_status
        ;;
    -h|--help)
        usage
        ;;
    *)
        echo "Unknown command: $COMMAND"
        echo
        usage
        exit 1
        ;;
esac
