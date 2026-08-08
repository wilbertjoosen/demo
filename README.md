# Demo — Microservices Architecture Playground

A from-scratch rebuild of a single Spring Boot monolith into a full microservices system, built as a
hands-on reference for *why* you'd reach for a given distributed-systems pattern, not just *how* to
wire it up. Every pattern below exists because a concrete problem in this domain needed it — see
[Patterns demonstrated](#patterns-demonstrated) for the reasoning behind each one.

**Stack:** Spring Boot 4 / Java 26 backend (19 modules), Vue 3 + TypeScript + Element Plus frontend,
MySQL + MongoDB + Kafka + Redis + Elasticsearch, Keycloak (OAuth2/OIDC), Eureka + Spring Cloud Gateway,
Prometheus + Grafana + Loki + Kibana, Docker Compose for local infra, k3d + ArgoCD for a local
Kubernetes/GitOps loop.

## Architecture

A choreographed Kafka saga drives an order through payment, shipping, and delivery. Every service
that isn't infrastructure is independently deployable — its own jar, its own Docker image, its own k8s
Deployment.

| Service | Port | Store | Responsibility |
|---|---|---|---|
| `gateway-service` | 8080 | — | Spring Cloud Gateway; routes `/api/**` to backend services by path, CORS |
| `eureka-server` | 8761 | — | Service discovery — every backend registers here |
| `config-server` | 8888 | — | Centralized config (currently minimal; most config is per-service `application.yaml`) |
| `user-service` | 8082 | MongoDB | User profiles; identity fields (username/email/name) are read live from Keycloak, not duplicated locally |
| `product-service` | 8083 | MongoDB | Product catalog; reserve/release stock; Spring Batch CSV bulk import |
| `order-service` | 8084 | MySQL (write) + MongoDB (read) | **Saga initiator** + **CQRS**: `POST /api/orders` hits MySQL directly (ACID), `GET /api/orders*` reads from a Mongo projection kept in sync by the same Kafka listeners the saga needs anyway |
| `payment-service` | 8086 | MongoDB | Saga participant: charges, refunds on downstream failure |
| `shipping-service` | 8088 | MongoDB | Saga participant: carrier selection (UPS/DHL), tracking |
| `delivery-service` | 8087 | MongoDB | Saga participant: last-mile delivery simulation |
| `inventory-service` | 8089 | MongoDB | Per-warehouse stock, called synchronously by `order-service` (the one sync inter-service call in the system, see [Circuit breaker](#circuit-breaker)) |
| `notification-service` | 8085 | — | Kafka consumer on every topic → WebSocket broadcast (`/ws/notifications`) + email via Mailpit |
| `audit-service` | 8090 | Elasticsearch | Consumes the audit trail every service emits; reconstructs field-level diffs and full record history |
| `chat-service` | 8094 | MongoDB | Public per-product chat rooms **and** private user-to-user direct messages (JWT-authenticated WebSocket, delivery/read receipts, typing indicators) |
| `product-comment-service` | 8091 | MongoDB | Product comments, ownership-enforced editing |
| `product-media-service` | 8092 | MongoDB + local disk | Product photos/videos/documents, file upload |
| `product-review-service` | 8093 | MongoDB | Product ratings/reviews |
| `common-security` | — | — | Shared JWT resource-server config, reused by every service |
| `common-audit` | — | — | Shared aspect that captures every REST call's request/response for the audit trail |
| `common-model` | — | — | Shared DTOs (e.g. `Address`) |
| `frontend` | 5173 (dev) | — | Vue 3 SPA — the only thing that talks to the gateway; runs in the browser regardless of how the backend is deployed |

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

### Option B — full Docker Compose stack

```bash
docker compose up -d --build
```

Everything (infra + all 19 backend services + frontend) runs in containers on one network.

### Option C — Kubernetes (k3d) + GitOps

```bash
k3d cluster create demo --servers 1 --agents 2 -p "18090:80@loadbalancer" -p "18453:443@loadbalancer" --api-port 6550
kubectl apply -f k8s/
```

App: http://demo.localhost:18090 — infra (MySQL/Mongo/Kafka/Keycloak/etc.) still runs via Docker
Compose on the host; pods reach it through `host.k3d.internal`. See [Kubernetes / GitOps](#kubernetes--gitops)
for how ArgoCD takes over from here, and note the one manual step k3d always needs: **new/changed
Docker images must be `k3d image import`ed** — there's no registry, so ArgoCD only manages manifests,
never image builds.

## Default credentials

| User | Password | Realm role |
|---|---|---|
| `demo` | `demo` | `user` |
| `admin` | `admin` | `user`, `admin` |
| `manager` | `manager` | `finance`, `product_manager`, `shipping_manager`, `inventory_manager` |

Realm: `demo`. Client: `demo-spa` (public, PKCE).

## Useful URLs

| Tool | URL | Credentials |
|---|---|---|
| Frontend (dev) | http://localhost:5173 | — |
| Frontend (k8s) | http://demo.localhost:18090 | — |
| Keycloak admin | http://localhost:8081 | `admin` / `admin` (realm: **`demo`**, not `master` — see note below) |
| Swagger UI (aggregated) | http://localhost:8080/swagger-ui.html | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | — |
| Kibana | http://localhost:5601 | — |
| Kafka UI | http://localhost:8095 | — |
| Mailpit (SMTP inbox) | http://localhost:8025 | — |
| Rancher (Docker container) | https://localhost:9443 | bootstrap password `rancherdemo123` (set via `CATTLE_BOOTSTRAP_PASSWORD` in `docker-compose.yml`) |
| ArgoCD | http://argocd.localhost:18090 (via `k8s-argocd/ingress.yaml`) | `admin` / `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` |

> **Keycloak gotcha:** the admin console defaults to the `master` realm, which only ever contains the
> bootstrap admin. Application users (`demo`, `admin`, `manager`, everyone created through the app)
> live in the **`demo`** realm — switch realms via the dropdown in the top-left before looking for them.

### Infrastructure connection ports

For connecting a DB client, `redis-cli`, `kcat`, etc. directly rather than through a UI:

| Service | Host:Port | Notes |
|---|---|---|
| MySQL | `localhost:3306` | `order-service`'s write model (db `demo`, user/pass `demo`/`demo`) |
| MongoDB | `localhost:27017` | every other service's store, one logical DB per service |
| Kafka (host clients) | `localhost:9092` | `PLAINTEXT` listener for local JVM services / host tools |
| Kafka (k8s pods) | `host.k3d.internal:9094` | dedicated `PLAINTEXT_K8S` listener, only reachable from inside the k3d cluster |
| Redis | `localhost:6379` | Resilience4j response caching |
| Elasticsearch | `localhost:9200` | `audit-service`'s store |
| Loki | `localhost:3100` | log storage; query via Grafana's Explore tab rather than the raw API |
| Mailpit SMTP | `localhost:1025` | what `notification-service` actually sends to; `:8025` above is its web inbox |
| Rancher (HTTP) | `http://localhost:9080` | redirects to the HTTPS UI at `:9443` |

k3d cluster ports (`k3d cluster create`, see [Kubernetes / GitOps](#kubernetes--gitops)): `18090` →
Traefik HTTP (frontend + ArgoCD ingress), `18453` → Traefik HTTPS, `6550` → the k8s API server
(`kubectl` uses this automatically via your kubeconfig context, not something you visit directly).

## Kubernetes / GitOps

`k8s/` holds every application manifest. ArgoCD itself is installed once into an `argocd` namespace
(`kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml`),
then `k8s-argocd/application.yaml` is applied to point it at this repo's `k8s/` path with auto-sync +
self-heal enabled. From there ArgoCD watches `main` on its own. Loop once bootstrapped:

1. Edit a manifest in `k8s/`, or push code that needs a new image
2. If code changed: `docker build -f Dockerfile.service --build-arg SERVICE=<name> -t demo/<name>:local .`
   then `k3d image import demo/<name>:local -c demo` (images aren't in a registry — this step doesn't
   happen automatically)
3. `git push` — ArgoCD picks up manifest changes on its own; a changed image still needs
   `kubectl -n demo rollout restart deployment/<name>` to actually pick up the freshly-imported image

`Dockerfile.service` (repo root) is a single parameterized Dockerfile (`--build-arg SERVICE=<module>`) shared
by every backend service — build context is the repo root so the multi-module Maven build can see
sibling modules.

## Observability

- **Metrics**: every service exposes `/actuator/prometheus`; Prometheus scrapes them; Grafana has a
  pre-provisioned "Services Overview" dashboard (`docker/grafana/dashboards/`).
- **Logs**: services log to stdout; in Docker Compose that's `docker logs <container>`. In k8s,
  Promtail (`k8s/promtail-daemonset.yaml`) ships every pod's logs to the same Loki instance — query
  either through Grafana's Explore tab.
- **Audit trail**: every REST call across every service is captured (who, what, when, request/response
  bodies with secrets redacted) and shipped to Elasticsearch. The admin UI's history icons (Users,
  Products, Media, Chat) show the full change timeline with before/after diffs per field, powered by
  `audit-service`'s `RecordHistoryService` — a Kibana dashboard (`docker/kibana/audit-trail-dashboard.ndjson`)
  covers the same data for ad-hoc querying.

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
├── docker/                  # Keycloak realm, Grafana/Kibana/Prometheus provisioning
├── k8s/                     # Kubernetes manifests (ArgoCD-synced)
├── docker-compose.yml       # full local stack (infra + every service + frontend)
└── pom.xml                  # Maven reactor parent
```
