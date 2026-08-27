# k8s manifest layout — folder split by concern

**Status:** implemented on `main` (this file's PR). Propagation to `testing` / `develop` is the
remaining step — see [Rollout](#rollout).

---

## Problem it solves

Before this, the environment was **the git branch**: `main`'s `k8s/*.yaml` was prod
(`namespace: demo`), `testing`'s the same files rewritten for QA (`namespace: demo-qa`), each a
full hand-maintained copy. Every `develop → testing → main` promotion re-diffed ~20 manifests —
namespace, image tag, DB name, `Deployment`-vs-`Rollout`, Vault wiring all interleaved with real
changes. Files existed on one branch and not another (`keycloak.yaml`, `grafana.yaml`, …), so
merges hit "deleted here / modified there".

## Layout

```
k8s/
├── platform/                 # cluster-wide, one copy, owned by the `demo` ArgoCD Application
│   ├── namespaces/           #   namespace.yaml, namespace-infra.yaml, namespace-monitoring.yaml
│   ├── infra/                #   elasticsearch, kafka, keycloak, mailpit, mongo, mysql,
│   │                         #   promtail-daemonset, redis, vault           (namespace: infra)
│   └── monitoring/           #   grafana, k6, kibana, kube-state-metrics, loki, node-exporter,
│                             #   prometheus, pyroscope, tempo               (namespace: monitoring)
├── demo/                      # the app tier — every service + gateway + eureka + config-server
│                             #   + frontend + ingress + configmap-common    (namespace: demo)
└── demo-qa/                   # (testing branch only) the SAME app manifests, namespace: demo-qa,
                              #   QA image tags, order-service as a Rollout, Vault env, *_qa DB
                              #   names, plus this namespace's own kafka/redis/mailpit
```

`platform/` is what "common" means here: namespaces, the shared stateful `infra` tier, and the
shared `monitoring` tier. The QA environment reaches all of it cross-namespace
(`mysql.infra.svc.cluster.local`, etc.) and never re-deploys it.

This is a **folder split, not deduplication** — `demo/` and `demo-qa/` are full copies. That's
deliberate: side by side in one branch they are a reviewable `diff`, not a cross-branch merge.
Collapsing the two into a Kustomize `base/` + `overlays/` is a sensible *later* refinement (see
[below](#optional-later-kustomize)), and the folder split is a clean stepping stone to it — nothing
moves again.

## ArgoCD

| Application | `source.path` | `targetRevision` | Owns |
|---|---|---|---|
| `demo` (`k8s-argocd/application.yaml`) | `k8s` + `directory: { recurse: true, exclude: '{demo-qa/**,RESTRUCTURE.md}' }` | `main` | `k8s/platform/**` + `k8s/demo/**` |
| `demo-qa` (`k8s-argocd/application-qa.yaml`) | `k8s/demo-qa` + `recurse: true` *(pending — see Rollout)* | `testing` | `k8s/demo-qa/**` only |

`recurse: true` is new and required — a bare `path: k8s` reads only top-level files, and there are
none now. The `demo` Application excludes `demo-qa/**` so it never fights the `demo-qa` Application
over QA resources; on `main` that tree doesn't exist, so the exclude is future-proofing.

## CI

`.github/workflows/ci-cd.yml`'s two manifest-mutating jobs no longer hardcode `k8s/<svc>.yaml`:

- **`update-manifests`** (push → `testing`, or `workflow_dispatch`): resolves each rebuilt service
  to its manifest with `find k8s -maxdepth 3 -name "<svc>.yaml" -not -path 'k8s/platform/*'` — works
  whether the manifest is at `k8s/<svc>.yaml` (pre-split), `k8s/demo/<svc>.yaml` (main), or
  `k8s/demo-qa/<svc>.yaml` (testing, post-rollout).
- **`promote-to-production`** (push → `main`): for each `k8s/demo/*.yaml`, finds testing's
  counterpart trying `k8s/demo-qa/<name>` then `k8s/<name>`, and copies its image tag across.

Both stage with `git add k8s/` instead of `git add k8s/*.yaml`.

## Rollout

1. **This PR** — `main` gets `k8s/platform/` + `k8s/demo/`; `demo` Application repointed; CI made
   layout-tolerant. `application-qa.yaml` left as `path: k8s` (still valid against testing's
   still-flat layout).
2. **`main` → `testing`** — brings `k8s/platform/` + `k8s/demo/` onto testing. Then, on testing:
   move testing's flat app manifests into `k8s/demo-qa/`, delete the now-duplicated flat copies and
   the ones `platform/` supersedes, and set `application-qa.yaml` to `path: k8s/demo-qa`,
   `recurse: true`.
3. **`main` → `develop`** — develop's apps are already `namespace: demo`, so it just adopts
   `k8s/demo/` + `k8s/platform/` from the merge and drops its flat copies.

Between steps 1 and 2, CI's `promote-to-production` finds nothing new under `k8s/demo-qa/` on
testing and no-ops — fine, no releases are expected mid-rollout.

## Verification done in this PR

- `git mv` only — `git show --stat` confirms every manifest is a pure rename, content unchanged.
- `kubectl apply --dry-run=client -R -f k8s/platform/ -R -f k8s/demo/` parses clean.
- No `namespace:` value changed; no `image:` line changed.

## Optional later: Kustomize

Once the folder split is on all branches, `demo/` and `demo-qa/` can collapse into:

```
k8s/base/{apps,infra,monitoring}/     # namespace-less, no image tag
k8s/overlays/demo/                    # namespace: demo + image tags
k8s/overlays/demo-qa/                 # namespace: demo-qa + patches (rollout, db-names, vault)
```

with ArgoCD `path`s pointing at the overlays. That removes the `demo/` ↔ `demo-qa/` duplication
entirely. It needs a `kubectl kustomize` render-diff proving the output is byte-identical to the
current manifests, and touches the CI globs again — worth doing as its own PR, not now.
