/**
 * Escalating visual urgency for rows that have been sitting untouched: a soft yellow tint past 1
 * hour, a soft red tint past 24 hours. Plain inline styles (not Tailwind classes) — a row-class-name
 * function builds class names at runtime, which Tailwind's static content scanner can't see, so the
 * generated CSS bundle would silently omit the rule (the exact bug behind the report-chart height
 * regression earlier this session). Light tints + default text color keep it readable per spec,
 * rather than saturated backgrounds that fight the row's own text/tag colors.
 */
const ONE_HOUR_MS = 60 * 60 * 1000
const ONE_DAY_MS = 24 * ONE_HOUR_MS

export function agingRowStyle(createdAt: string, isPending: boolean): Record<string, string> {
  if (!isPending) return {}
  const ageMs = Date.now() - new Date(createdAt).getTime()
  if (ageMs > ONE_DAY_MS) return { backgroundColor: '#fef2f2' }
  if (ageMs > ONE_HOUR_MS) return { backgroundColor: '#fefce8' }
  return {}
}
