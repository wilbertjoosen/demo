import { defineStore } from 'pinia'
import { keycloak } from '../auth/keycloak'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    isAuthenticated: keycloak.authenticated ?? false,
    username: keycloak.tokenParsed?.preferred_username ?? '',
    isAdmin: keycloak.hasRealmRole('admin'),
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
