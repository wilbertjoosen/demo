# .env.production supplies the same localhost:* URLs used in local dev — the SPA runs in the
# user's browser on the host, never inside the compose network, regardless of how the backend runs.
FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
