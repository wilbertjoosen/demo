import { defineStore } from 'pinia'
import { keycloak } from '../auth/keycloak'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    isAuthenticated: keycloak.authenticated ?? false,
    username: keycloak.tokenParsed?.preferred_username ?? '',
    keycloakId: keycloak.tokenParsed?.sub ?? '',
    isAdmin: keycloak.hasRealmRole('admin'),
    isFinance: keycloak.hasRealmRole('finance'),
    isProductManager: keycloak.hasRealmRole('product_manager'),
    isShippingManager: keycloak.hasRealmRole('shipping_manager'),
    isInventoryManager: keycloak.hasRealmRole('inventory_manager'),
    /** Present only on a token issued via admin impersonation — see api/impersonation.ts. */
    impersonatorUsername: (keycloak.tokenParsed as { impersonator?: { username?: string } } | undefined)
      ?.impersonator?.username ?? null,
  }),
  actions: {
    login() {
      keycloak.login()
    },
    logout() {
      keycloak.logout({ redirectUri: window.location.origin })
    },
  },
})
