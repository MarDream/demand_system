import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmAssistantToolCall,
  createAssistantSession,
  deleteAssistantSession,
  getAssistantMessages,
  getAssistantSessions,
  regenerateAssistantMessage,
  streamAssistantMessage,
} from '@/api/modules/assistant'
import type { AssistantChatRequest, AssistantMessage, AssistantMessageId, AssistantSession, AssistantTask, ConversationTurn, SubAgentRun, ToolCallRecord } from '@/types/assistant'

const DEFAULT_SESSION_TITLE = '新会话'

/** AI 助手浮标拖拽位置的 localStorage 键；登录成功时清除，使浮标回到左下角默认位 */
export const ASSISTANT_FAB_POSITION_KEY = 'assistant-fab-position'

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

/** 单条历史注入上限（字符），与后端截断口径一致即可 */
const HISTORY_MAX_TURN_CHARS = 500
/** 随请求携带的最大历史条数（约 3 轮） */
const HISTORY_MAX_TURNS = 6

/**
 * 从会话消息中组装多轮对话历史（旧→新），供后端做查询改写与回答上下文。
 * 仅保留成功完成的发言，过滤空内容与流式中的临时消息。
 */
function buildHistory(messages: AssistantMessage[], beforeId?: AssistantMessageId): ConversationTurn[] {
  const usable = messages.filter((item) => {
    if (item.role !== 'user' && item.role !== 'assistant') return false
    if (beforeId != null && String(item.id) === String(beforeId)) return false
    if (item.status === 'streaming' || item.status === 'failed') return false
    return !!item.content?.trim()
  })
  const scoped = beforeId != null
    ? usable.slice(0, Math.max(0, usable.findIndex((item) => String(item.id) === String(beforeId))))
    : usable
  return scoped
    .slice(-HISTORY_MAX_TURNS)
    .map((item) => ({
      role: item.role as 'user' | 'assistant',
      content: item.content.trim().slice(0, HISTORY_MAX_TURN_CHARS),
    }))
}

/**
 * 弹框窗口模式：
 * - normal：浏览器居中浮动（默认，打开时由 SystemAssistant 居中定位，可拖动）
 * - maximized：全屏铺满 viewport
 * - minimized：缩成右下角迷你条，仅展示头像 + 标题 + 还原/关闭按钮
 */
export type AssistantWindowMode = 'normal' | 'maximized' | 'minimized'

export const useAssistantStore = defineStore('assistant', () => {
  const visible = ref(false)
  /** 窗口模式：仅当 visible=true 时生效，关闭后会被重置回 'normal' */
  const windowMode = ref<AssistantWindowMode>('normal')
  const initialized = ref(false)
  const sessions = ref<AssistantSession[]>([])
  const activeSessionId = ref<number | null>(null)
  const messages = ref<AssistantMessage[]>([])
  const sending = ref(false)
  /** 智能体模式：固定 auto——后端按问题特征智能路由，复杂问题自动进入智能体编排（前端不提供切换） */
  const agentMode = ref<'auto' | 'on' | 'off'>('auto')
  let abortController: AbortController | null = null

  function stopGenerating() {
    abortController?.abort()
    abortController = null
  }

  /** 确保消息上有智能体轨迹对象（流式事件到达时惰性创建） */
  function ensureAgentTrace(target: AssistantMessage) {
    if (!target.agentTrace) {
      target.agentTrace = { subAgents: [], toolCalls: [] }
    }
    if (!target.agentTrace.subAgents) target.agentTrace.subAgents = []
    if (!target.agentTrace.toolCalls) target.agentTrace.toolCalls = []
    return target.agentTrace
  }

  function upsertById<T extends { id: string }>(list: T[] | undefined, item: T): T[] {
    const base = list ? [...list] : []
    const idx = base.findIndex(existing => existing.id === item.id)
    if (idx >= 0) {
      base[idx] = item
    } else {
      base.push(item)
    }
    return base
  }

  /** 工具调用记录归属：主循环进 trace.toolCalls，子 Agent 进对应 run 的 toolCalls */
  function applyToolCall(target: AssistantMessage, record: ToolCallRecord) {
    const trace = ensureAgentTrace(target)
    if (record.agentId && record.agentId !== 'main') {
      const runs = trace.subAgents!
      const runIdx = runs.findIndex(run => run.id === record.agentId)
      if (runIdx >= 0) {
        runs[runIdx] = { ...runs[runIdx], toolCalls: upsertById(runs[runIdx].toolCalls, record) }
        trace.subAgents = [...runs]
        return
      }
    }
    trace.toolCalls = upsertById(trace.toolCalls, record)
  }

  function applySubAgent(target: AssistantMessage, run: SubAgentRun) {
    const trace = ensureAgentTrace(target)
    trace.subAgents = upsertById(trace.subAgents, run)
  }

  /** 用户点击确认卡片：回填决议并从待确认列表移除 */
  async function confirmPermission(requestId: string, approved: boolean) {
    try {
      await confirmAssistantToolCall(requestId, approved)
    } finally {
      const target = [...messages.value].reverse()
        .find(item => item.pendingPermissions?.some(entry => entry.requestId === requestId))
      if (target && target.pendingPermissions) {
        target.pendingPermissions = target.pendingPermissions.filter(entry => entry.requestId !== requestId)
      }
    }
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

    // 仅刷新会话列表，不自动选中会话：
    // 「打开助手 = 新会话」由 open() 的 startNewSession 保证，选中历史会话只能由用户显式触发。
    if (activeSessionId.value && !sessions.value.some(item => item.id === activeSessionId.value)) {
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
    // 打开即新会话：无选中会话时直接创建，绝不回落到历史会话，
    // 避免新提问混入旧对话的上下文。
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

  /** 批量删除会话：逐个调用删除接口，一次性更新会话列表与激活会话指向 */
  async function removeSessions(sessionIds: number[]) {
    const idSet = new Set(sessionIds)
    if (idSet.size === 0) return
    for (const sessionId of sessionIds) {
      await deleteAssistantSession(sessionId)
    }
    sessions.value = sessions.value.filter(item => !idSet.has(item.id))

    if (activeSessionId.value && idSet.has(activeSessionId.value)) {
      activeSessionId.value = sessions.value[0]?.id ?? null
      if (activeSessionId.value) {
        await loadMessages(activeSessionId.value)
      } else {
        messages.value = []
      }
    }
  }

  function open() {
    // 每次打开都进入全新会话，不停留在关闭前的对话中；
    // 会话记录发送首条消息时才真正创建（见 ensureSession）。
    startNewSession()
    visible.value = true
    // 始终以 normal 形态唤起，避免上一次遗留的 minimized/maximized 干扰
    windowMode.value = 'normal'
  }

  function close() {
    visible.value = false
    // 关闭即重置窗口模式，下次打开从 normal 起步
    windowMode.value = 'normal'
  }

  function toggle() {
    if (visible.value) {
      close()
      return
    }
    open()
  }

  /** 缩为右下角迷你条；如未在 visible 态则一并打开 */
  function minimize() {
    if (!visible.value) {
      open()
    }
    windowMode.value = 'minimized'
  }

  /** 切到全屏（如未打开则一并打开） */
  function maximize() {
    if (!visible.value) {
      open()
    }
    windowMode.value = 'maximized'
  }

  /** 全屏 ↔ 普通（不含最小化） */
  function toggleMaximize() {
    if (!visible.value) {
      open()
      return
    }
    windowMode.value = windowMode.value === 'maximized' ? 'normal' : 'maximized'
  }

  /** 从最小化 / 全屏还原为普通浮动窗口 */
  function restoreMode() {
    if (!visible.value) {
      open()
      return
    }
    windowMode.value = 'normal'
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
    const history = buildHistory(messages.value)

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
      await streamAssistantMessage(sessionId, { ...request, agentMode: agentMode.value, history }, {
        onActions(payload) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.intent = payload.intent || null
          target.actions = payload.actions || []
          target.sources = payload.sources || []
          target.tasks = payload.tasks || []
          target.warnings = payload.warnings || []
          // 角标来源在回答开始流式推送前下发，先落库再收 delta，正文 [N] 才能即时渲染成角标
          if (payload.citations) {
            target.citations = payload.citations
          }
          if (payload.dataResult) {
            target.dataResult = payload.dataResult
          }
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
        onDataResult(result) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.dataResult = result
        },
        onPlan(payload) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          target.agentPlan = payload.plan
          const trace = ensureAgentTrace(target)
          trace.plan = payload.plan
          trace.routeReason = payload.routeReason || trace.routeReason
        },
        onToolCall(record) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          applyToolCall(target, record)
        },
        onSubAgent(run) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          applySubAgent(target, run)
        },
        onPermissionRequest(payload) {
          const target = messages.value.find(item => String(item.id) === assistantTempId)
          if (!target) return
          if (!target.pendingPermissions) target.pendingPermissions = []
          if (!target.pendingPermissions.some(entry => entry.requestId === payload.requestId)) {
            target.pendingPermissions = [...target.pendingPermissions, payload]
          }
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
    oldMessage.warnings = []
    oldMessage.dataResult = null
    oldMessage.agentPlan = null
    oldMessage.agentTrace = null
    oldMessage.pendingPermissions = []
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
        searchScopes: request.searchScopes,
        dataQuery: request.dataQuery,
        agentMode: agentMode.value,
        files: request.files,
        history: buildHistory(messages.value, assistantMessageId),
        assistantMessageId,
      }, {
        onActions(payload) {
          oldMessage.intent = payload.intent || null
          oldMessage.actions = payload.actions || []
          oldMessage.sources = payload.sources || []
          oldMessage.tasks = payload.tasks || []
          oldMessage.warnings = payload.warnings || []
          if (payload.citations) {
            oldMessage.citations = payload.citations
          }
          if (payload.dataResult) {
            oldMessage.dataResult = payload.dataResult
          }
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
        onDataResult(result) {
          oldMessage.dataResult = result
        },
        onPlan(payload) {
          oldMessage.agentPlan = payload.plan
          const trace = ensureAgentTrace(oldMessage)
          trace.plan = payload.plan
          trace.routeReason = payload.routeReason || trace.routeReason
        },
        onToolCall(record) {
          applyToolCall(oldMessage, record)
        },
        onSubAgent(run) {
          applySubAgent(oldMessage, run)
        },
        onPermissionRequest(payload) {
          if (!oldMessage.pendingPermissions) oldMessage.pendingPermissions = []
          if (!oldMessage.pendingPermissions.some(entry => entry.requestId === payload.requestId)) {
            oldMessage.pendingPermissions = [...oldMessage.pendingPermissions, payload]
          }
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
    windowMode,
    initialized,
    sessions,
    activeSessionId,
    messages,
    sending,
    open,
    close,
    toggle,
    minimize,
    maximize,
    toggleMaximize,
    restoreMode,
    startNewSession,
    loadSessions,
    loadMessages,
    selectSession,
    createSession,
    ensureSession,
    removeSession,
    removeSessions,
    sendMessage,
    regenerateMessage,
    stopGenerating,
    agentMode,
    confirmPermission,
  }
})
