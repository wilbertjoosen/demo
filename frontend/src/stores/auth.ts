import { defineStore } from 'pinia'
import { keycloak } from '../auth/keycloak'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    isAuthenticated: keycloak.authenticated ?? false,
    username: keycloak.tokenParsed?.preferred_username ?? '',
    keycloakId: keycloak.subject ?? '',
    isAdmin: keycloak.hasRealmRole('admin'),
    isFinance: keycloak.hasRealmRole('finance'),
    isProductManager: keycloak.hasRealmRole('product_manager'),
    isShippingManager: keycloak.hasRealmRole('shipping_manager'),
    isInventoryManager: keycloak.hasRealmRole('inventory_manager'),
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
