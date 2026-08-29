# k8s manifest layout — folder split + Kustomize

**Status:** on `main` — folder split (#74) + thin Kustomization / ArgoCD Kustomize mode (#75).
On `testing` — this file's PR adopts `main`'s `k8s/` tree and adds the `demo-qa` overlay + the CI
`images:` automation. `develop` — still to do (see [Rollout](#rollout)).

---

## Problem it solved

The environment used to be **the git branch**: `main`'s `k8s/*.yaml` was prod (`namespace: demo`),
`testing`'s the same files rewritten for QA (`namespace: demo-qa`), each a full hand-maintained
copy. Every `develop → testing → main` promotion re-diffed ~20 manifests — namespace, image tag,
DB name, `Deployment`-vs-`Rollout`, Vault wiring interleaved with real changes. Files existed on one
branch and not another, so merges hit "deleted here / modified there".

## Layout

```
k8s/
├── kustomization.yaml         # resources: [platform, demo]        ← what `path: k8s` renders
├── platform/                  # cluster-wide, ONE copy, on every branch, kept identical by merges
│   ├── kustomization.yaml     #   resources: [namespaces, infra, monitoring]
│   ├── namespaces/            #   namespace(.yaml = demo), namespace-infra, namespace-monitoring
│   ├── infra/                 #   elasticsearch, kafka, keycloak, mailpit, mongo, mysql,
│   │                          #   promtail-daemonset, redis, vault           (namespace: infra)
│   └── monitoring/            #   grafana, k6, kibana, kube-state-metrics, loki, node-exporter,
│                              #   prometheus, pyroscope, tempo               (namespace: monitoring)
├── demo/                      # the app tier (namespace: demo) — 21 manifests: every service +
│   ├── kustomization.yaml     #   gateway + eureka + config-server + frontend + ingress +
│   └── *.yaml                 #   configmap-common
└── demo-qa/                   # (testing branch only) a THIN OVERLAY of demo/ — see below.
```

`platform/` is what "common" means: namespaces + the shared stateful `infra` tier + the shared
`monitoring` tier. QA reaches all of it cross-namespace (`mysql.infra.svc.cluster.local`, …) and
never re-deploys it.

Every `kustomization.yaml` under `platform/` and `demo/` is a **plain resource list** — no
transformers — so `kubectl kustomize k8s` renders byte-identical to the raw manifests. It exists to
(a) let ArgoCD and `kubectl -k` use the Kustomize renderer and (b) give `demo-qa/` something to
`../demo`-reference.

## ArgoCD

| Application | `source` | `targetRevision` | Renders |
|---|---|---|---|
| `demo` (`application.yaml`) | `path: k8s` (Kustomize auto-detected — no `directory:` block) | `main` | `k8s/kustomization.yaml` → `platform/**` + `demo/**` |
| `demo-qa` (`application-qa.yaml`) | `path: k8s/demo-qa` *(pending step 5; currently `path: k8s`)* | `testing` | `k8s/demo-qa/**` only |

`k8s/kustomization.yaml` does **not** list `demo-qa`, so the `demo` Application never renders it —
that overlay is owned entirely by `application-qa.yaml`.

## The `demo-qa` overlay

`k8s/demo-qa/` is **~8 files**, not 24 copies. It reuses `../demo`'s manifests and changes only
what actually differs between prod and QA:

```
k8s/demo-qa/
├── kustomization.yaml             # namespace: demo-qa transformer + resources + patches
├── configmap-common.yaml          # strategic-merge patch on ConfigMap/demo-common-config (8 keys)
├── kafka.yaml                     # QA-isolated infra — own copies, namespace: demo-qa
├── redis.yaml
├── mailpit.yaml
├── namespace.yaml                 # the demo-qa Namespace object
└── patches/
    ├── mongo-db-names.yaml        # MONGO_DB: <svc>_qa  — 12 stateful services (order-service = Rollout)
    ├── audit-es-index.yaml        # AUDIT_ES_INDEX: audit-log-qa  (QA-only env var)
    └── media-upload-dir.yaml      # MEDIA_UPLOAD_DIR: media-uploads-qa
```

```yaml
# k8s/demo-qa/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: demo-qa                  # one line — rewrites every inherited manifest's namespace
resources:
  - namespace.yaml
  - ../demo                         # the whole app tier, reused
  - kafka.yaml                      # ...plus QA's own kafka/redis/mailpit
  - redis.yaml
  - mailpit.yaml
patches:
  - path: configmap-common.yaml     # patch bodies carry `namespace: demo`, matching ../demo at
  - path: patches/mongo-db-names.yaml  #   patch time (the transformer above runs afterwards)
  - path: patches/audit-es-index.yaml
  - path: patches/media-upload-dir.yaml
  - target: { kind: Ingress, name: demo-ingress }   # JSON patch: rules list has no merge key
    patch: |
      - { op: replace, path: /spec/rules/0/host, value: qa.demo.localhost }
# images: — absent until QA runs ahead of prod. CI adds entries here (see CI section); each
# overrides the tag inherited from ../demo in the demo-qa render only.
```

No `order-service` or `vault` patch: the canary `strategy` and `VAULT_TOKEN` env are already
identical in `../demo` (promoted from QA earlier), so the overlay inherits them unchanged.
`eureka-server` (Pyroscope env, 512Mi) and `order-service` (nginx access-log sidecar) also come
straight from `../demo` — QA gains those over its previous hand-maintained manifests.

| Concern | `demo/` (prod) | `demo-qa/` overlay does |
|---|---|---|
| namespace | `demo` | `namespace: demo-qa` transformer |
| image tags | promoted from QA | `images:` list (independent) |
| `MONGO_DB` | `orders`, `media`, … | `patches/mongo-db-names.yaml` → `orders_qa`, `media_qa`, … (12 services) |
| Keycloak realm / JWK / token / admin URIs | `demo` realm | `configmap-common.yaml` patch → `demo-qa` realm |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka.infra…:9092` | patch → `kafka:9092` (in-namespace) |
| `REDIS_HOST` / `MAIL_HOST` | `…infra.svc…` | patch → `redis` / `mailpit` (in-namespace) |
| `DB_NAME` | `demo` | patch → `demo_qa` |
| `CORS_ALLOWED_ORIGINS` | `demo.localhost` | patch → `qa.demo.localhost` |
| `MONGO_HOST` | replica-set DNS | patch (same replica set, kept explicit) |
| order-service canary steps | prod weights | `patches/order-service.yaml` |
| Vault | — | `patches/vault.yaml` — `VAULT_TOKEN` env on order-service (+ any other Vault reader) |
| own kafka / redis / mailpit | — | `kafka.yaml` / `redis.yaml` / `mailpit.yaml` in the overlay |
| shared infra (mysql, mongo, keycloak, es, vault) + all monitoring | deployed by `platform/` | not referenced — reached cross-namespace |

## CI

`.github/workflows/ci-cd.yml`'s two manifest-mutating jobs no longer hardcode `k8s/<svc>.yaml`:

- **`update-manifests`** (push → `testing`, or `workflow_dispatch`): resolves each rebuilt service
  to its manifest with `find k8s -maxdepth 3 -name "<svc>.yaml" -not -path 'k8s/platform/*'`.
- **`promote-to-production`** (push → `main`): for each `k8s/demo/*.yaml`, finds testing's
  counterpart (`k8s/demo-qa/<name>` then `k8s/<name>`) and copies its image tag across.

Both stage with `git add k8s/`. These still `sed` the `image:` line inside each manifest —
switching them to edit Kustomize `images:` blocks instead is a later optimization, tracked in
[below](#later-move-image-bumps-into-kustomize).

## Rollout

1. **Folder split (#74, done)** — `main` has `k8s/platform/` + `k8s/demo/`.
2. **Thin Kustomization (this PR)** — `kustomization.yaml` in `k8s/`, `k8s/platform/**`, `k8s/demo/`;
   `application.yaml` drops its `directory:` block (Kustomize auto-detected); README bootstrap is
   `kubectl apply -k k8s`.
3. **`main` → `testing`** — brings `platform/` + `demo/` + the Kustomization files onto testing.
   Then, on `testing`:
   - `git rm` the flat manifests `platform/` now supersedes (`k8s/kafka.yaml` etc. — but keep a
     copy of `kafka`/`redis`/`mailpit` for the overlay), and `k8s/keycloak.yaml` (superseded by
     `k8s/platform/infra/keycloak.yaml`).
   - author `k8s/demo-qa/` per the [overlay spec](#the-demo-qa-overlay), moving `k8s/configmap-common.yaml`
     (QA values) → `k8s/demo-qa/configmap-common.yaml` as the patch, `k8s/{kafka,redis,mailpit,namespace}.yaml`
     → `k8s/demo-qa/`, and distilling the 21 per-service diffs into `patches/`.
   - `git rm` the remaining flat `k8s/*-service.yaml` etc. (their content now comes from `../demo`
     + patches).
   - set `application-qa.yaml` → `path: k8s/demo-qa`.
   - verify: `kubectl kustomize k8s/demo-qa` renders, and diff its output against `testing`'s
     pre-change manifests — only namespace / image tag / the listed keys may differ.
4. **`main` → `develop`** — develop's apps are already `namespace: demo`, so it adopts `demo/` +
   `platform/` + the Kustomization files and `git rm`s its flat copies.

Between steps 2 and 3, CI's `promote-to-production` finds nothing under `k8s/demo-qa/` on `testing`
and no-ops — fine, no releases are expected mid-rollout.

## Verification done in this PR

- Every `kustomization.yaml` added is a plain `resources:` list — no transformers.
- `kubectl kustomize k8s` renders **115 resources**, matching the pre-Kustomize
  `kubectl apply --dry-run=client -R` count against the live cluster.
- No manifest content, `namespace:`, or `image:` line changed.

## Later: move image bumps into Kustomize

Once `demo-qa` is an overlay, both CI jobs can stop `sed`-ing `image:` lines inside manifests and
instead edit the `images:` block in `k8s/demo/kustomization.yaml` (prod) /
`k8s/demo-qa/kustomization.yaml` (QA) — or set them on the ArgoCD Application's
`spec.source.kustomize.images`. One list per environment, no per-file edits. Its own PR — it
rewrites both CI jobs, which gate prod deploys.
