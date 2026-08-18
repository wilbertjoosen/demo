# .env.production supplies the same localhost:* URLs used in local dev — the SPA runs in the
# user's browser on the host, never inside the compose network, regardless of how the backend runs.
# Pinned to the build host's own platform, not the target — the built dist/ is plain JS/CSS/HTML,
# architecture-independent, so cross-compiling it under QEMU for a foreign target would be wasted
# emulation overhead. Only the final nginx layer below needs to match the target platform.
#
# MODE selects which .env.$MODE file Vite loads (see .env.k8s vs .env.production) — defaults to
# "production" so the docker-compose build (`docker compose up --build`, no --build-arg passed)
# behaves exactly as before. k8s builds pass --build-arg MODE=k8s: unlike docker-compose, where the
# browser reaches every backend service on its own published host port, k8s only hostPort-exposes
# Keycloak — everything else (API, WebSockets) has to go through the Traefik ingress at the SAME
# origin the page loaded from, which needs different baked-in URLs (relative API base, ingress-
# routed WS URLs) than the docker-compose build does.
ARG MODE=production
FROM --platform=$BUILDPLATFORM node:20-alpine AS build
ARG MODE
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx vue-tsc -b && npx vite build --mode $MODE

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
