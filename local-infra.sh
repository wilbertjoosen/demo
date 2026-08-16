# Shared by start-local.sh, stop-local.sh, and k8s-local.sh — single source of truth for which
# docker-compose services count as "infra" (and, of those, which are only needed by the host-JVM
# dev flow / Option B's full-docker-compose stack), so none of the three scripts can drift out of
# sync with each other.
#
# No vault/vault-init here (unlike main/testing) — this branch's docker-compose.yml doesn't define
# them, and order-service here still reads DB_USERNAME/DB_PASSWORD as plain env vars rather than
# fetching them from Vault at startup.

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
    "grafana"
    "tempo"
    "kibana"
)

# Of the above, the ones k8s pods no longer depend on — Mongo/Elasticsearch/Grafana/Loki/Tempo/
# Kibana are still reached by pods via host.k3d.internal (see k8s/configmap-common.yaml), but
# Kafka, Redis, MySQL, Keycloak, and Mailpit all moved in-cluster (k8s/kafka.yaml, k8s/redis.yaml,
# k8s/mysql.yaml, k8s/keycloak.yaml, k8s/mailpit.yaml); kafka-ui only makes sense once kafka itself
# is reachable from the host; this compose file's own "prometheus" is the host-JVM/compose-flow
# instance specifically — k8s has its own separate in-cluster one (k8s/prometheus.yaml).
DEV_ONLY_INFRASTRUCTURE=(
    "kafka"
    "kafka-ui"
    "redis"
    "prometheus"
    "mysql"
    "keycloak"
    "mailpit"
)

# What a k8s-only workflow (k8s-local.sh's default) actually needs: INFRASTRUCTURE minus
# DEV_ONLY_INFRASTRUCTURE. Computed rather than hand-duplicated so the two lists above can't
# silently drift apart from this one.
K8S_INFRASTRUCTURE=()
for __svc in "${INFRASTRUCTURE[@]}"; do
    __dev_only=false
    for __dev_svc in "${DEV_ONLY_INFRASTRUCTURE[@]}"; do
        if [ "$__svc" = "$__dev_svc" ]; then
            __dev_only=true
            break
        fi
    done
    $__dev_only || K8S_INFRASTRUCTURE+=("$__svc")
done
unset __svc __dev_svc __dev_only
