# Shared by start-local.sh, stop-local.sh, and k8s-local.sh — single source of truth for which
# docker-compose services count as "infra" (and, of those, which are only needed by the host-JVM
# dev flow / Option B's full-docker-compose stack), so none of the three scripts can drift out of
# sync with each other.

INFRASTRUCTURE=(
    "mysql"
    "mongo"
    "kafka"
    "kafka-ui"
    "keycloak"
    "mailpit"
    "elasticsearch"
    "redis"
    "vault"
    "vault-init"
)

# Of the above, the ones k8s pods no longer depend on — everything except Mailpit's per-namespace
# copies (see k8s/demo/configmap-common.yaml's comment) moved in-cluster: Kafka, Redis, MySQL,
# Keycloak, Vault (k8s/platform/infra/kafka.yaml, redis.yaml, mysql.yaml, keycloak.yaml,
# vault.yaml), MongoDB (now a sharded cluster — k8s/platform/infra/mongo-configsvr.yaml,
# mongo-shard0.yaml, mongo-shard1.yaml, mongo-mongos.yaml, mongo-cluster-init-job.yaml) and
# Elasticsearch (k8s/platform/infra/elasticsearch.yaml). kafka-ui only makes sense once kafka
# itself is reachable from the host.
# Kibana/Grafana/Prometheus/Loki/Promtail/Tempo used to be here too — now fully in-cluster
# (k8s/platform/monitoring/kibana.yaml, grafana.yaml, prometheus.yaml, loki.yaml, tempo.yaml,
# k8s/platform/infra/promtail-daemonset.yaml) with no docker-compose service left to toggle at all.
DEV_ONLY_INFRASTRUCTURE=(
    "kafka"
    "kafka-ui"
    "redis"
    "mysql"
    "keycloak"
    "vault"
    "vault-init"
    "mongo"
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
