/**
 * Local JVM dev (.env.development) points these straight at each service's own host port, since
 * there's no ingress in that flow. The deployed frontend (.env.production) leaves the env var empty
 * and relies on same-origin instead — the k8s Ingress (k8s/ingress.yaml) proxies /ws/conversations
 * and /ws/notifications on the same origin the frontend itself is served from, mirroring how
 * VITE_API_BASE_URL='' already works for REST calls. A build-time absolute ws://localhost:PORT
 * value only resolves on the machine actually running that service — never true for a browser
 * loading the deployed frontend.
 */
export function resolveWsUrl(envValue: string, path: string): string {
  if (envValue) return envValue
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${window.location.host}${path}`
}
