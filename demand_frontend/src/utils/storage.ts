function get<T>(key: string): T | null {
  const value = localStorage.getItem(key)
  if (value === null) return null
  try {
    return JSON.parse(value) as T
  } catch {
    return value as unknown as T
  }
}

function set(key: string, value: unknown): void {
  const serialized = typeof value === 'string' ? value : JSON.stringify(value)
  localStorage.setItem(key, serialized)
}

function remove(key: string): void {
  localStorage.removeItem(key)
}

function clear(): void {
  localStorage.clear()
}

export default { get, set, remove, clear }
