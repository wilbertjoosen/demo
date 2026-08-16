# Shared by start-local.sh and stop-local.sh — single source of truth for which docker-compose
# services count as "infra", so the two scripts can't drift out of sync with each other.

INFRASTRUCTURE=(
    "mysql"
    "mongo1"
    "mongo2"
    "mongo3"
    "mongo-rs-init"
    "kafka"
    "kafka-ui"
    "loki"
    "prometheus"
    "keycloak"
    "mailpit"
    "promtail"
    "elasticsearch"
    "redis"
    "vault"
    "vault-init"
    "grafana"
    "tempo"
    "kibana"
)
