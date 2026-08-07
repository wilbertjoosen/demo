import { ref } from 'vue'
import axios from 'axios'

/**
 * Module-level (not per-component) state — one shared health check for the whole app, polled
 * from App.vue so the "backend is unavailable" banner shows up before the user ever tries an
 * action, not just as a reactive error toast after a failed request.
 */
const available = ref(true)
const checking = ref(false)
let pollHandle: ReturnType<typeof setInterval> | null = null

async function checkNow() {
  checking.value = true
  try {
    await axios.get(`${import.meta.env.VITE_API_BASE_URL}/actuator/health`, { timeout: 4000 })
    available.value = true
  } catch {
    available.value = false
  } finally {
    checking.value = false
  }
}

export function useBackendHealth() {
  function startPolling(intervalMs = 15000) {
    if (pollHandle) return
    checkNow()
    pollHandle = setInterval(checkNow, intervalMs)
  }

  function stopPolling() {
    if (pollHandle) {
      clearInterval(pollHandle)
      pollHandle = null
    }
  }

  return { available, checking, checkNow, startPolling, stopPolling }
}
