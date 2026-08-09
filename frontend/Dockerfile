# .env.production supplies the same localhost:* URLs used in local dev — the SPA runs in the
# user's browser on the host, never inside the compose network, regardless of how the backend runs.
# Pinned to the build host's own platform, not the target — the built dist/ is plain JS/CSS/HTML,
# architecture-independent, so cross-compiling it under QEMU for a foreign target would be wasted
# emulation overhead. Only the final nginx layer below needs to match the target platform.
FROM --platform=$BUILDPLATFORM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
