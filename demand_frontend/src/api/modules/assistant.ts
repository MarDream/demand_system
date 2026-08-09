import request from '@/api/request'
import { getToken } from '@/utils/auth'
import type {
  AssistantActionPayload,
  AssistantChatRequest,
  AssistantMessage,
  AssistantMetaPayload,
  AssistantSession,
  AssistantTask,
  AssistantUsage,
  ThinkingStep,
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
  onThinkingSteps?: (steps: ThinkingStep[]) => void
  onTaskUpdate?: (task: AssistantTask) => void
  onDelta?: (delta: string) => void
  /** 深度思考内容增量（reasoningDelta 事件，流式） */
  onReasoningDelta?: (delta: string) => void
  /** 深度思考完整内容（reasoning 事件，RAG 分支一次性下发） */
  onReasoning?: (content: string) => void
  onUsage?: (usage: AssistantUsage) => void
  onDone?: (message: AssistantMessage) => void
  onError?: (message: string) => void
}

export async function streamAssistantMessage(
  sessionId: number,
  data: AssistantChatRequest,
  handlers: AssistantStreamHandlers,
  signal?: AbortSignal,
) {
  await streamAssistantRequest(sessionId, 'stream', data, handlers, signal)
}

export async function regenerateAssistantMessage(
  sessionId: number,
  data: AssistantChatRequest & { assistantMessageId: number | string },
  handlers: AssistantStreamHandlers,
  signal?: AbortSignal,
) {
  await streamAssistantRequest(sessionId, 'regenerate', data, handlers, signal)
}

async function streamAssistantRequest(
  sessionId: number,
  action: 'stream' | 'regenerate',
  data: AssistantChatRequest & { assistantMessageId?: number | string },
  handlers: AssistantStreamHandlers,
  signal?: AbortSignal,
) {
  const baseURL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  const token = getToken()
  const suffix = action === 'regenerate' ? 'regenerate' : 'stream'
  const response = await fetch(`${baseURL}/v1/assistant/sessions/${sessionId}/messages/${suffix}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`${action === 'regenerate' ? '重新生成' : '操作助手'}请求失败: HTTP ${response.status}`)
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

  if (eventName === 'reasoningDelta') {
    handlers.onReasoningDelta?.(payload)
    return eventName
  }

  if (eventName === 'reasoning') {
    handlers.onReasoning?.(payload)
    return eventName
  }

  if (eventName === 'usage') {
    try {
      handlers.onUsage?.(JSON.parse(payload) as AssistantUsage)
    } catch {
      // usage 帧解析失败时忽略
    }
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
  } else if (eventName === 'thinkingSteps') {
    handlers.onThinkingSteps?.(parsed as ThinkingStep[])
  } else if (eventName === 'taskUpdate') {
    handlers.onTaskUpdate?.(parsed as AssistantTask)
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

// ==================== 快捷提问 API ====================

export interface QuickQuestion {
  id: number
  category: 'manual_curated' | 'auto_extracted' | 'ai_suggested'
  questionText: string
  pageRoute: string | null
  weight: number
  sortOrder: number
  hitCount: number
  status: 'enabled' | 'disabled'
}

export interface ExtractedQuestion {
  questionText: string
  questionHash: string
  pageRoute: string
  frequency: number
  avgRating: number
  aiConfidence: number
  lastAskedAt: string
  infoLevel: '丰富' | '中等' | '基础'
}

export function getQuickQuestions(pageRoute?: string) {
  return request.get<QuickQuestion[]>('/v1/assistant/quick-questions', {
    params: { pageRoute: pageRoute || '' },
  })
}

export function recordQuickQuestionClick(id: number) {
  return request.post<void>(`/v1/assistant/quick-questions/${id}/click`)
}

export function listAllQuickQuestions(params?: { pageRoute?: string; status?: string; category?: string }) {
  return request.get<QuickQuestion[]>('/v1/assistant/admin/quick-questions', { params })
}

export function getExtractedQuestions(windowDays?: number, minFrequency?: number) {
  return request.get<ExtractedQuestion[]>('/v1/assistant/admin/quick-questions/extracted', {
    params: { windowDays: windowDays ?? 30, minFrequency: minFrequency ?? 5 },
  })
}

export function createQuickQuestion(data: {
  questionText: string
  category?: string
  pageRoute?: string | null
  weight?: number
  sortOrder?: number
  status?: string
}) {
  return request.post<QuickQuestion>('/v1/assistant/admin/quick-questions', data)
}

export function updateQuickQuestion(id: number, data: {
  questionText: string
  category?: string
  pageRoute?: string | null
  weight?: number
  sortOrder?: number
  status?: string
}) {
  return request.put<QuickQuestion>(`/v1/assistant/admin/quick-questions/${id}`, data)
}

export function deleteQuickQuestion(id: number) {
  return request.delete<void>(`/v1/assistant/admin/quick-questions/${id}`)
}

export function toggleQuickQuestionStatus(id: number, status: string) {
  return request.put<void>(`/v1/assistant/admin/quick-questions/${id}/status`, null, { params: { status } })
}

export function adoptAiSuggestion(data: { questionText: string; pageRoute?: string | null; questionHash?: string }) {
  return request.post<QuickQuestion>('/v1/assistant/admin/quick-questions/adopt', data)
}
