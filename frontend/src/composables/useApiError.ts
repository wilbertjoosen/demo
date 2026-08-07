import { isAxiosError } from 'axios'
import { ElMessage } from 'element-plus'

/** Gateway/downstream-outage status codes — the request reached something, but not the actual service. */
const SERVICE_UNAVAILABLE_STATUSES = new Set([502, 503, 504])

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/**
 * Backend validation failures come back from ApiExceptionHandler as
 * {"status":400,"message":"validation failed","errors":{field: message}} — shown as a bullet list
 * (one field per line) rather than a single semicolon-joined sentence. Network failures and
 * 502/503/504 (reached the gateway, not the actual service) get a distinct "try again later"
 * message instead of a generic failure.
 */
export function showApiError(error: unknown, fallback: string, serviceUnavailableMessage?: string): void {
  if (isAxiosError(error)) {
    if (serviceUnavailableMessage && (!error.response || SERVICE_UNAVAILABLE_STATUSES.has(error.response.status))) {
      ElMessage.error(serviceUnavailableMessage)
      return
    }
    const data = error.response?.data as { message?: string; errors?: Record<string, string> } | undefined
    if (data?.errors && Object.keys(data.errors).length > 0) {
      const items = Object.entries(data.errors)
        .map(([field, message]) => `<li>${escapeHtml(field)}: ${escapeHtml(message)}</li>`)
        .join('')
      ElMessage({ message: `<ul class="list-disc pl-4">${items}</ul>`, type: 'error', dangerouslyUseHTMLString: true })
      return
    }
    if (data?.message) {
      ElMessage.error(data.message)
      return
    }
  }
  ElMessage.error(fallback)
}
