import { isAxiosError } from 'axios'

/**
 * Backend validation failures come back from ApiExceptionHandler as
 * {"status":400,"message":"validation failed","errors":{field: message}} — surface those
 * per-field messages when present instead of a generic "something went wrong" toast.
 */
/** Gateway/downstream-outage status codes — the request reached something, but not the actual service. */
const SERVICE_UNAVAILABLE_STATUSES = new Set([502, 503, 504])

export function extractErrorMessage(error: unknown, fallback: string, serviceUnavailableMessage?: string): string {
  if (isAxiosError(error)) {
    // No response at all (connection refused/timeout) means the request never reached anything
    // that could return a proper error body; a 502/503/504 means it reached the gateway but the
    // downstream service it routes to is down. Both are "try again later", not a rejected request.
    if (serviceUnavailableMessage && (!error.response || SERVICE_UNAVAILABLE_STATUSES.has(error.response.status))) {
      return serviceUnavailableMessage
    }
    const data = error.response?.data as { message?: string; errors?: Record<string, string> } | undefined
    if (data?.errors && Object.keys(data.errors).length > 0) {
      return Object.entries(data.errors)
        .map(([field, message]) => `${field}: ${message}`)
        .join('; ')
    }
    if (data?.message) {
      return data.message
    }
  }
  return fallback
}
