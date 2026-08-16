#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="$ROOT_DIR/.local-pids"
LOG_DIR="$ROOT_DIR/logs"

mkdir -p "$PID_DIR" "$LOG_DIR"

# ================================================================
# Configuration
# ================================================================

SERVICES=(
    "eureka-server"
    "config-server"
    "user-service"
    "product-service"
    "order-service"
    "payment-service"
    "shipping-service"
    "delivery-service"
    "inventory-service"
    "notification-service"
    "audit-service"
    "chat-service"
    "product-comment-service"
    "product-media-service"
    "product-review-service"
    "gateway-service"
)

# shellcheck source=local-infra.sh
source "$ROOT_DIR/local-infra.sh"

# Every mongoN port is published to the host (see docker-compose.yml), so the host-JVM flow can be
# just as replica-set-aware as the containerized one (docker-compose.yml's own MONGO_HOST/
# MONGO_REPLICA_SET_PARAM, used by app containers). Without this, every service falls back to
# application.yaml's bare `localhost:27017` default — fine as long as mongo1 happens to be
# primary, but a real replica-set election (a restart, a failover) can make it a secondary at any
# time, and a non-replica-set-aware client has no way to find the actual primary: every write then
# fails with "NotWritablePrimary" until mongo1 is primary again.
export MONGO_HOST="localhost:27017,localhost:27018,localhost:27019"
export MONGO_REPLICA_SET_PARAM="?replicaSet=rs0"

# ================================================================
# Argument parsing
# ================================================================
#
# Default (no flags): start both infra and services, same as before this
# option was added. --infra/--services select a subset; passing both is the
# same as passing neither.

DO_INFRA=true
DO_SERVICES=true

usage() {
    cat <<EOF
Usage: $(basename "$0") [--infra] [--services] [-h|--help]

  --infra       Start Docker infrastructure only (databases, Kafka, Redis, Keycloak, observability stack, etc.)
  --services    Start Maven backend services + frontend only (assumes infra is already running)
  (no flags)    Start both infra and services (default)
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
fi

# ================================================================
# Functions
# ================================================================

start_service() {
    local service="$1"

    echo "  → Starting $service..."

    (
        cd "$ROOT_DIR"
        exec ./mvnw -pl "$service" spring-boot:run
    ) > "$LOG_DIR/$service.log" 2>&1 &

    local pid=$!

    echo "$pid" > "$PID_DIR/$service.pid"

    echo "    PID: $pid"
}

wait_for_url() {
    local name="$1"
    local url="$2"
    local retries="${3:-60}"

    echo -n "  → Waiting for $name"

    for ((i=1; i<=retries; i++)); do
        if curl -sf "$url" >/dev/null 2>&1; then
            echo " ✓"
            return 0
        fi

        echo -n "."
        sleep 1
    done

    echo " ✗"
    echo "    $name did not become available."
    echo "    Check: $LOG_DIR/$name.log"

    return 1
}

# ================================================================
# Check for existing environment
# ================================================================

if $DO_SERVICES; then
    if compgen -G "$PID_DIR/*.pid" > /dev/null; then
        echo
        echo "⚠️  Local environment appears to already be running."
        echo
        echo "Run:"
        echo "  ./stop-local.sh"
        echo
        exit 1
    fi

    rm -f "$PID_DIR"/*.pid
fi

# ================================================================
# Start infrastructure
# ================================================================

if $DO_INFRA; then

echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🐳 Starting infrastructure"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

docker compose up -d "${INFRASTRUCTURE[@]}"

echo
echo "✓ Infrastructure containers started"
echo

fi

# ================================================================
# Build
# ================================================================

if $DO_SERVICES; then

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔨 Building Maven modules"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

./mvnw \
    -pl "$(IFS=,; echo "${SERVICES[*]}")" \
    -am install \
    -DskipTests

echo
echo "✓ Maven build completed"
echo

# ================================================================
# Eureka
# ================================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧩 Starting Eureka"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

start_service "eureka-server"

wait_for_url \
    "eureka-server" \
    "http://localhost:8761"

echo

# ================================================================
# Config Server
# ================================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚙️  Starting Config Server"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

start_service "config-server"

wait_for_url \
    "config-server" \
    "http://localhost:8888/actuator/health"

echo

# ================================================================
# Backend services
# ================================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Starting backend services"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

for service in \
    product-service \
    order-service \
    payment-service \
    shipping-service \
    delivery-service \
    inventory-service \
    notification-service \
    audit-service \
    chat-service \
    product-comment-service \
    product-media-service \
    user-service \
    product-review-service
do
    start_service "$service"
done

echo

# ================================================================
# Gateway
# ================================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🌐 Starting Gateway"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

start_service "gateway-service"

echo

# ================================================================
# Frontend
# ================================================================

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎨 Starting Frontend"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

(
    cd "$ROOT_DIR/frontend"
    exec npm run dev
) > "$LOG_DIR/frontend.log" 2>&1 &

FRONTEND_PID=$!

echo "$FRONTEND_PID" > "$PID_DIR/frontend.pid"

echo "  → Frontend PID: $FRONTEND_PID"

fi

# ================================================================
# Done
# ================================================================

echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Local environment started"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

if $DO_SERVICES; then
    echo "Frontend:       http://localhost:5173"
    echo "Gateway:        http://localhost:8080"
    echo "Eureka:         http://localhost:8761"
    echo "Config Server:  http://localhost:8888"
fi

if $DO_INFRA; then
    echo "Keycloak:       http://localhost:8081"
    echo "Mailpit:        http://localhost:8025"
    echo "Kafka UI:       http://localhost:8099"
    echo "Elasticsearch:  http://localhost:9200"
    echo "Kibana:         http://localhost:5601"
    echo "Grafana:        http://localhost:3000"
    echo "Prometheus:     http://localhost:9090"
fi

echo
echo "Logs:           ./logs/"
echo
echo "Stop with:"
if $DO_INFRA && $DO_SERVICES; then
    echo "  ./stop-local.sh"
else
    stop_flags=""
    $DO_INFRA && stop_flags="$stop_flags --infra"
    $DO_SERVICES && stop_flags="$stop_flags --services"
    echo "  ./stop-local.sh$stop_flags"
fi
echo