import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAssistantSession,
  deleteAssistantSession,
  getAssistantMessages,
  getAssistantSessions,
  streamAssistantMessage,
} from '@/api/modules/assistant'
import type { AssistantChatRequest, AssistantMessage, AssistantMessageId, AssistantSession } from '@/types/assistant'

const DEFAULT_SESSION_TITLE = '新会话'

function buildTempId(prefix: string): AssistantMessageId {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}

function normalizeText(value?: string | null) {
  return String(value || '').replace(/\s+/g, ' ').trim()
}

function buildSessionTitleCandidate(message: string) {
  const compact = normalizeText(message)
  if (!compact) return DEFAULT_SESSION_TITLE
  return compact.length > 24 ? `${compact.slice(0, 24)}…` : compact
}

function buildMessagePreview(message: string) {
  const compact = normalizeText(message)
  if (!compact) return ''
  return compact.length > 50 ? `${compact.slice(0, 50)}…` : compact
}

function isDefaultSessionTitle(title?: string | null) {
  const compact = normalizeText(title)
  return !compact || compact === DEFAULT_SESSION_TITLE
}

function isEmptyDefaultSession(session: AssistantSession) {
  return isDefaultSessionTitle(session.title)
    && !normalizeText(session.lastMessagePreview)
    && !session.lastMessageAt
}

export const useAssistantStore = defineStore('assistant', () => {
  const visible = ref(false)
  const initialized = ref(false)
  const sessions = ref<AssistantSession[]>([])
  const activeSessionId = ref<number | null>(null)
  const messages = ref<AssistantMessage[]>([])
  const sending = ref(false)

  async function loadSessions() {
    const data = await getAssistantSessions() as unknown as AssistantSession[]
    const allSessions = Array.isArray(data) ? data : []
    sessions.value = allSessions.filter(item => !isEmptyDefaultSession(item))
    initialized.value = true

    if (activeSessionId.value && sessions.value.some(item => item.id === activeSessionId.value)) {
      return sessions.value
    }

    if (sessions.value.length > 0) {
      activeSessionId.value = sessions.value[0].id
    } else {
      activeSessionId.value = null
      messages.value = []
    }
    return sessions.value
  }

  async function createSession(title?: string) {
    const created = await createAssistantSession(title ? { title } : {}) as unknown as AssistantSession
    sessions.value = [created, ...sessions.value.filter(item => item.id !== created.id)]
    activeSessionId.value = created.id
    messages.value = []
    return created
  }

  async function ensureSession() {
    if (activeSessionId.value) {
      return activeSessionId.value
    }

    if (!initialized.value) {
      await loadSessions()
    }

    if (activeSessionId.value) {
      return activeSessionId.value
    }

    const created = await createSession()
    return created.id
  }

  async function loadMessages(sessionId: number) {
    const data = await getAssistantMessages(sessionId) as unknown as AssistantMessage[]
    messages.value = Array.isArray(data) ? data : []
    activeSessionId.value = sessionId
    return messages.value
  }

  async function selectSession(sessionId: number) {
    if (activeSessionId.value === sessionId && messages.value.length > 0) {
      return
    }
    await loadMessages(sessionId)
  }

  async function removeSession(sessionId: number) {
    await deleteAssistantSession(sessionId)
    sessions.value = sessions.value.filter(item => item.id !== sessionId)

    if (activeSessionId.value === sessionId) {
      activeSessionId.value = sessions.value[0]?.id ?? null
      if (activeSessionId.value) {
        await loadMessages(activeSessionId.value)
      } else {
        messages.value = []
      }
    }
  }

  function open() {
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  function toggle() {
    visible.value = !visible.value
  }

  function startNewSession() {
    activeSessionId.value = null
    messages.value = []
    initialized.value = true
  }

  function updateSessionOptimistically(sessionId: number, content: string) {
    const index = sessions.value.findIndex(item => item.id === sessionId)
    if (index < 0) return

    const current = sessions.value[index]
    const now = new Date().toISOString()
    const updated: AssistantSession = {
      ...current,
      title: isDefaultSessionTitle(current.title) ? buildSessionTitleCandidate(content) : current.title,
      lastMessagePreview: buildMessagePreview(content),
      lastMessageAt: now,
      updatedAt: now,
    }

    sessions.value = [
      updated,
      ...sessions.value.filter(item => item.id !== sessionId),
    ]
  }

  async function sendMessage(request: AssistantChatRequest) {
    const content = request.message?.trim()
    if (!content) {
      return
    }
    if (sending.value) {
      return
    }

    const sessionId = await ensureSession()
    sending.value = true

    const userTempId = buildTempId('user')
    const assistantTempId = buildTempId('assistant')
    const createdAt = new Date().toISOString()

    updateSessionOptimistically(sessionId, content)

    messages.value.push({
      id: userTempId,
      sessionId,
      role: 'user',
      content,
      status: 'completed',
      pageContext: request.pageContext,
      actions: [],
      sources: [],
      createdAt,
    })
    messages.value.push({
      id: assistantTempId,
      sessionId,
      role: 'assistant',
      content: '',
      status: 'streaming',
      actions: [],
      sources: [],
      createdAt,
    })

    try {
      await streamAssistantMessage(sessionId, request, {
        onActions(payload) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.intent = payload.intent || null
          target.actions = payload.actions || []
          target.sources = payload.sources || []
        },
        onDelta(delta) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.content += delta
        },
        onDone(message) {
          const index = messages.value.findIndex(item => String(item.id) === assistantTempId)
          if (index >= 0) {
            messages.value.splice(index, 1, message)
          }
          sending.value = false
        },
        onError(message) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (target) {
            target.status = 'failed'
            if (!target.content) {
              target.content = message
            }
          }
          sending.value = false
        },
      })
    } catch (error) {
      const message = error instanceof Error ? error.message : '操作助手请求失败'
      const target = messages.value.find(item => String(item.id) === assistantTempId)
      if (target) {
        target.status = 'failed'
        target.content = target.content || message
      }
      ElMessage.error(message)
    } finally {
      sending.value = false
      await loadSessions()
      if (activeSessionId.value === sessionId) {
        await loadMessages(sessionId)
      }
    }
  }

  return {
    visible,
    initialized,
    sessions,
    activeSessionId,
    messages,
    sending,
    open,
    close,
    toggle,
    startNewSession,
    loadSessions,
    loadMessages,
    selectSession,
    createSession,
    ensureSession,
    removeSession,
    sendMessage,
  }
})
