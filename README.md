# Demo — Microservices Architecture Playground

A from-scratch rebuild of a single Spring Boot monolith into a full microservices system, built as a
hands-on reference for *why* you'd reach for a given distributed-systems pattern, not just *how* to
wire it up. Every pattern below exists because a concrete problem in this domain needed it — see
[Patterns demonstrated](#patterns-demonstrated) for the reasoning behind each one.

**Stack:** Spring Boot 4 / Java 26 backend (19 modules), Vue 3 + TypeScript + Element Plus frontend,
MySQL + MongoDB + Kafka + Redis + Elasticsearch, Keycloak (OAuth2/OIDC), Eureka + Spring Cloud Gateway,
Prometheus + Grafana + Loki + Kibana, Docker Compose for local infra, k3d + ArgoCD for a local
Kubernetes/GitOps loop, GitHub Actions → GHCR for CI/CD across a production and a QA environment.

## Architecture

A choreographed Kafka saga drives an order through payment, shipping, and delivery. Every service
that isn't infrastructure is independently deployable — its own jar, its own Docker image, its own k8s
Deployment.

| Service                   | Port       | Store | Responsibility                                                                                                                                                                                |
|---------------------------|------------|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `gateway-service`         | 8080       | — | Spring Cloud Gateway; routes `/api/**` to backend services by path, CORS                                                                                                                      |
| `eureka-server`           | 8761       | — | Service discovery — every backend registers here                                                                                                                                              |
| `config-server`           | 8888       | — | Centralized config (currently minimal; most config is per-service `application.yaml`)                                                                                                         |
| `user-service`            | 8082       | MongoDB | User profiles; identity fields (username/email/name) are read live from Keycloak, not duplicated locally                                                                                      |
| `product-service`         | 8083       | MongoDB | Product catalog; reserve/release stock; Spring Batch CSV bulk import                                                                                                                          |
| `order-service`           | 8084       | MySQL (write) + MongoDB (read) | **Saga initiator** + **CQRS**: `POST /api/orders` hits MySQL directly (ACID), `GET /api/orders*` reads from a Mongo projection kept in sync by the same Kafka listeners the saga needs anyway |
| `payment-service`         | 8086       | MongoDB | Saga participant: charges, refunds on downstream failure                                                                                                                                      |
| `shipping-service`        | 8088       | MongoDB | Saga participant: carrier selection (UPS/DHL), tracking                                                                                                                                       |
| `delivery-service`        | 8087       | MongoDB | Saga participant: last-mile delivery simulation                                                                                                                                               |
| `inventory-service`       | 8089       | MongoDB | Per-warehouse stock, called synchronously by `order-service` (the one sync inter-service call in the system, see [Circuit breaker](#circuit-breaker))                                         |
| `notification-service`    | 8085       | — | Kafka consumer on every topic → WebSocket broadcast (`/ws/notifications`) + email via Mailpit                                                                                                 |
| `audit-service`           | 8090       | Elasticsearch | Consumes the audit trail every service emits; reconstructs field-level diffs and full record history                                                                                          |
| `chat-service`            | 8094       | MongoDB | Public per-product chat rooms **and** private user-to-user direct messages (JWT-authenticated WebSocket, delivery/read receipts, typing indicators)                                           |
| `reporting-service`       | 8095       | MongoDB | Reporting (JWT-authenticated WebSocket, delivery/read receipts, typing indicators)                                                                                                            |
| `product-comment-service` | 8091       | MongoDB | Product comments, ownership-enforced editing                                                                                                                                                  |
| `product-media-service`   | 8092       | MongoDB + local disk | Product photos/videos/documents, file upload                                                                                                                                                  |
| `product-review-service`  | 8093       | MongoDB | Product ratings/reviews                                                                                                                                                                       |
| `common-security`         | —          | — | Shared JWT resource-server config, reused by every service                                                                                                                                    |
| `common-audit`            | —          | — | Shared aspect that captures every REST call's request/response for the audit trail                                                                                                            |
| `common-model`            | —          | — | Shared DTOs (e.g. `Address`)                                                                                                                                                                  |
| `frontend`                | 5173 (dev) | — | Vue 3 SPA — the only thing that talks to the gateway; runs in the browser regardless of how the backend is deployed                                                                           |

Kafka topics: `user-events`, `product-events`, `order-events`, `payment-events`, `shipping-events`,
`delivery-events`. Plain JSON, no schema registry — deliberate simplicity for a demo.

## Patterns demonstrated

### Distributed systems

- **Saga (choreography)** — `order → payment → shipping → delivery`, each step reacting to the
  previous step's Kafka event. No orchestrator. Compensation on failure always flows back through
  `payment-service` (refund) and `product-service` (stock release), regardless of which step failed.
- **CQRS** — scoped to `order-service` only: MySQL write model, Mongo read model, synced by the saga's
  own Kafka listeners rather than a separate change-data-capture pipeline.
- **Circuit breaker** — `order-service → inventory-service` is the *only* synchronous call in the
  system; everything else is async via Kafka, which gets resilience largely for free through consumer
  replay. That's exactly why the one sync call is wrapped in Resilience4j.
- **Outbox / store-and-forward** — Kafka publish failures are persisted and retried rather than
  silently dropped or blocking the request.
- **Sidecar** — demonstrated on `order-service`'s k8s pod (`k8s/order-service.yaml`): an `nginx`
  access-log sidecar in the same pod.
- **GitOps** — ArgoCD watches this repo's `k8s/` path; `kubectl apply` is not how deploys happen once
  the cluster is bootstrapped (see [Kubernetes / GitOps](#kubernetes--gitops)).
- **Audit trail without per-service instrumentation** — `common-audit`'s `RestCallAuditAspect` captures
  every REST request/response generically; `audit-service` reconstructs field-level diffs by comparing
  consecutive snapshots for the same record ID, rather than requiring Envers-style per-entity setup.

### Object-oriented & domain design

These are graded honestly below — only claiming what's actually in the code, including where a pattern
is a partial or pragmatic fit rather than textbook.

**Design patterns**

- **Repository** — every persistence-facing interface (16 `*Repository` interfaces across the reactor)
  is a Spring Data abstraction over MongoDB or JPA; service code never touches a driver or
  `EntityManager` directly.
- **Adapter** — one `*ModelAssembler` per service (`UserModelAssembler`, `OrderViewModelAssembler`, 11
  in total) converts a persistence/domain object into its HATEOAS-linked API representation, keeping
  wire format decoupled from storage format.
- **Template Method** — `ChatWebSocketHandler`, `DirectMessageWebSocketHandler`, and
  `NotificationWebSocketHandler` all extend Spring's `TextWebSocketHandler`, overriding only the
  lifecycle hooks each one needs (`afterConnectionEstablished`, `handleTextMessage`); the
  connection-bookkeeping skeleton stays in the base class.
- **Interceptor** — `ChatHandshakeInterceptor` / `DirectMessageHandshakeInterceptor` hook into the
  WebSocket handshake to extract and (for direct messages) cryptographically verify identity before
  the handler ever sees a session — the same shape as a servlet filter chain.
- **Factory Method** — `DomainEvent.of(eventType, orderId, payload)` is the only way any saga event
  gets constructed, so the timestamp can't be forgotten at a call site.
- **Aspect-Oriented Programming** — `common-audit`'s `RestCallAuditAspect` captures every REST call's
  request/response with one `@Around` advice, instead of every controller method calling an audit
  helper by hand.

**SOLID**

- **SRP** — each service owns exactly one bounded context (see DDD below); within a service,
  controller/service/repository/assembler are separate classes, each with one reason to change.
- **DIP** — controllers depend on a `*Service` interface (`ConversationService`, `ChatService`,
  `UserService`, ...), never the `*Impl` directly, so an alternate implementation could swap in
  without touching any caller.
- **ISP** — those service interfaces stay narrow and use-case-shaped rather than one god interface per
  service — `ConversationService` is 7 methods, all conversation-lifecycle operations, nothing else.
- **OCP is the weakest fit here, honestly** — extension mostly happens by adding an enum constant plus
  an `if`/`switch` (`PaymentMethod`, `MediaType`) rather than a polymorphic strategy class per variant.
  Worth knowing as a limitation of this codebase, not a pattern to go looking for.

**Domain-Driven Design**

- **Bounded contexts** — each microservice *is* a bounded context: `order-service` only knows a
  `Shipment` as an event payload field, `chat-service` only knows a `Product` as an opaque ID. The
  service boundary and the Maven module boundary are the same boundary.
- **Domain events** — every Kafka message is a `DomainEvent` (`common-security/.../events/DomainEvent.java`),
  published after a state change and consumed to drive the next saga step — closer to a true DDD domain
  event than to a generic message-bus payload.
- **Value objects** — `Address` (`common-model`) has no identity of its own and is embedded wherever
  needed (a user's default address, an order's shipping snapshot); it's shared *because* both services
  mean the literal same concept, not out of laziness. Caveat: it's Lombok-`@Setter` mutable, not a
  textbook immutable VO — a pragmatic shortcut, not a purist one.
- **Aggregates** — `Order` (order-service, MySQL) and `Conversation` (chat-service, MongoDB) are each
  the aggregate root and consistency boundary for their own writes; `OrderView` and
  `ConversationSummary` are deliberately separate read models, not the aggregate leaking out through
  the API.
- **Ubiquitous language** — service names, event types (`ORDER_CREATED`, `PAYMENT_COMPLETED`,
  `SHIPPED`), and REST paths all use the vocabulary a product owner would use — no translation layer
  between "the business" and "the code."

## Quick start

### Option A — local JVM + npm (fastest inner loop)

Bring up infra only, then run services directly:

```bash
docker compose up -d mysql mongodb kafka keycloak mailpit elasticsearch redis
./mvnw -pl user-service,product-service,order-service,payment-service,shipping-service,delivery-service,inventory-service,notification-service,gateway-service,eureka-server,config-server,audit-service,chat-service,product-comment-service,product-media-service,product-review-service -am install -DskipTests
# then in separate terminals, per service:
./mvnw -pl <service> spring-boot:run
cd frontend && npm install && npm run dev
```

Frontend: http://localhost:5173

`./start-local.sh` / `./stop-local.sh` automate the above (build, start every service + infra
container in the background, tear down again). Both take `--infra` and/or `--services` to start or
stop just one half — e.g. `./start-local.sh --infra` to bring up only the Docker containers, or
`./stop-local.sh --services` to kill just the local `mvnw`/`npm` processes and leave infra running.
With no flags, both do everything (unchanged default behavior). `stop-local.sh` also takes `-y` to
skip its "stop infra too?" prompt. Every backend module also now has `spring-boot-devtools` on the
classpath, so a rebuild while a service from these scripts (or an IDE run) is running triggers a
fast in-process restart instead of a full relaunch.

### Option B — full Docker Compose stack

```bash
docker compose up -d --build
```

Everything (infra + all 19 backend services + frontend) runs in containers on one network.

### Option C — Kubernetes (k3d) + GitOps

```bash
k3d cluster create demo --servers 1 --agents 2 -p "18090:80@loadbalancer" -p "18453:443@loadbalancer" -p "8081:8081@loadbalancer" --api-port 6550
kubectl apply -f k8s/
```

App: http://demo.localhost:18090 — Kafka, Redis, MySQL, Keycloak, Mailpit, and Vault all run
in-cluster now: Kafka/Redis/Mailpit as their own instance per namespace (`k8s/kafka.yaml`,
`k8s/redis.yaml`, `k8s/mailpit.yaml`) — see [QA / testing environment](#qa--testing-environment) —
while MySQL/Keycloak/Vault are shared cross-namespace from the `demo` (prod) namespace, matching
how they were already shared on the host. Only MongoDB and Elasticsearch still run via Docker
Compose on the host, reached through `host.k3d.internal`. The `-p "8081:8081@loadbalancer"`
mapping is load-bearing, not optional: every service's `KEYCLOAK_ISSUER_URI` (and the frontend's)
is hardcoded to `http://localhost:8081`, so in-cluster Keycloak has to keep answering there too —
see `k8s/keycloak.yaml`'s comment (main branch) for the full reasoning. `kubectl apply -f k8s/`
here is a one-time bootstrap — from then on, ArgoCD watches the repo and CI/CD (see [CI/CD &
versioning](#cicd--versioning)) handles building, pushing to GHCR, and bumping the manifests
ArgoCD syncs; there's no `k3d image import` step in the normal flow, since images live in a real
registry now. (`k3d image import` is still the right tool if you want to test a *local, unpushed*
code change without going through CI.)

Day-to-day cluster start/stop (the cluster plus the host infra it depends on) is wrapped by
`./k8s-local.sh {start|stop|restart|status}` — e.g. `./k8s-local.sh stop` runs `k3d cluster stop demo`
then `docker compose stop`; `./k8s-local.sh start` runs `docker compose up -d` then
`k3d cluster start demo` and tails `kubectl get pods -A -w` (pass `--no-watch` to skip the tail).
`start`/`stop` only touch the infra pods actually need now (Mongo, Elasticsearch,
Grafana/Loki/Tempo) — not docker-compose's own app containers (Option B, irrelevant when using
k8s) and not the pieces that are dev-only now that Kafka, Redis, MySQL, Keycloak, Mailpit, and
Vault all run in-cluster (docker-compose's own Prometheus is dev-only too — k8s has its own
separate one, `k8s/prometheus.yaml`). Pass `--with-dev` to also start/stop those, e.g. if
`start-local.sh`'s host-JVM services are running against the same docker-compose stack at the same
time.

## Default credentials

| User | Password | Realm role |
|---|---|---|
| `demo` | `demo` | `user` |
| `admin` | `admin` | `user`, `admin` |
| `manager` | `manager` | `finance`, `product_manager`, `shipping_manager`, `inventory_manager` |

Realm: `demo` (prod) / `demo-qa` (QA) — same users/passwords in both. Client: `demo-spa` (public, PKCE).

## Useful URLs

| Tool | URL                                                           | Credentials |
|---|---------------------------------------------------------------|---|
| Frontend (dev) | http://localhost:5173                                         | — |
| Frontend (k8s, prod) | http://demo.localhost:18090                                   | — |
| Frontend (k8s, QA) | http://qa.demo.localhost:18090                                | — (realm `demo-qa`, same users as above) |
| Keycloak admin | http://localhost:8081                                         | `admin` / `admin` (realm: **`demo`** for prod, **`demo-qa`** for QA — not `master`, see note below) |
| Swagger UI (aggregated) | http://localhost:8080/swagger-ui.html                         | — |
| Grafana | http://localhost:3000                                         | `admin` / `admin` — datasources: **Prometheus** (host-JVM/compose flow), **Prometheus (k8s)** (both k8s namespaces), **Loki** |
| Prometheus (host-JVM/compose) | http://localhost:9090                                         | — |
| Prometheus (k8s, prod + QA) | http://prometheus.demo.localhost:18090                        | — |
| Kibana | http://localhost:5601                                         | — |
| Kafka UI | http://localhost:8099                                         | prod Kafka only — QA's Kafka has no UI wired up |
| Mailpit (SMTP inbox, prod) | http://localhost:8025                                         | — |
| Mailpit (SMTP inbox, QA) | http://localhost:8026                                         | — |
| Rancher (Docker container) | https://localhost:9443                                        | bootstrap password `rancherdemo123` (set via `CATTLE_BOOTSTRAP_PASSWORD` in `docker-compose.yml`) |
| ArgoCD | http://argocd.localhost:18090 (via `k8s-argocd/ingress.yaml`) | `admin` / `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` — manages two Applications, `demo` (prod) and `demo-qa` |

> **Keycloak gotcha:** the admin console defaults to the `master` realm, which only ever contains the
> bootstrap admin. Application users (`demo`, `admin`, `manager`, everyone created through the app)
> live in the **`demo`** realm (prod) or **`demo-qa`** realm (QA) — switch realms via the dropdown in
> the top-left before looking for them. Same one Keycloak server hosts both.

### Infrastructure connection ports

For connecting a DB client, `redis-cli`, `kcat`, etc. directly rather than through a UI. All of the
rows below marked **dev-only** are host containers that k8s no longer depends on — QA and prod k8s
pods use their own in-cluster instances instead (`k8s/kafka.yaml`, `k8s/redis.yaml`,
`k8s/mysql.yaml`, `k8s/keycloak.yaml`, `k8s/mailpit.yaml`, `k8s/vault.yaml`); the host containers
still exist purely for host-JVM/`start-local.sh` debugging.

| Service | Host:Port | Notes |
|---|---|---|
| MySQL (host / dev) | `localhost:3306` | `demo-mysql` — `order-service`'s write model, db `demo` (prod) / `demo_qa` (QA), user/pass `demo`/`demo`; **dev-only**, in-cluster instance is shared cross-namespace from the `demo` namespace (`mysql.demo.svc.cluster.local`) |
| MongoDB | `localhost:27017` | every other service's store, one logical DB per service, `_qa`-suffixed for QA — still host-based for both dev and k8s |
| Kafka (host clients / dev, prod) | `localhost:9092` | `PLAINTEXT` listener for local JVM services / host tools — **dev-only**; QA and prod k8s pods use their own in-cluster Kafka (`kafka:9092` inside the cluster, `k8s/kafka.yaml`), not this container |
| Kafka (host clients / dev, QA) | `localhost:9192` | separate broker — shared topics would mean QA test traffic triggering prod's saga; host-JVM debugging only, same in-cluster-Kafka caveat as above |
| Redis (host / dev, prod) | `localhost:6379` | `demo-redis` — Resilience4j response caching for the local `mvnw`/IDE flow; **dev-only**, QA and prod k8s pods use their own in-cluster Redis (`redis:6379` inside the cluster, `k8s/redis.yaml`) |
| Redis (host / dev, QA) | `localhost:6380` | same caveat — host-JVM debugging only, QA's k8s namespace has its own in-cluster Redis |
| Elasticsearch | `localhost:9200` | `audit-service`'s store — index `audit-log` (prod) / `audit-log-qa` (QA) — still host-based for both dev and k8s |
| Loki | `localhost:3100` | log storage; query via Grafana's Explore tab rather than the raw API — one shared instance, filter by the `namespace` label (`demo`/`demo-qa`) — still host-based |
| Mailpit SMTP (host / dev, prod) | `localhost:1025` | `demo-mailpit`; **dev-only**, prod's in-cluster Mailpit (`k8s/mailpit.yaml`, `demo` namespace) is what `notification-service` actually sends to when running in k8s |
| Mailpit SMTP (host / dev, QA) | `localhost:1026` | same caveat — QA's k8s namespace has its own in-cluster Mailpit |
| Vault (host / dev) | `localhost:8200` | `demo-vault`, fixed dev root token; **dev-only**, in-cluster instance is shared cross-namespace the same way MySQL is |
| Rancher (HTTP) | `http://localhost:9080` | redirects to the HTTPS UI at `:9443` |

k3d cluster ports (`k3d cluster create`, see [Kubernetes / GitOps](#kubernetes--gitops)): `18090` →
Traefik HTTP (routes every `*.demo.localhost` ingress by hostname — frontend, ArgoCD, Prometheus, both
environments), `18453` → Traefik HTTPS, `8081` → in-cluster Keycloak specifically (load-bearing, not
a convenience port — see `k8s/keycloak.yaml`'s comment on the main branch), `6550` → the k8s API
server (`kubectl` uses this automatically via your kubeconfig context, not something you visit
directly).

## CI/CD & versioning

`.github/workflows/ci-cd.yml` runs on every push to `main` or `testing`:

1. **Test** — full Maven reactor `verify` (unit + Testcontainers-backed integration tests, Checkstyle,
   SpotBugs) and the frontend's `lint` + typecheck + build. Nothing downstream runs if this fails.
2. **Detect changed services** — path-filters the diff between this push and the branch's own previous
   commit (`base: github.event.before`, not the default branch — `main` and `testing` differ
   permanently in QA-only files and namespace-specific manifests, so diffing against `main` would make
   almost every `testing` push look like "everything changed") so only services that actually changed
   get rebuilt (a shared module or `Dockerfile.service` changing forces a full rebuild, since every
   image build is `mvnw -pl <service> -am`). A manual `workflow_dispatch` run (Actions tab → "Run
   workflow") skips the filter and rebuilds everything — useful after a change that doesn't touch any
   single service's own path but every service still needs picking up.
3. **Build & push** — each changed service's image goes to GHCR (`ghcr.io/<owner>/demo-<service>`),
   tagged with both the commit SHA and that service's own `pom.xml`/`package.json` version (each
   service versions independently, starting at `1.0.0` — bump it by hand when you want to mark a
   release). Images are `linux/arm64` only, matching this k3d cluster's nodes.
4. **Update manifests** — bumps the changed services' `image:` lines in `k8s/*.yaml` to the new commit
   SHA tag and commits straight back to whichever branch triggered the run, gated behind the images
   already existing in GHCR — ArgoCD's `selfHeal` never sees a manifest pointing at an unpullable
   image. Deploying by the immutable SHA (not the semver tag) means every commit that touches a
   service produces a genuinely new tag and a real manifest diff, so a rollout always happens —
   nothing depends on remembering to bump that service's version. The semver tag still rides along on
   the same image purely for human-readable release tracking.

GHCR packages are private, so every Deployment references `imagePullSecrets: ghcr-pull-secret` — a
`kubernetes.io/dockerconfigjson` Secret created directly in each namespace (`demo`, `demo-qa`) via
`kubectl create secret docker-registry`, never committed to git.

## Kubernetes / GitOps

`k8s/` holds every application manifest for **production** (namespace `demo`, branch `main`); the same
path on the **`testing`** branch holds QA's manifests (namespace `demo-qa`) — see
[QA / testing environment](#qa--testing-environment). ArgoCD itself is installed once into an `argocd`
namespace
(`kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml`),
then both `k8s-argocd/application.yaml` (tracks `main` → `demo`) and `k8s-argocd/application-qa.yaml`
(tracks `testing` → `demo-qa`) are applied, each with auto-sync + self-heal enabled. From there it's
push-to-deploy — CI/CD above handles the build/push/manifest-bump, ArgoCD picks up the commit on its
own. `k8s-argocd/` holds the manifests applied once by hand, not GitOps-managed — the ArgoCD
`Application` CRs themselves and the ArgoCD ingress (chicken-and-egg: nothing can sync them into
existence before ArgoCD is watching anything).

`Dockerfile.service` (repo root) is a single parameterized Dockerfile (`--build-arg SERVICE=<module>`) shared
by every backend service — build context is the repo root so the multi-module Maven build can see
sibling modules. Its build stage is pinned to `--platform=$BUILDPLATFORM` (the jar it produces is
architecture-independent bytecode) so cross-compiling for the cluster's arm64 nodes on an amd64 CI
runner needs no QEMU emulation — only the final runtime layer (`COPY`, no execution) is actually
arm64.

## QA / testing environment

A second, fully independent deploy target — same cluster, same shared stateful infra, separate
namespace and separate ArgoCD Application from production:

```mermaid
flowchart LR
    subgraph GIT["GitHub"]
        MAIN["main branch"]
        TEST["testing branch"]
    end

    subgraph K8S["k3d cluster"]
        subgraph DEMO["namespace: demo (prod)"]
            APPS_PROD["17 services\n+ frontend"]
            MYSQL[("MySQL (in-cluster)\ndb: demo / demo_qa")]
            KC["Keycloak (in-cluster)\nrealm: demo / demo-qa"]
            VAULT["Vault (in-cluster)"]
            MAILPIT_PROD["Mailpit (in-cluster)"]
        end
        subgraph DEMOQA["namespace: demo-qa (QA)"]
            APPS_QA["17 services\n+ frontend"]
            KAFKA_QA["Kafka (in-cluster)\nk8s/kafka.yaml"]
            REDIS_QA["Redis (in-cluster)\nk8s/redis.yaml"]
            MAILPIT_QA["Mailpit (in-cluster)"]
        end
        ARGO_PROD["ArgoCD app: demo"]
        ARGO_QA["ArgoCD app: demo-qa"]
    end

    subgraph SHARED["Shared host infra — one instance, environment-scoped by name"]
        MONGO[("MongoDB\ndb: <svc> / <svc>_qa")]
        ES[("Elasticsearch\nindex: audit-log / audit-log-qa")]
    end

    subgraph ISO_PROD["Prod-only host infra — dev/host-debug only, cluster doesn't depend on these"]
        KAFKA_PROD["Kafka :9092/:9094"]
        REDIS_PROD["Redis :6379"]
    end

    MAIN -- "CI/CD: build, push, bump k8s/" --> ARGO_PROD
    TEST -- "CI/CD: build, push, bump k8s/" --> ARGO_QA
    ARGO_PROD --> APPS_PROD
    ARGO_QA --> APPS_QA

    APPS_PROD --> MYSQL & MONGO & ES & KC & VAULT & MAILPIT_PROD
    APPS_QA --> MONGO & ES & KAFKA_QA & REDIS_QA & MAILPIT_QA
    APPS_QA -. "cross-namespace Service DNS" .-> MYSQL & KC & VAULT
```

- **Branch model**: `main` is production (bugfixes branch from here); `develop` is where feature
  branches merge; `testing` is the QA environment itself — merging `develop` → `testing` and pushing
  deploys to QA the same way pushing to `main` deploys to prod.
- **k8s**: namespace `demo-qa`, ArgoCD Application `demo-qa` (tracks the `testing` branch's own
  `k8s/` path), ingress at `qa.demo.localhost` — same port (`18090`) as prod, routed by hostname.
- **Infra**: MySQL, Keycloak, and Vault now run **in-cluster in the `demo` namespace** (main
  branch's `k8s/mysql.yaml`, `k8s/keycloak.yaml`, `k8s/vault.yaml`) — QA reaches them
  **cross-namespace** (`mysql.demo.svc.cluster.local` etc. — k8s Services are reachable across
  namespaces by default, no NetworkPolicy restricting it here) instead of getting duplicate
  containers, same "one shared instance, environment-scoped by name" reasoning as before (QA gets
  its own `demo_qa` database / `demo-qa` Keycloak realm). Kafka, Redis, and Mailpit run
  **in-cluster and genuinely separate per namespace** (`k8s/kafka.yaml`, `k8s/redis.yaml`,
  `k8s/mailpit.yaml`) — Kafka/Redis because shared topics would mean QA test traffic triggering
  production's saga, Mailpit because it was always a separate instance per environment even on the
  host. MongoDB and Elasticsearch are the only infra still host-based, reached via
  `host.k3d.internal` — same instance, QA gets its own database/index names.
  `docker-compose.yml`/`docker-compose.qa.yml`'s equivalent containers for everything now in-cluster
  (Kafka, Redis, MySQL, Keycloak, Mailpit, Vault) still exist for host-JVM debugging (`kcat`,
  `redis-cli`, a local IDE run) — the k8s namespaces themselves just don't depend on them anymore.
- **One frontend image, two Keycloak realms**: Vite bakes `VITE_KEYCLOAK_REALM` in at build time, but
  the same built image is deployed to both `demo` and `demo-qa` — a build-time value can't vary per
  environment. `frontend/src/auth/keycloak.ts` instead resolves the realm at runtime from the
  hostname (`qa.` prefix → `demo-qa`, anything else → the build-time default), matching the
  `demo.localhost` / `qa.demo.localhost` ingress split above.
- **`demo_qa` database creation** on the shared in-cluster MySQL is handled automatically by a Job
  (`mysql-create-qa-db` in main's `k8s/mysql.yaml`) rather than a manual step now. The equivalent
  manual command still applies if you're instead running the shared MySQL on the host (see
  `docker-compose.qa.yml`'s header comment) — e.g. for the plain host-JVM/`start-local.sh` flow.
  MongoDB and Elasticsearch need no equivalent step — both auto-create on first write.
- **Excluded from QA on purpose**: `promtail-daemonset.yaml` and `prometheus.yaml` are cluster-wide,
  single-shared-instance resources (see [Observability](#observability)) — duplicating them per
  environment would just make the `demo` and `demo-qa` Applications fight over the same
  ClusterRole/ClusterRoleBinding names.

## Observability

- **Metrics**: every service exposes `/actuator/prometheus`. Two separate Prometheus instances cover
  two separate deploy modes — `docker-compose.yml`'s (static targets, `host.docker.internal:<port>`)
  covers the host-JVM/docker-compose dev flow; a second one runs *inside* the k3d cluster
  (`k8s/prometheus.yaml`, namespace `monitoring`) covering the k8s-deployed services in **both**
  `demo` and `demo-qa`, discovered via `kubernetes_sd_configs` (`role: pod`, opted in by the
  `monitored: "true"` label most manifests already carry) and labeled by `namespace`. Pod IPs on the
  k3d overlay network aren't reachable from outside the cluster at all, which is why this one has to
  run in-cluster rather than as a third docker-compose static-target job. Grafana has both as
  datasources, plus two pre-provisioned dashboards (`docker/grafana/dashboards/`): "Services Overview"
  (the docker-compose/host-JVM Prometheus) and **"Kubernetes Overview (prod vs QA)"** (the in-cluster
  one) — the latter has a `namespace` filter variable and puts prod/QA side by side in the top row
  (Services Up/Down for each), so environment health is a single glance, not two separate dashboards.
- **Logs**: services log to stdout; in Docker Compose that's `docker logs <container>`. In k8s,
  Promtail (`k8s/promtail-daemonset.yaml`, one shared instance, not per-environment) ships every pod's
  logs — from both namespaces — to the same Loki instance, labeled by `namespace`. Query either
  through Grafana's Explore tab, e.g. `{namespace="demo-qa"}` to see QA only.
- **Audit trail**: every REST call across every service is captured (who, what, when, request/response
  bodies with secrets redacted) and shipped to Elasticsearch — index `audit-log` for prod, `audit-log-qa`
  for QA (same shared ES instance, see [QA / testing environment](#qa--testing-environment)). The admin
  UI's history icons (Users, Products, Media, Chat) show the full change timeline with before/after
  diffs per field, powered by `audit-service`'s `RecordHistoryService`. Three Kibana dashboards cover
  the same data for ad-hoc querying, all auto-imported on startup (`kibana-dashboard-init` in
  `docker-compose.yml`): **"Audit Trail"** (`audit-trail-dashboard.ndjson`, the original — index
  pattern `audit-log*`, both environments together, for cross-environment searching), and
  **"Audit Trail — Production"** / **"Audit Trail — QA"** (`audit-trail-dashboard-{prod,qa}.ndjson`),
  each pinned to its own exact index instead of relying on a filter — matches the Grafana dashboard's
  approach of making the environment split a first-class view, not something the reader has to
  remember to filter for.

## Repo layout

```
demo/
├── common-security/, common-audit/, common-model/   # shared library modules
├── eureka-server/, config-server/, gateway-service/  # platform services
├── user-service/, product-service/, order-service/, payment-service/,
│   shipping-service/, delivery-service/, inventory-service/,
│   notification-service/, audit-service/, chat-service/,
│   product-comment-service/, product-media-service/, product-review-service/
├── frontend/                # Vue 3 SPA
├── docker/                  # Keycloak realms (demo + demo-qa), Grafana/Kibana/Prometheus provisioning
├── k8s/                     # Kubernetes manifests — prod content on main, QA content on testing
│                             #   (includes kafka.yaml/redis.yaml — in-cluster infra, QA namespace)
├── k8s-argocd/              # ArgoCD Application CRs + ingress, applied once by hand, not GitOps-synced
├── docker-compose.yml       # full local stack (dev-only Kafka/Redis + every service + frontend)
├── docker-compose.qa.yml    # QA host-debug infra (Kafka/Redis/Mailpit) — Mailpit's real dependency;
│                             #   Kafka/Redis here are for host-JVM debugging only, not used by k8s
├── start-local.sh, stop-local.sh  # host-JVM/npm dev flow — each takes --infra and/or --services
├── k8s-local.sh              # k3d cluster + its host infra: start/stop/restart/status
├── .github/workflows/       # CI/CD: test -> build & push to GHCR -> bump k8s manifests
└── pom.xml                  # Maven reactor parent
```

Each backend service module follows the same internal package layout under its base package
(`com.example.<service>`): `config/` (Spring `@Configuration`/security config), `controller/`
(REST endpoints), `enums/` (status/type enums), `model/` (entities, DTOs, `*ModelAssembler`s),
`repository/` (Spring Data repositories), `saga/` (`@KafkaListener` domain-event consumers,
including the choreographed-saga participants), `service/` (business logic, external clients).
Modules with a WebSocket surface (`chat-service`, `notification-service`) add a `websocket/`
package for handlers/interceptors; `gateway-service` adds a `filter/` package for its servlet
filter. The `*ServiceApplication` bootstrap class stays directly in the base package, not in any
sub-package.
