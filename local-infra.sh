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
    "kibana"
)

# Of the above, the ones k8s pods no longer depend on — Kibana is the only piece still reached by
# pods via host.k3d.internal now (see k8s/configmap-common.yaml); everything else moved in-cluster:
# Kafka, Redis, MySQL, Keycloak, Mailpit, Vault (k8s/kafka.yaml, k8s/redis.yaml, and — cross-namespace
# from main's demo namespace — MySQL/Keycloak/Vault, plus this namespace's own k8s/mailpit.yaml), and
# now MongoDB and Elasticsearch too (also cross-namespace from main's demo namespace). kafka-ui only
# makes sense once kafka itself is reachable from the host. Grafana/Prometheus/Loki/Promtail/Tempo
# used to be here too — now fully in-cluster, a single shared instance for both prod and QA owned by
# main's own ArgoCD Application (k8s/grafana.yaml, k8s/prometheus.yaml, k8s/loki.yaml,
# k8s/promtail-daemonset.yaml, k8s/tempo.yaml, all on the main branch) — this branch never gets its
# own copies (see main's k8s/prometheus.yaml's comment), so there's no docker-compose service left to
# toggle for any of them.
DEV_ONLY_INFRASTRUCTURE=(
    "kafka"
    "kafka-ui"
    "redis"
    "mysql"
    "keycloak"
    "mailpit"
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
