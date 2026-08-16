#!/usr/bin/env bash
#
# Starts/stops the local k3d cluster ("demo") together with the docker-compose infra it still
# depends on (pods reach Grafana/Kibana via host.k3d.internal — see k8s/configmap-common.yaml
# and docker-compose.yml). Wraps the manual sequence:
#
#   k3d cluster stop demo && docker compose stop
#   docker compose up -d && k3d cluster start demo && kubectl get pods -A -w
#
# Only starts the infra k8s pods actually need, NOT docker-compose's app containers (Option B's
# full-docker-compose-stack mode — irrelevant here, the cluster runs its own pods) and NOT the
# dev-only pieces docker-compose also happens to host: Kafka, Redis, MySQL, Keycloak, Mailpit,
# MongoDB, and Elasticsearch all now run in-cluster (k8s/kafka.yaml, k8s/redis.yaml,
# k8s/mysql.yaml, k8s/keycloak.yaml, k8s/mailpit.yaml, k8s/mongo.yaml, k8s/elasticsearch.yaml);
# kafka-ui only matters once kafka is reachable from the host; this compose file's own Loki is
# the host-JVM/compose-flow instance specifically — k8s has its own separate in-cluster one
# (k8s/loki.yaml); its own Promtail only ships logs to this file's own Loki (k8s pods get their
# logs shipped by the in-cluster k8s/promtail-daemonset.yaml instead). Prometheus is dev-only for
# a different reason: this branch has no in-cluster Prometheus at all — pod IPs on the k3d overlay
# network were never reachable from docker-compose's Prometheus anyway, so it never actually
# scraped k8s pods here. Pass --with-dev to also start/stop the dev-only set, e.g. if you're
# running start-local.sh's host-JVM services against the cluster at the same time.
#
# See start-local.sh/stop-local.sh for the non-k8s (mvnw/npm) local dev flow instead.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="demo"

# shellcheck source=local-infra.sh
source "$ROOT_DIR/local-infra.sh"

usage() {
    cat <<EOF
Usage: $(basename "$0") <start|stop|restart|status> [--with-dev] [--no-watch]

  start      docker compose up -d (k8s-needed infra only), then k3d cluster start $CLUSTER_NAME
  stop       k3d cluster stop $CLUSTER_NAME, then docker compose stop (the full infra set,
             regardless of --with-dev, so nothing is left running behind)
  restart    stop, then start
  status     k3d cluster list + kubectl get pods -A

  --with-dev   Also start (or explicitly target on stop) the dev-only infra pieces docker-compose
               hosts but k8s pods don't use: Kafka, Redis, Kafka UI, MySQL, Keycloak, Mailpit,
               MongoDB, Elasticsearch, and docker-compose's own Prometheus/Loki/Promtail.
               Use this if you're also running start-local.sh's host-JVM services against the
               same docker-compose stack.
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
WITH_DEV=false

for arg in "$@"; do
    case "$arg" in
        --no-watch)
            WATCH=false
            ;;
        --with-dev)
            WITH_DEV=true
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

    # Always the full set on stop (regardless of --with-dev): if the dev-only pieces happen to be
    # running — e.g. a prior --with-dev start, or start-local.sh was also used — this cleans them
    # up too rather than leaving them behind. Stopping an already-stopped/non-running service is a
    # harmless no-op.
    (cd "$ROOT_DIR" && docker compose stop "${INFRASTRUCTURE[@]}")

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

    if $WITH_DEV; then
        (cd "$ROOT_DIR" && docker compose up -d "${INFRASTRUCTURE[@]}")
    else
        (cd "$ROOT_DIR" && docker compose up -d "${K8S_INFRASTRUCTURE[@]}")
    fi

    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "☸️  Starting k3d cluster ($CLUSTER_NAME)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo

    if ! cluster_exists; then
        echo "  → Cluster '$CLUSTER_NAME' does not exist yet."
        echo "    Create it first — see README.md's 'Kubernetes / GitOps' section:"
        echo "    k3d cluster create $CLUSTER_NAME --servers 1 --agents 2 -p \"18090:80@loadbalancer\" -p \"18453:443@loadbalancer\" -p \"8081:8081@loadbalancer\" --api-port 6550"
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
