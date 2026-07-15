import request from '@/api/request'
import { getToken } from '@/utils/auth'
import type {
  AssistantActionPayload,
  AssistantChatRequest,
  AssistantMessage,
  AssistantMetaPayload,
  AssistantSession,
} from '@/types/assistant'

export function getAssistantSessions() {
  return request.get<AssistantSession[]>('/v1/assistant/sessions')
}

export function createAssistantSession(data?: { title?: string }) {
  return request.post<AssistantSession>('/v1/assistant/sessions', data || {})
}

export function getAssistantMessages(sessionId: number) {
  return request.get<AssistantMessage[]>(`/v1/assistant/sessions/${sessionId}/messages`)
}

export function deleteAssistantSession(sessionId: number) {
  return request.delete<void>(`/v1/assistant/sessions/${sessionId}`)
}

export interface AssistantStreamHandlers {
  onMeta?: (payload: AssistantMetaPayload) => void
  onActions?: (payload: AssistantActionPayload) => void
  onDelta?: (delta: string) => void
  onDone?: (message: AssistantMessage) => void
  onError?: (message: string) => void
}

export async function streamAssistantMessage(
  sessionId: number,
  data: AssistantChatRequest,
  handlers: AssistantStreamHandlers,
) {
  const baseURL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const token = getToken()
  const response = await fetch(`${baseURL}/v1/assistant/sessions/${sessionId}/messages/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
  })

  if (!response.ok || !response.body) {
    throw new Error(`操作助手请求失败: HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const eventBlock of events) {
      const eventName = handleStreamEvent(eventBlock, handlers)
      if (eventName === 'done' || eventName === 'error') {
        await reader.cancel().catch(() => undefined)
        return
      }
    }
  }

  if (buffer.trim()) {
    handleStreamEvent(buffer, handlers)
  }
}

function handleStreamEvent(eventBlock: string, handlers: AssistantStreamHandlers) {
  const lines = eventBlock.split(/\r?\n/)
  let eventName = 'message'
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  })

  const payload = dataLines.join('\n')
  if (!payload) return eventName

  if (eventName === 'delta') {
    handlers.onDelta?.(payload)
    return eventName
  }

  if (eventName === 'error') {
    handlers.onError?.(parseStreamMessage(payload))
    return eventName
  }

  const parsed = JSON.parse(payload)
  if (eventName === 'meta') {
    handlers.onMeta?.(parsed as AssistantMetaPayload)
  } else if (eventName === 'actions') {
    handlers.onActions?.(parsed as AssistantActionPayload)
  } else if (eventName === 'done') {
    handlers.onDone?.(parsed as AssistantMessage)
  }
  return eventName
}

function parseStreamMessage(payload: string) {
  try {
    const parsed = JSON.parse(payload)
    return parsed?.message || payload
  } catch {
    return payload
  }
}
