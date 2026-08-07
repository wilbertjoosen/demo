import axios from 'axios'
import { keycloak } from '../auth/keycloak'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

http.interceptors.request.use(async (config) => {
  try {
    await keycloak.updateToken(30)
  } catch {
    keycloak.login()
    return Promise.reject(new Error('session expired, redirecting to login'))
  }
  config.headers.Authorization = `Bearer ${keycloak.token}`
  return config
})
