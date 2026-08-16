# Shared by start-local.sh, stop-local.sh, and k8s-local.sh — single source of truth for which
# docker-compose services count as "infra" (and, of those, which are only needed by the host-JVM
# dev flow / Option B's full-docker-compose stack), so none of the three scripts can drift out of
# sync with each other.
#
# This branch's docker-compose.yml differs from main/testing in three ways this list has to match:
# no vault/vault-init (order-service here still reads DB_USERNAME/DB_PASSWORD as plain env vars,
# no Vault integration to wire up), no tempo (not defined at all here), and a single "mongodb"
# container rather than a 3-node mongo1/2/3 + mongo-rs-init replica set.

INFRASTRUCTURE=(
    "mysql"
    "mongodb"
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
    "kibana"
)

# Of the above, the ones k8s pods no longer depend on — Grafana and Kibana are the only pieces
# still reached by pods via host.k3d.internal now (see k8s/configmap-common.yaml); everything else
# moved in-cluster: Kafka, Redis, MySQL, Keycloak, Mailpit (k8s/kafka.yaml, k8s/redis.yaml,
# k8s/mysql.yaml, k8s/keycloak.yaml, k8s/mailpit.yaml), and now MongoDB and Elasticsearch too
# (k8s/mongo.yaml, k8s/elasticsearch.yaml). kafka-ui only makes sense once kafka itself is
# reachable from the host; this compose file's own "loki" is the host-JVM/compose-flow instance
# specifically — k8s has its own separate in-cluster one (k8s/loki.yaml); "promtail" only ships
# logs to this file's own "loki" (k8s pods get their own logs shipped by the in-cluster
# k8s/promtail-daemonset.yaml instead, a resource applied directly rather than run through
# docker-compose at all). "prometheus" is dev-only for a different reason: this branch has no
# in-cluster Prometheus at all (pod IPs on the k3d overlay network were never reachable from
# docker-compose's Prometheus anyway, so it never actually scraped k8s pods here).
DEV_ONLY_INFRASTRUCTURE=(
    "kafka"
    "kafka-ui"
    "redis"
    "prometheus"
    "mysql"
    "keycloak"
    "mailpit"
    "mongodb"
    "elasticsearch"
    "loki"
    "promtail"
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
