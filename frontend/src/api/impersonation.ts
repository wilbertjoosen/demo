import axios from 'axios'
import { keycloak, resolveKeycloakRealm } from '../auth/keycloak'

/**
 * Keycloak's admin impersonation endpoint sets a session cookie on Keycloak's own domain for the
 * target user (requires withCredentials so the browser stores it) — it does not return a token
 * directly. Redirecting through Keycloak's login URl afterward picks up that new cookie silently
 * (no credentials prompt) and completes our app's normal OIDC flow as the impersonated user.
 */
export async function impersonateUser(targetKeycloakId: string): Promise<void> {
  const base = import.meta.env.VITE_KEYCLOAK_URL
  const realm = resolveKeycloakRealm()
  await axios.post(
    `${base}/admin/realms/${realm}/users/${targetKeycloakId}/impersonation`,
    {},
    { headers: { Authorization: `Bearer ${keycloak.token}` }, withCredentials: true },
  )
  window.location.href = await keycloak.createLoginUrl()
}
