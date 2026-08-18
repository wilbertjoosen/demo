# Shared by start-local.sh, stop-local.sh, and k8s-local.sh — single source of truth for which
# docker-compose services count as "infra" (and, of those, which are only needed by the host-JVM
# dev flow / Option B's full-docker-compose stack), so none of the three scripts can drift out of
# sync with each other.
#
# This branch's docker-compose.yml differs from main/testing in three ways this list has to match:
# no vault/vault-init (order-service here still reads DB_USERNAME/DB_PASSWORD as plain env vars,
# no Vault integration to wire up), no tempo (not defined at all here), and a single "mongodb"
# container rather than a 3-node mongo1/2/3 + mongo-rs-init replica set.
#
# Grafana/Prometheus/Loki/Promtail/Kibana aren't in docker-compose.yml at all any more — every one
# of them already has an in-cluster equivalent (k8s/grafana.yaml, k8s/prometheus.yaml, k8s/loki.yaml,
# k8s/promtail-daemonset.yaml, k8s/kibana.yaml), so the host-compose copies were pure duplication
# regardless of which local dev flow (host-JVM or k8s) you're using — removed rather than kept
# dev-only.

INFRASTRUCTURE=(
    "mysql"
    "mongodb"
    "kafka"
    "kafka-ui"
    "keycloak"
    "mailpit"
    "elasticsearch"
    "redis"
)

# Of the above, the ones k8s pods no longer depend on — Kafka, Redis, MySQL, Keycloak, Mailpit
# (k8s/kafka.yaml, k8s/redis.yaml, k8s/mysql.yaml, k8s/keycloak.yaml, k8s/mailpit.yaml), MongoDB,
# and Elasticsearch (k8s/mongo.yaml, k8s/elasticsearch.yaml) all have in-cluster equivalents.
# kafka-ui only makes sense once kafka itself is reachable from the host, i.e. only for the
# host-JVM dev flow.
DEV_ONLY_INFRASTRUCTURE=(
    "kafka"
    "kafka-ui"
    "redis"
    "mysql"
    "keycloak"
    "mailpit"
    "mongodb"
    "elasticsearch"
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
