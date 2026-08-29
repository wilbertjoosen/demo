#!/usr/bin/env bash
# Usage: scale-mongo-replica-set.sh <statefulset-name> <replica-set-name> <headless-service> <port> <target-replicas>
#
# Safely scales a MongoDB replica set's StatefulSet up or down — a bare `kubectl scale` is NOT
# enough on its own: a new pod from scaling up starts as a standalone mongod with no replica-set
# membership at all until someone runs rs.add() for it, and scaling down without rs.remove() first
# can strand the remaining members below majority quorum instead of cleanly shrinking the set.
#
# Topology-agnostic on purpose: works against mongo-shard0/shard0rs, mongo-shard1/shard1rs (min 2,
# max 5 — see those StatefulSets' own comments for the quorum tradeoff at 2 members), or the
# pre-sharding single mongo/rs0 replica set — same shape, just different names, so this isn't
# blocked on any particular topology having landed first.
#
# Examples:
#   ./scale-mongo-replica-set.sh mongo-shard0 shard0rs mongo-shard0-headless 27018 4
#   ./scale-mongo-replica-set.sh mongo-shard1 shard1rs mongo-shard1-headless 27018 2
set -euo pipefail

STATEFULSET=$1
REPLSET=$2
HEADLESS_SVC=$3
PORT=$4
TARGET=$5
NAMESPACE=${NAMESPACE:-infra}

CURRENT=$(kubectl get statefulset "$STATEFULSET" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')
echo "Scaling $STATEFULSET ($REPLSET) from $CURRENT to $TARGET replicas..."

member_host() {
  echo "${STATEFULSET}-$1.${HEADLESS_SVC}.${NAMESPACE}.svc.cluster.local:${PORT}"
}

# All rs.add()/rs.remove() calls go through pod -0 — safe as long as scale-downs always remove the
# highest-index member first (the loop below does exactly that), so -0 is never the one being
# removed except when scaling all the way down to a single member, which isn't a meaningful
# replica-set size anyway.
run_on_primary() {
  kubectl exec "${STATEFULSET}-0" -n "$NAMESPACE" -- mongosh --port "$PORT" --quiet --eval "$1"
}

if [ "$TARGET" -gt "$CURRENT" ]; then
  kubectl scale statefulset "$STATEFULSET" -n "$NAMESPACE" --replicas="$TARGET"
  kubectl rollout status statefulset "$STATEFULSET" -n "$NAMESPACE" --timeout=300s
  for ((i = CURRENT; i < TARGET; i++)); do
    host=$(member_host "$i")
    until run_on_primary "db.runCommand({ping:1})" >/dev/null 2>&1; do
      echo "waiting for $STATEFULSET-0 to be reachable..."; sleep 2
    done
    run_on_primary "rs.add('$host')"
    echo "Added $host to $REPLSET"
  done
elif [ "$TARGET" -lt "$CURRENT" ]; then
  for ((i = CURRENT - 1; i >= TARGET; i--)); do
    host=$(member_host "$i")
    run_on_primary "rs.remove('$host')"
    echo "Removed $host from $REPLSET"
  done
  kubectl scale statefulset "$STATEFULSET" -n "$NAMESPACE" --replicas="$TARGET"
else
  echo "Already at $TARGET replicas, nothing to do."
fi
