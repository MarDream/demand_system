import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createAssistantSession,
  deleteAssistantSession,
  getAssistantMessages,
  getAssistantSessions,
  regenerateAssistantMessage,
  streamAssistantMessage,
} from '@/api/modules/assistant'
import type { AssistantChatRequest, AssistantMessage, AssistantMessageId, AssistantSession, AssistantTask } from '@/types/assistant'

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
  let abortController: AbortController | null = null

  function stopGenerating() {
    abortController?.abort()
    abortController = null
  }

  function applyUsage(target: AssistantMessage, usage?: { inputTokens?: number | null, outputTokens?: number | null, totalTokens?: number | null }) {
    if (!usage || !target) return
    if (usage.inputTokens != null) target.inputTokens = usage.inputTokens
    if (usage.outputTokens != null) target.outputTokens = usage.outputTokens
    if (usage.totalTokens != null) target.totalTokens = usage.totalTokens
  }

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

    abortController = new AbortController()
    try {
      await streamAssistantMessage(sessionId, request, {
        onActions(payload) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.intent = payload.intent || null
          target.actions = payload.actions || []
          target.sources = payload.sources || []
          target.tasks = payload.tasks || []
        },
        onThinkingSteps(steps) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.thinkingSteps = steps
        },
        onTaskUpdate(task) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          if (!target.tasks) target.tasks = []
          const idx = target.tasks.findIndex(t => t.id === task.id)
          if (idx >= 0) {
            target.tasks[idx] = task
          } else {
            target.tasks.push(task)
          }
          // 触发响应式更新
          target.tasks = [...target.tasks]
        },
        onDelta(delta) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.content += delta
        },
        onReasoningDelta(delta) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.reasoning = (target.reasoning || '') + delta
        },
        onReasoning(content) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.reasoning = content
        },
        onUsage(usage) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          applyUsage(target, usage)
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
      }, abortController.signal)
    } catch (error) {
      const isAborted = error instanceof DOMException && error.name === 'AbortError'
      const target = messages.value.find(item => String(item.id) === assistantTempId)
      if (target) {
        target.status = isAborted ? 'completed' : 'failed'
        if (!target.content) {
          target.content = isAborted ? '' : (error instanceof Error ? error.message : '操作助手请求失败')
        }
      }
      if (!isAborted) {
        ElMessage.error(error instanceof Error ? error.message : '操作助手请求失败')
      }
    } finally {
      abortController = null
      sending.value = false
      await loadSessions()
      if (activeSessionId.value === sessionId) {
        await loadMessages(sessionId)
      }
    }
  }

  async function regenerateMessage(assistantMessageId: AssistantMessageId, request: AssistantChatRequest) {
    if (sending.value) {
      return
    }
    const sessionId = activeSessionId.value
    if (sessionId == null) {
      return
    }

    sending.value = true
    const index = messages.value.findIndex(item => String(item.id) === String(assistantMessageId))
    const oldMessage = messages.value[index]
    if (!oldMessage) {
      sending.value = false
      return
    }
    // 目标消息原地进入流式态，新内容覆盖旧内容
    oldMessage.status = 'streaming'
    oldMessage.content = ''
    oldMessage.reasoning = null
    oldMessage.thinkingSteps = []
    oldMessage.actions = []
    oldMessage.sources = []
    oldMessage.inputTokens = null
    oldMessage.outputTokens = null
    oldMessage.totalTokens = null

    abortController = new AbortController()
    try {
      await regenerateAssistantMessage(sessionId, {
        message: request.message,
        pageContext: request.pageContext,
        knowledgeBaseId: request.knowledgeBaseId,
        llmModelId: request.llmModelId,
        mode: request.mode,
        topK: request.topK,
        webSearch: request.webSearch,
        files: request.files,
        assistantMessageId,
      }, {
        onActions(payload) {
          oldMessage.intent = payload.intent || null
          oldMessage.actions = payload.actions || []
          oldMessage.sources = payload.sources || []
          oldMessage.tasks = payload.tasks || []
        },
        onThinkingSteps(steps) {
          oldMessage.thinkingSteps = steps
        },
        onTaskUpdate(task) {
          if (!oldMessage.tasks) oldMessage.tasks = []
          const idx = oldMessage.tasks.findIndex(t => t.id === task.id)
          if (idx >= 0) {
            oldMessage.tasks[idx] = task
          } else {
            oldMessage.tasks.push(task)
          }
          oldMessage.tasks = [...oldMessage.tasks]
        },
        onDelta(delta) {
          oldMessage.content += delta
        },
        onReasoningDelta(delta) {
          oldMessage.reasoning = (oldMessage.reasoning || '') + delta
        },
        onReasoning(content) {
          oldMessage.reasoning = content
        },
        onUsage(usage) {
          applyUsage(oldMessage, usage)
        },
        onDone(message) {
          const doneIndex = messages.value.findIndex(item => String(item.id) === String(assistantMessageId))
          if (doneIndex >= 0) {
            messages.value.splice(doneIndex, 1, message)
          }
          sending.value = false
        },
        onError(message) {
          oldMessage.status = 'failed'
          if (!oldMessage.content) {
            oldMessage.content = message
          }
          sending.value = false
        },
      }, abortController.signal)
    } catch (error) {
      const isAborted = error instanceof DOMException && error.name === 'AbortError'
      oldMessage.status = isAborted ? 'completed' : 'failed'
      if (!oldMessage.content && !isAborted) {
        oldMessage.content = error instanceof Error ? error.message : '重新生成失败'
      }
      if (!isAborted) {
        ElMessage.error(error instanceof Error ? error.message : '重新生成失败')
      }
    } finally {
      abortController = null
      sending.value = false
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
    regenerateMessage,
    stopGenerating,
  }
})
