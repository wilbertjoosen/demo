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
    "common-service"
    "gateway-service"
    "reporting-service"
)

# shellcheck source=local-infra.sh
source "$ROOT_DIR/local-infra.sh"

# NOT setting MONGO_HOST here, on purpose: the docker-compose `mongo` container is a plain
# standalone instance (no replica set), so every service's own application.yaml default
# (localhost:27017) already points at it correctly with zero env vars needed.

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
    # product-media-service's StoragePort has no local-disk fallback (S3StorageAdapter is its only
    # implementation) and these four have no defaults in its application.yaml — without them it
    # fails at boot with a placeholder-resolution error, easy to miss among 17 other services'
    # startup output. Warn instead of blocking the whole run: everything else works fine without them.
    missing_aws_vars=()
    for var in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_S3_BUCKET_NAME AWS_S3_STAGING_BUCKET_NAME; do
        if [ -z "${!var:-}" ]; then
            missing_aws_vars+=("$var")
        fi
    done

    if [ "${#missing_aws_vars[@]}" -gt 0 ]; then
        echo
        echo "⚠️  product-media-service needs real AWS S3 credentials — missing: ${missing_aws_vars[*]}"
        echo "    It will fail to start without them (no local-disk fallback). Export them first,"
        echo "    or ignore this if you don't need media upload this session."
        echo
    fi

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
    reporting-service \
    user-service \
    product-review-service \
    common-service
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
    echo "Keycloak:       http://keycloak.localhost:8181"
    echo "Mailpit:        http://localhost:8025"
    echo "Kafka UI:       http://localhost:8095"
    echo "Elasticsearch:  http://localhost:9200"
fi
# Kibana/Grafana/Prometheus/Loki/Promtail/Tempo used to print here too — all fully in-cluster now
# (see local-infra.sh's header comment), no docker-compose service left to point at.

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