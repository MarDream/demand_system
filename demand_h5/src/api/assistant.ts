import { get, post, del } from './request'
import { getToken } from '@/utils/auth'

export interface AssistantSession {
  id: number
  title: string
  lastMessagePreview?: string | null
  lastMessageAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface AssistantMessage {
  id: number | string
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
}

export interface ChatStreamHandlers {
  onDelta?: (delta: string) => void
  onDone?: (message: AssistantMessage) => void
  onError?: (message: string) => void
}

export function getSessions() {
  return get<AssistantSession[]>('/v1/assistant/sessions')
}

export function createSession(data?: { title?: string }) {
  return post<AssistantSession>('/v1/assistant/sessions', data || {})
}

export function getMessages(sessionId: number) {
  return get<AssistantMessage[]>(`/v1/assistant/sessions/${sessionId}/messages`)
}

export function deleteSession(sessionId: number) {
  return del<void>(`/v1/assistant/sessions/${sessionId}`)
}

/**
 * 流式对话（SSE）。仅处理 delta / done / error 三类事件，
 * 思考过程、动作卡片等 PC 端专属能力不在 H5 展示。
 */
export async function streamMessage(
  sessionId: number,
  message: string,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
) {
  const baseURL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
  const token = getToken()
  const response = await fetch(`${baseURL}/v1/assistant/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message }),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`AI 助手请求失败: HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (block: string) => {
    let eventName = 'message'
    const dataLines: string[] = []
    block.split(/\r?\n/).forEach((line) => {
      if (line.startsWith('event:')) eventName = line.slice('event:'.length).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice('data:'.length).trimStart())
    })
    const payload = dataLines.join('\n')
    if (!payload) return eventName
    if (eventName === 'delta') {
      handlers.onDelta?.(payload)
    } else if (eventName === 'error') {
      try {
        handlers.onError?.(JSON.parse(payload)?.message || payload)
      } catch {
        handlers.onError?.(payload)
      }
    } else if (eventName === 'done') {
      try {
        handlers.onDone?.(JSON.parse(payload) as AssistantMessage)
      } catch {
        // done 帧解析失败时忽略
      }
    }
    return eventName
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      const name = dispatch(block)
      if (name === 'done' || name === 'error') {
        await reader.cancel().catch(() => undefined)
        return
      }
    }
  }
  if (buffer.trim()) dispatch(buffer)
}
