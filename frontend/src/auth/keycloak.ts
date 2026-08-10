import Keycloak from 'keycloak-js'

/**
 * One frontend image is deployed to both demo (host demo.localhost, realm "demo") and demo-qa
 * (host qa.demo.localhost, realm "demo-qa") — see k8s/ingress.yaml on the main/testing branches.
 * Vite bakes VITE_KEYCLOAK_REALM at build time, so it can't vary per environment; the hostname's
 * "qa." prefix is the one signal available at runtime that already distinguishes the two.
 */
export function resolveKeycloakRealm(): string {
  return window.location.hostname.startsWith('qa.') ? 'demo-qa' : import.meta.env.VITE_KEYCLOAK_REALM
}

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: resolveKeycloakRealm(),
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
})

let initialized: Promise<boolean> | null = null

/**
 * `check-sso` (not `login-required`): the app mounts either way, so an unauthenticated visitor sees
 * an in-app guest landing view with a Login button instead of being bounced straight to Keycloak.
 */
export function initKeycloak(): Promise<boolean> {
  if (!initialized) {
    initialized = keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    })
  }
  return initialized
}

export function hasRole(role: string): boolean {
  return keycloak.hasRealmRole(role)
}
