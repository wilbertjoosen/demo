# k8s manifests → Kustomize base + overlays

**Status:** proposal / not yet implemented.
**Owner:** wilbertjoosen.
**Goal:** stop hand-maintaining a full copy of every manifest per branch.

---

## Problem

Environment differentiation is currently **the git branch**:

| Branch | `k8s/*.yaml` targets | ArgoCD Application | Namespace |
|---|---|---|---|
| `main` | production | `k8s-argocd/application.yaml` (`targetRevision: main`, `path: k8s`) | `demo` |
| `testing` | QA | `k8s-argocd/application-qa.yaml` (`targetRevision: testing`, `path: k8s`) | `demo-qa` |
| `develop` | local k3d dev | — (applied by hand / `k8s-local.sh`) | `demo` |

Every manifest is copied in full on each branch with the environment's `namespace:`,
image tags, DB names, and (on `testing`) Argo Rollouts / Vault wiring baked directly in.
Consequences:

- **Every `develop → testing` promotion re-diffs ~20 manifests.** The sync that produced
  this document hit 15 conflicting files; ~6 needed manual infra judgment.
- Files exist on one branch and not another (`k8s/keycloak.yaml`, `k8s/grafana.yaml`,
  `k8s/mongo.yaml`, … are on `main`/`develop` but not `testing`, which shares that infra
  with prod) — so a merge sees "deleted here / modified there" conflicts.
- The intentional per-environment differences and the accidental drift are impossible to
  tell apart in a diff.

Divergence at time of writing:

| Pair | ahead / behind | files differ |
|---|---|---|
| develop ↔ testing | testing +155, develop +29 | 243 |
| testing ↔ main | main +142, testing +59 | 174 |
| develop ↔ main | main +242, develop +33 | 226 |

---

## Target layout

```
k8s/
├── base/
│   ├── apps/                      # every app workload, namespace-less, no image tag
│   │   ├── audit-service.yaml
│   │   ├── chat-service.yaml
│   │   ├── common-service.yaml
│   │   ├── config-server.yaml
│   │   ├── delivery-service.yaml
│   │   ├── eureka-server.yaml
│   │   ├── frontend.yaml
│   │   ├── gateway-service.yaml
│   │   ├── inventory-service.yaml
│   │   ├── notification-service.yaml
│   │   ├── order-service.yaml     # plain Deployment in base; demo-qa overlay patches it to a Rollout
│   │   ├── payment-service.yaml
│   │   ├── product-comment-service.yaml
│   │   ├── product-media-service.yaml
│   │   ├── product-review-service.yaml
│   │   ├── product-service.yaml
│   │   ├── reporting-service.yaml
│   │   ├── shipping-service.yaml
│   │   ├── user-service.yaml
│   │   ├── ingress.yaml
│   │   └── kustomization.yaml
│   ├── infra/                     # shared stateful — mysql, mongo, kafka, redis, keycloak,
│   │   │                          #   elasticsearch, mailpit, vault
│   │   └── kustomization.yaml
│   └── monitoring/                # grafana, prometheus, loki, tempo, promtail, kibana
│       └── kustomization.yaml     #   (pyroscope server lives here too if/when self-hosted)
└── overlays/
    ├── demo/                      # PRODUCTION
    │   ├── kustomization.yaml     #   namespace: demo
    │   │                          #   resources: ../../base/apps, ../../base/infra, ../../base/monitoring
    │   ├── configmap-common.yaml  #   full demo values (replace, not patch)
    │   ├── images.yaml            #   `images:` tag pins
    │   └── patches/               #   prod-only tweaks, if any
    └── demo-qa/                   # QA
        ├── kustomization.yaml     #   namespace: demo-qa
        │                          #   resources: ../../base/apps
        │                          #            + ../../base/infra  (kafka, redis, mailpit ONLY — see below)
        │                          #   NO base/monitoring (shares prod's)
        ├── configmap-common.yaml  #   demo-qa values (OTLP_ENDPOINT, *_qa DB names, demo-qa realm, VAULT_*)
        ├── images.yaml            #   independent tag pins — this is how QA runs ahead of prod
        └── patches/
            ├── order-service-rollout.yaml   # Deployment → argoproj.io Rollout + canary steps
            ├── db-names.yaml                # MONGO_DB: orders_qa / media_qa / reviews_qa / …
            ├── media-upload-dir.yaml        # MEDIA_UPLOAD_DIR: media-uploads-qa
            └── vault.yaml                   # VAULT_TOKEN env + spring.cloud.vault.* where needed
```

### What is "common"

`base/infra` + `base/monitoring`. The `demo` overlay takes all of both. The `demo-qa`
overlay takes **only** `kafka`, `redis`, `mailpit` from `infra` (the genuinely
QA-isolated pieces) and **none** of `monitoring` — QA reaches the shared
`monitoring`-namespace Grafana / Prometheus / Loki / Tempo the same way prod does.

`namespace-infra.yaml` / `namespace-monitoring.yaml` / `namespace.yaml` stay as-is
(or fold into `CreateNamespace=true`, already set on both Applications).

`secret-order-db.yaml` and every imperatively-created Secret (`ghcr-pull-secret`,
`mysql-credentials`, `grafana-admin`, `pyroscope-agent`, `aws-credentials`,
`vault-token`) stay **out** of Kustomize, unchanged — see the README's k8s section.

---

## The real per-overlay diff

Distilled from the develop↔testing manifest conflicts:

| Concern | `demo` overlay | `demo-qa` overlay |
|---|---|---|
| namespace | `demo` | `demo-qa` |
| image tags | `images.yaml` | `images.yaml` (independent — lets QA run newer builds) |
| `imagePullPolicy` | `IfNotPresent` | `Always` |
| `MONGO_DB` per service | `orders`, `media`, `reviews`, … | `orders_qa`, `media_qa`, … (patch) |
| Keycloak realm | `demo` | `demo-qa` |
| `CORS_ALLOWED_ORIGINS` | `demo.localhost` | `qa.demo.localhost` |
| `MEDIA_UPLOAD_DIR` | `media-uploads` | `media-uploads-qa` (patch) |
| order-service kind | `Deployment` | `Rollout` + canary steps (patch) |
| Vault | — | `VAULT_TOKEN` + `spring.cloud.vault.*` (patch) |
| infra deployed | all of `base/infra` | `kafka`, `redis`, `mailpit` only |
| monitoring deployed | all of `base/monitoring` | none (shared) |
| `TRACING_EXPORT_OTLP_ENABLED` | `"true"` | `"true"` |
| OTLP endpoint var | `OTLP_ENDPOINT` | `OTLP_ENDPOINT` (unify the name while doing this) |

Everything not in this table is identical and belongs in `base/`.

---

## ArgoCD changes

```yaml
# k8s-argocd/application.yaml      (demo)     spec.source.path: k8s          →  k8s/overlays/demo
# k8s-argocd/application-qa.yaml   (demo-qa)  spec.source.path: k8s          →  k8s/overlays/demo-qa
```

`targetRevision` unchanged for now (`main` / `testing`). See Phase 2.

---

## Migration steps

1. **Branch off `main`** — production is the canonical manifest shape.
   `mkdir -p k8s/base/{apps,infra,monitoring} k8s/overlays/{demo,demo-qa}`.
2. Move each `k8s/*.yaml` into the right `base/…` folder. **Strip `namespace:` and image
   tags**; reduce env to the common set. Write the three `base/**/kustomization.yaml`
   `resources:` lists.
3. **`overlays/demo/`**: `kustomization.yaml` (`namespace: demo`, resources = all three base
   groups), full `configmap-common.yaml`, `images.yaml` from `main`'s current tags.
   **Verify:** `kubectl kustomize k8s/overlays/demo` must render byte-identical (modulo key
   ordering) to `main`'s current flat manifests. `diff` it.
4. **`overlays/demo-qa/`**: `kustomization.yaml` (`namespace: demo-qa`, resources = `base/apps`
   + partial `base/infra`), `configmap-common.yaml` with `testing`'s current values, the
   patches from the table. **Verify** the same way against `testing`'s current manifests.
5. Repoint both ArgoCD Application `path`s. Sync `demo` first (render-diff is a no-op = safe),
   then `demo-qa`.
6. Delete the flat `k8s/*.yaml`. Update the README's k8s section and `k8s-local.sh`
   (`kubectl apply -k k8s/overlays/demo` instead of `-f k8s/`).

Land the whole thing on `main`, then merge to `testing` and `develop` so `base/` is identical
everywhere.

---

## Phase 2 (optional, the real prize)

Once both overlays live on `main`, point the **`demo-qa` Application's `targetRevision` at
`main`** too. Then:

- `testing` and `develop` become **code-only branches** — no k8s manifests to merge, ever.
- QA runs ahead of prod purely via `overlays/demo-qa/images.yaml` pinning newer per-commit
  image tags (CI already builds one image per commit — see README CI/CD section).
- A promotion is: bump `overlays/demo/images.yaml` to the tag QA has been running.

This is a process change (QA no longer defined by "whatever's on the `testing` branch"),
so it's deliberately a separate decision from the mechanical restructure above.

---

## Risks / notes

- **`kubectl kustomize` render-diff in steps 3–4 is the safety net.** Do not skip it. A
  clean diff against the current live manifests is the whole proof the migration is inert.
- **ArgoCD `prune: true`** on both Applications: the cutover sync will show a
  delete+create for every resource (same content, moved file). Set `prune: false` for the
  one cutover sync, re-enable immediately after.
- `bucket4j` / rate limiting, `EndUserIdTracingFilter`, Pyroscope env, replica-set Mongo:
  these are testing-first features not yet on `develop`. They are code + `base/` concerns,
  independent of this restructure — but doing the restructure first means the
  `testing → develop` back-merge that brings them over is code-only.
- Estimated size: ~40 files moved, ~10 new `kustomization.yaml` / patch files, 2 ArgoCD
  edits, README + `k8s-local.sh` updates. One focused PR.
