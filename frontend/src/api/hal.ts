interface HalCollection<T> {
  _embedded?: Record<string, T[]>
}

export function unwrapCollection<T>(payload: HalCollection<T>): T[] {
  if (!payload._embedded) return []
  const key = Object.keys(payload._embedded)[0]
  return key ? payload._embedded[key] : []
}
