export function clampProgress(progress: number, min = 0, max = 100): number {
  if (Number.isNaN(progress)) return min
  return Math.min(max, Math.max(min, Math.round(progress)))
}

export function computeOfficePreviewProgress(
  progress: number | undefined,
  attempt: number,
  maxAttempts: number,
): number {
  if (typeof progress === 'number' && progress > 0) {
    return clampProgress(12 + progress * 0.8, 12, 94)
  }

  const ratio = maxAttempts > 0 ? attempt / maxAttempts : 0
  return clampProgress(16 + ratio * 52, 16, 82)
}

async function readResponseBuffer(
  response: Response,
  onProgress?: (progress: number) => void,
): Promise<ArrayBuffer> {
  const total = Number(response.headers.get('content-length') || 0)

  if (!response.body) {
    const buffer = await response.arrayBuffer()
    onProgress?.(96)
    return buffer
  }

  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let loaded = 0
  let fallbackProgress = 12

  onProgress?.(8)

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    if (!value) continue

    chunks.push(value)
    loaded += value.byteLength

    if (total > 0) {
      onProgress?.(clampProgress(12 + (loaded / total) * 82, 12, 96))
    } else {
      fallbackProgress = clampProgress(fallbackProgress + 7, 12, 90)
      onProgress?.(fallbackProgress)
    }
  }

  const merged = new Uint8Array(loaded)
  let offset = 0
  for (const chunk of chunks) {
    merged.set(chunk, offset)
    offset += chunk.byteLength
  }
  onProgress?.(96)
  return merged.buffer
}

export async function fetchTextWithProgress(
  url: string,
  onProgress?: (progress: number) => void,
): Promise<string> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`请求失败: ${response.status}`)
  }
  const buffer = await readResponseBuffer(response, onProgress)
  return new TextDecoder('utf-8').decode(buffer)
}

export async function fetchBlobWithProgress(
  url: string,
  onProgress?: (progress: number) => void,
): Promise<Blob> {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`请求失败: ${response.status}`)
  }
  const buffer = await readResponseBuffer(response, onProgress)
  return new Blob([buffer], { type: response.headers.get('content-type') || 'application/octet-stream' })
}
