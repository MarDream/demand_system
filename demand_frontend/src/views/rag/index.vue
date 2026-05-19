<template>
  <PageContainer
    title="文档检索工作台"
    subtitle="按知识库组织问答记录、管理上下文，并在回答后直接查看命中文件证据"
  >
    <template #headerActions>
      <el-button @click="refreshKnowledgeBases" :loading="refreshing">
        <el-icon><RefreshRight /></el-icon>
        <span>刷新知识库</span>
      </el-button>
      <el-button plain @click="goToModelCenter">模型配置</el-button>
      <el-button type="primary" plain @click="goToKnowledgeManagement">管理知识库</el-button>
    </template>

    <div class="rag-workspace">
      <aside class="rag-shell rag-sidebar">
        <section class="sidebar-section">
          <div class="section-heading">
            <div>
              <div class="section-label">可选知识库</div>
              <div class="section-tip">每个知识库维护独立会话与上下文</div>
            </div>
            <div class="section-badge">{{ store.knowledgeBases.length }}</div>
          </div>

          <div v-if="store.knowledgeBases.length" class="knowledge-grid">
            <button
              v-for="kb in store.knowledgeBases"
              :key="kb.id"
              type="button"
              class="knowledge-card"
              :class="{ 'knowledge-card--active': selectedKbId === kb.id }"
              @click="handleSelectKnowledgeBase(kb.id)"
            >
              <div class="knowledge-card__title">
                <span>{{ kb.name }}</span>
                <el-tag size="small" :type="kb.status === 'active' ? 'success' : 'info'">
                  {{ kb.status === 'active' ? '活跃' : '归档' }}
                </el-tag>
              </div>
              <div class="knowledge-card__desc">{{ kb.description || '暂无描述，适合作为当前知识空间的入口。' }}</div>
              <div class="knowledge-card__meta">
                <span>{{ kb.docCount }} 份文档</span>
                <span>{{ kb.chunkCount }} 个分块</span>
              </div>
            </button>
          </div>

          <el-empty v-else description="暂无知识库，请先在知识库管理中创建" />
        </section>

        <section v-if="selectedKnowledgeBase" class="sidebar-section sidebar-section--fill">
          <div class="section-heading">
            <div>
              <div class="section-label">对话记录</div>
              <div class="section-tip">{{ selectedKnowledgeBase.name }} 的历史问答会保存在本地</div>
            </div>
            <el-button type="primary" text @click="createSessionForCurrentKb">
              <el-icon><Plus /></el-icon>
              <span>新建</span>
            </el-button>
          </div>

          <div v-if="sessionsForSelectedKb.length" class="session-list">
            <button
              v-for="session in sessionsForSelectedKb"
              :key="session.id"
              type="button"
              class="session-item"
              :class="{ 'session-item--active': activeSessionId === session.id }"
              @click="activeSessionId = session.id"
            >
              <div class="session-item__main">
                <div class="session-item__title">{{ session.title }}</div>
                <div class="session-item__meta">
                  <span>{{ formatDateTime(session.updatedAt) }}</span>
                  <span>{{ session.messages.length }} 条消息</span>
                </div>
                <div class="session-item__context">
                  {{ session.contextEnabled ? `上下文 ${session.contextTurns} 轮` : '单轮检索' }}
                </div>
              </div>
              <el-button text type="danger" @click.stop="handleDeleteSession(session)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </button>
          </div>

          <el-empty v-else description="当前知识库还没有会话" />
        </section>
      </aside>

      <section class="rag-shell rag-chat">
        <header class="chat-header">
          <div class="chat-title">
            <div class="chat-title__label">检索问答</div>
            <div class="chat-title__main">{{ selectedKnowledgeBase?.name || '请选择知识库' }}</div>
          </div>
          <div class="chat-filters">
            <el-select v-model="searchMode" size="small" style="width: 124px">
              <el-option label="混合模式" value="hybrid" />
              <el-option label="语义检索" value="semantic" />
              <el-option label="关键词" value="keyword" />
            </el-select>
            <el-select v-model="topK" size="small" style="width: 96px">
              <el-option :value="5" label="Top 5" />
              <el-option :value="10" label="Top 10" />
              <el-option :value="20" label="Top 20" />
            </el-select>
            <el-button
              v-if="selectedKnowledgeBase"
              text
              @click="goToKnowledgeDetail(selectedKnowledgeBase.id)"
            >
              查看文档
            </el-button>
          </div>
        </header>

        <div class="chat-stream">
          <template v-if="activeSession && activeSession.messages.length">
            <div
              v-for="message in activeSession.messages"
              :key="message.id"
              class="message-row"
              :class="[`message-row--${message.role}`]"
            >
              <div
                class="message-bubble"
                :class="{
                  'message-bubble--assistant-active': message.role === 'assistant' && activeInsightMessageId === message.id,
                  'message-bubble--error': message.failed
                }"
                @click="handleSelectInsight(message)"
              >
                <div class="message-bubble__role">
                  {{ message.role === 'user' ? '提问' : '检索回答' }}
                </div>
                <div class="message-bubble__content">{{ message.content }}</div>

                <div v-if="message.role === 'assistant'" class="message-bubble__footer">
                  <span>{{ message.citations?.length || 0 }} 份证据文件</span>
                  <span>{{ message.retrievedCount || 0 }} 条命中</span>
                  <span v-if="message.llmModelLabel">模型：{{ message.llmModelLabel }}</span>
                  <span>{{ formatDateTime(message.createdAt) }}</span>
                </div>
              </div>
            </div>
          </template>

          <div v-else class="chat-empty">
            <div class="chat-empty__title">从知识库开始一次结构化问答</div>
            <div class="chat-empty__desc">
              选择左侧知识库后输入问题，系统会保存该知识库下的会话记录，并在回答后展示关键点和涉及文件。
            </div>
          </div>

          <div v-if="asking" class="message-row message-row--assistant">
            <div class="message-bubble message-bubble--loading">
              <div class="message-bubble__role">检索中</div>
              <div class="thinking-loader">
                <span class="thinking-loader__dot"></span>
                <span class="thinking-loader__dot"></span>
                <span class="thinking-loader__dot"></span>
              </div>
              <div class="message-bubble__hint">正在整理问题、召回文档并汇总答案...</div>
            </div>
          </div>
        </div>

        <footer class="composer-panel">
          <div class="composer-shell" :class="{ 'composer-shell--disabled': !selectedKnowledgeBase }">
            <div class="composer-shell__body">
              <el-input
                v-model="draftQuestion"
                type="textarea"
                resize="none"
                :autosize="{ minRows: 4, maxRows: 8 }"
                class="composer-input"
                :disabled="!selectedKnowledgeBase || asking"
                placeholder="请输入你想在当前知识库中检索的问题，按 Enter 发送，Shift + Enter 换行"
                @keydown.enter.exact.prevent="handleAsk"
              />
            </div>

            <div class="composer-bottom">
              <div class="composer-bottom__left">
                <button
                  type="button"
                  class="composer-ghost-action"
                  :disabled="!activeSession || asking"
                  @click="handleClearSession"
                >
                  清空对话
                </button>
                <span class="composer-tipline">{{ composerTip }}</span>
              </div>

              <div class="composer-bottom__right">
                <el-popover
                  v-model:visible="contextPopoverVisible"
                  trigger="click"
                  placement="top-start"
                  width="260"
                  popper-class="rag-composer-popover"
                >
                  <template #reference>
                    <button
                      type="button"
                      class="composer-pill composer-pill--interactive"
                      :disabled="!activeSession || asking"
                    >
                      <span>{{ contextDisplayLabel }}</span>
                      <el-icon><ArrowDown /></el-icon>
                    </button>
                  </template>

                  <div class="composer-menu">
                    <div class="composer-menu__title">上下文</div>
                    <button
                      v-for="option in contextOptions"
                      :key="option.value"
                      type="button"
                      class="composer-menu__item"
                      :class="{ 'composer-menu__item--active': contextDisplayLabel === (option.value === 'off' ? '单轮' : `上下文 ${option.value} 轮`) }"
                      @click="applyContextOption(option.value)"
                    >
                      <div>
                        <div class="composer-menu__label">{{ option.label }}</div>
                        <div class="composer-menu__hint">{{ option.hint }}</div>
                      </div>
                      <span class="composer-menu__check">{{ contextDisplayLabel === (option.value === 'off' ? '单轮' : `上下文 ${option.value} 轮`) ? '✓' : '' }}</span>
                    </button>
                  </div>
                </el-popover>

                <span class="composer-provider-badge">
                  {{ selectedProviderName }}
                </span>

                <el-popover
                  v-model:visible="modelPopoverVisible"
                  trigger="click"
                  placement="top-end"
                  width="320"
                  popper-class="rag-composer-popover"
                >
                  <template #reference>
                    <button
                      type="button"
                      class="composer-pill composer-pill--interactive composer-pill--model"
                      :disabled="!availableChatModels.length || asking"
                    >
                      <span>{{ selectedModelDisplay }}</span>
                      <el-icon><ArrowDown /></el-icon>
                    </button>
                  </template>

                  <div class="composer-menu">
                    <div class="composer-menu__title">模型</div>
                    <div
                      v-for="group in groupedChatModels"
                      :key="group.providerName"
                      class="composer-menu__group"
                    >
                      <div class="composer-menu__group-label">{{ group.providerName }}</div>
                      <button
                        v-for="model in group.items"
                        :key="model.id"
                        type="button"
                        class="composer-menu__item"
                        :class="{ 'composer-menu__item--active': selectedLlmModelId === model.id }"
                        @click="handleModelSelect(model.id)"
                      >
                        <div>
                          <div class="composer-menu__label">{{ model.name }}</div>
                          <div class="composer-menu__hint">{{ model.modelId }}</div>
                        </div>
                        <span class="composer-menu__check">{{ selectedLlmModelId === model.id ? '✓' : '' }}</span>
                      </button>
                    </div>
                  </div>
                </el-popover>

                <button
                  type="button"
                  class="composer-send"
                  :disabled="!selectedKnowledgeBase || asking"
                  @click="handleAsk"
                >
                  <span class="composer-send__icon">{{ asking ? '...' : '↑' }}</span>
                </button>
              </div>
            </div>
          </div>
        </footer>
      </section>

      <aside class="rag-shell rag-insights">
        <template v-if="currentInsight">
          <section class="insight-card insight-card--summary">
            <div class="insight-card__header">
              <div>
                <div class="insight-card__label">关键问题总结</div>
                <div class="insight-card__title">本轮输出概览</div>
              </div>
              <div class="insight-card__tags">
                <el-tag v-if="currentInsight.llmModelLabel" effect="dark" type="success">{{ currentInsight.llmModelLabel }}</el-tag>
                <el-tag effect="dark" type="info">{{ currentInsight.retrievedCount || 0 }} 条片段</el-tag>
              </div>
            </div>
            <div class="summary-content">{{ currentInsight.processSummary || currentInsight.content }}</div>
          </section>

          <section class="insight-card">
            <div class="insight-card__header">
              <div>
                <div class="insight-card__label">模型思考摘要</div>
                <div class="insight-card__title">可审阅的检索路径</div>
              </div>
            </div>
            <div class="thinking-list">
              <div v-for="(step, index) in currentInsight.thinkingSteps || []" :key="step.title" class="thinking-item">
                <div class="thinking-item__index">{{ index + 1 }}</div>
                <div class="thinking-item__body">
                  <div class="thinking-item__title">{{ step.title }}</div>
                  <div class="thinking-item__detail">{{ step.detail }}</div>
                </div>
              </div>
            </div>
          </section>

          <section class="insight-card">
            <div class="insight-card__header">
              <div>
                <div class="insight-card__label">关键点</div>
                <div class="insight-card__title">便于快速复盘</div>
              </div>
            </div>
            <ul class="keypoint-list">
              <li v-for="point in currentInsight.summaryPoints || []" :key="point">{{ point }}</li>
            </ul>
          </section>

          <section class="insight-card">
            <div class="insight-card__header">
              <div>
                <div class="insight-card__label">涉及文件</div>
                <div class="insight-card__title">点击可预览命中文档</div>
              </div>
            </div>

            <div v-if="currentInsight.citations?.length" class="citation-list">
              <button
                v-for="citation in currentInsight.citations"
                :key="`${citation.documentId}-${citation.fileName}`"
                type="button"
                class="citation-item"
                @click="openPreview(citation)"
              >
                <div class="citation-item__top">
                  <div class="citation-item__name">
                    <el-icon><Document /></el-icon>
                    <span>{{ citation.fileName }}</span>
                  </div>
                  <el-button text type="primary" @click.stop="openPreview(citation)">
                    <el-icon><View /></el-icon>
                    <span>预览</span>
                  </el-button>
                </div>
                <div class="citation-item__meta">
                  <span>{{ citation.hitCount }} 个片段</span>
                  <span v-if="citation.sectionTitle">{{ citation.sectionTitle }}</span>
                  <span>{{ citation.pageText }}</span>
                  <span>{{ Math.round(citation.score * 100) }}% 相关度</span>
                </div>
                <div class="citation-item__excerpt">
                  <HighlightText :content="citation.excerpt" :query="currentInsight.question || ''" />
                </div>
              </button>
            </div>

            <el-empty v-else description="当前回答未返回可预览文件" />
          </section>
        </template>

        <div v-else class="insight-empty">
          <div class="insight-empty__title">这里会展示思考摘要与证据文件</div>
          <div class="insight-empty__desc">
            完成一次检索后，你可以在这里查看总结、关键点，以及命中文件的在线预览入口。
          </div>
        </div>
      </aside>
    </div>

    <FilePreviewDialog
      v-model="previewVisible"
      :file-name="previewState.fileName"
      :file-type="previewState.fileType"
      :knowledge-base-id="previewState.knowledgeBaseId"
      :document-id="previewState.documentId"
      :download-url="previewState.downloadUrl"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Delete, Document, Plus, RefreshRight, View } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import HighlightText from '@/components/common/HighlightText.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import { llmProviderApi, type LlmModel, type LlmProvider } from '@/api/modules/llmProvider'
import { useKnowledgeStore } from '@/stores/knowledge'
import storage from '@/utils/storage'
import { formatDate } from '@/utils/format'
import type { KnowledgeBase, SearchMode, SearchResponse, SearchResultItem } from '@/api/modules/knowledge'

interface RagThinkingStep {
  title: string
  detail: string
}

interface RagCitation {
  knowledgeBaseId: number
  documentId: number
  fileName: string
  fileType: string
  sectionTitle?: string
  excerpt: string
  score: number
  hitCount: number
  pageText: string
}

interface RagModelOption {
  id: number
  providerId: number
  providerName: string
  name: string
  label: string
  modelId: string
  modelType: string
  isDefault: boolean
}

interface RagMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: number
  failed?: boolean
  question?: string
  processSummary?: string
  summaryPoints?: string[]
  thinkingSteps?: RagThinkingStep[]
  citations?: RagCitation[]
  retrievedCount?: number
  llmModelId?: number | null
  llmModelLabel?: string | null
}

interface RagSession {
  id: string
  knowledgeBaseId: number
  title: string
  createdAt: number
  updatedAt: number
  contextEnabled: boolean
  contextTurns: number
  messages: RagMessage[]
}

interface RagWorkspaceState {
  selectedKbId: number | null
  activeSessionId: string | null
  selectedLlmModelId?: number | null
  sessions: RagSession[]
}

const RAG_WORKSPACE_STORAGE_KEY = 'rag-workspace-state-v1'

const router = useRouter()
const store = useKnowledgeStore()

const sessions = ref<RagSession[]>([])
const selectedKbId = ref<number | null>(null)
const activeSessionId = ref<string | null>(null)
const activeInsightMessageId = ref<string | null>(null)
const selectedLlmModelId = ref<number | null>(null)
const draftQuestion = ref('')
const searchMode = ref<SearchMode>('hybrid')
const topK = ref(10)
const asking = ref(false)
const refreshing = ref(false)
const availableChatModels = ref<RagModelOption[]>([])

const previewVisible = ref(false)
const previewState = ref({
  knowledgeBaseId: 0,
  documentId: 0,
  fileName: '',
  fileType: '',
  downloadUrl: ''
})

const selectedKnowledgeBase = computed<KnowledgeBase | null>(() => {
  return store.knowledgeBases.find(item => item.id === selectedKbId.value) || null
})

const selectedChatModel = computed<RagModelOption | null>(() => {
  if (selectedLlmModelId.value == null) return null
  return availableChatModels.value.find(item => item.id === selectedLlmModelId.value) || null
})

const contextPopoverVisible = ref(false)
const modelPopoverVisible = ref(false)

const contextOptions = [
  { value: 'off', label: '单轮检索', hint: '不携带历史上下文，适合独立问题' },
  { value: '1', label: '最近 1 轮', hint: '轻量保留上轮语义' },
  { value: '2', label: '最近 2 轮', hint: '适合连续追问，响应更稳妥' },
  { value: '3', label: '最近 3 轮', hint: '保留更多上下文细节' },
  { value: '5', label: '最近 5 轮', hint: '适合复杂多轮分析' }
]

const groupedChatModels = computed(() => {
  const groups = new Map<string, RagModelOption[]>()
  availableChatModels.value.forEach((model) => {
    const providerName = model.providerName || '未命名提供商'
    if (!groups.has(providerName)) {
      groups.set(providerName, [])
    }
    groups.get(providerName)!.push(model)
  })
  return Array.from(groups.entries()).map(([providerName, items]) => ({
    providerName,
    items
  }))
})

const contextDisplayLabel = computed(() => {
  const session = activeSession.value
  if (!session || !session.contextEnabled) return '单轮'
  return `上下文 ${session.contextTurns} 轮`
})

const selectedProviderName = computed(() => {
  return selectedChatModel.value?.providerName || '未配置提供商'
})

const selectedModelDisplay = computed(() => {
  if (!selectedChatModel.value) {
    return availableChatModels.value.length ? '选择模型' : '未配置模型'
  }
  return compactModelName(selectedChatModel.value)
})

const composerTip = computed(() => {
  const contextText = activeSession.value?.contextEnabled
    ? `会携带最近 ${activeSession.value.contextTurns} 轮上下文增强检索语义`
    : '当前按单轮问答检索'

  if (selectedChatModel.value) {
    return `${contextText}，回答模型为 ${selectedChatModel.value.label}`
  }

  if (availableChatModels.value.length === 0) {
    return `${contextText}，当前未加载到可用问答模型，将只输出检索证据摘要`
  }

  return `${contextText}，如需大模型生成回答，请先选择问答模型`
})

const sessionsForSelectedKb = computed(() => {
  if (!selectedKbId.value) return []
  return sessions.value
    .filter(session => session.knowledgeBaseId === selectedKbId.value)
    .sort((a, b) => b.updatedAt - a.updatedAt)
})

const activeSession = computed<RagSession | null>(() => {
  if (!activeSessionId.value) return null
  return sessions.value.find(session => session.id === activeSessionId.value) || null
})

const currentInsight = computed<RagMessage | null>(() => {
  const session = activeSession.value
  if (!session) return null
  if (activeInsightMessageId.value) {
    const found = session.messages.find(message => message.id === activeInsightMessageId.value && message.role === 'assistant')
    if (found) return found
  }
  return [...session.messages].reverse().find(message => message.role === 'assistant') || null
})

onMounted(async () => {
  restoreWorkspace()
  await Promise.all([store.fetchAllBases(), loadAvailableLlmModels()])
  bootstrapWorkspace()
})

watch(
  () => activeSession.value,
  (session) => {
    if (!session) {
      activeInsightMessageId.value = null
      return
    }
    const latestAssistant = [...session.messages].reverse().find(message => message.role === 'assistant')
    activeInsightMessageId.value = latestAssistant?.id || null
  },
  { immediate: true }
)

watch(
  [sessions, selectedKbId, activeSessionId, selectedLlmModelId],
  () => {
    persistWorkspace()
  },
  { deep: true }
)

function restoreWorkspace() {
  const state = storage.get<RagWorkspaceState>(RAG_WORKSPACE_STORAGE_KEY)
  if (!state) return
  sessions.value = Array.isArray(state.sessions) ? state.sessions : []
  selectedKbId.value = typeof state.selectedKbId === 'number' ? state.selectedKbId : null
  activeSessionId.value = state.activeSessionId || null
  selectedLlmModelId.value = typeof state.selectedLlmModelId === 'number' ? state.selectedLlmModelId : null
}

function bootstrapWorkspace() {
  if (!store.knowledgeBases.length) {
    selectedKbId.value = null
    activeSessionId.value = null
    return
  }

  const currentKbExists = store.knowledgeBases.some(item => item.id === selectedKbId.value)
  if (!currentKbExists) {
    selectedKbId.value = store.knowledgeBases[0].id
  }

  ensureActiveSession()
}

function persistWorkspace() {
  const payload: RagWorkspaceState = {
    selectedKbId: selectedKbId.value,
    activeSessionId: activeSessionId.value,
    selectedLlmModelId: selectedLlmModelId.value,
    sessions: sessions.value
  }
  storage.set(RAG_WORKSPACE_STORAGE_KEY, payload)
}

async function loadAvailableLlmModels() {
  try {
    const res = await llmProviderApi.list() as any
    const providers = (res?.data ?? res ?? []) as LlmProvider[]
    availableChatModels.value = providers
      .filter(provider => provider.enabled)
      .flatMap((provider) => {
        return (provider.models || [])
          .filter(model => model.enabled && isChatModel(model))
          .map((model) => toModelOption(provider, model))
      })
      .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.label.localeCompare(b.label, 'zh-CN'))

    if (!availableChatModels.value.length) {
      selectedLlmModelId.value = null
      return
    }

    const stillExists = availableChatModels.value.some(item => item.id === selectedLlmModelId.value)
    if (!stillExists) {
      selectedLlmModelId.value = availableChatModels.value.find(item => item.isDefault)?.id || availableChatModels.value[0].id
    }
  } catch {
    availableChatModels.value = []
    selectedLlmModelId.value = null
  }
}

function ensureActiveSession() {
  if (!selectedKbId.value) return
  const kbSessions = sessions.value.filter(session => session.knowledgeBaseId === selectedKbId.value)
  if (!kbSessions.length) {
    const session = createSession(selectedKbId.value)
    sessions.value = [session, ...sessions.value]
    activeSessionId.value = session.id
    return
  }

  const currentSession = kbSessions.find(session => session.id === activeSessionId.value)
  if (!currentSession) {
    activeSessionId.value = [...kbSessions].sort((a, b) => b.updatedAt - a.updatedAt)[0].id
  }
}

function createSession(knowledgeBaseId: number): RagSession {
  const now = Date.now()
  return {
    id: createId('session'),
    knowledgeBaseId,
    title: '新对话',
    createdAt: now,
    updatedAt: now,
    contextEnabled: true,
    contextTurns: 2,
    messages: []
  }
}

function createSessionForCurrentKb() {
  if (!selectedKbId.value) return
  const session = createSession(selectedKbId.value)
  sessions.value = [session, ...sessions.value]
  activeSessionId.value = session.id
  activeInsightMessageId.value = null
}

function handleSelectKnowledgeBase(knowledgeBaseId: number) {
  selectedKbId.value = knowledgeBaseId
  ensureActiveSession()
}

async function refreshKnowledgeBases() {
  refreshing.value = true
  try {
    await store.fetchAllBases()
    bootstrapWorkspace()
    ElMessage.success('知识库列表已刷新')
  } finally {
    refreshing.value = false
  }
}

function goToKnowledgeManagement() {
  router.push('/settings/knowledge')
}

function goToModelCenter() {
  router.push('/settings/llm')
}

function goToKnowledgeDetail(knowledgeBaseId: number) {
  router.push(`/settings/knowledge/${knowledgeBaseId}`)
}

async function handleDeleteSession(session: RagSession) {
  try {
    await ElMessageBox.confirm(`确定删除会话「${session.title}」？该知识库下保存的本地问答记录将被移除。`, '删除会话', {
      type: 'warning'
    })
    sessions.value = sessions.value.filter(item => item.id !== session.id)
    if (activeSessionId.value === session.id) {
      activeSessionId.value = null
      ensureActiveSession()
    }
    ElMessage.success('会话已删除')
  } catch {
    // 用户取消时保持原状态。
  }
}

async function handleClearSession() {
  const session = activeSession.value
  if (!session) return
  try {
    await ElMessageBox.confirm('确定清空当前会话上下文？已保存的问答记录会被删除，但知识库不会受影响。', '清空上下文', {
      type: 'warning'
    })
    session.messages = []
    session.title = '新对话'
    session.updatedAt = Date.now()
    activeInsightMessageId.value = null
    ElMessage.success('当前会话已清空')
  } catch {
    // 用户取消时保持原状态。
  }
}

function handleContextToggle(value: string | number | boolean) {
  if (!activeSession.value) return
  activeSession.value.contextEnabled = Boolean(value)
  activeSession.value.updatedAt = Date.now()
}

function handleContextTurnsChange(value: number) {
  if (!activeSession.value) return
  activeSession.value.contextTurns = value
  activeSession.value.updatedAt = Date.now()
}

function applyContextOption(value: string) {
  if (!activeSession.value) return

  if (value === 'off') {
    activeSession.value.contextEnabled = false
  } else {
    activeSession.value.contextEnabled = true
    activeSession.value.contextTurns = Number(value)
  }

  activeSession.value.updatedAt = Date.now()
  contextPopoverVisible.value = false
}

function handleModelSelect(modelId: number) {
  selectedLlmModelId.value = modelId
  modelPopoverVisible.value = false
}

function handleSelectInsight(message: RagMessage) {
  if (message.role !== 'assistant') return
  activeInsightMessageId.value = message.id
}

async function handleAsk() {
  const knowledgeBase = selectedKnowledgeBase.value
  const session = activeSession.value
  const question = draftQuestion.value.trim()

  if (!knowledgeBase) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!session) {
    ElMessage.warning('当前知识库没有可用会话，请新建一个会话后再提问')
    return
  }
  if (!question) {
    ElMessage.warning('请输入检索问题')
    return
  }

  const requestQuery = buildRequestQuery(session, question)
  const askedAt = Date.now()
  session.messages.push({
    id: createId('msg'),
    role: 'user',
    content: question,
    createdAt: askedAt
  })
  session.updatedAt = askedAt
  if (session.title === '新对话') {
    session.title = buildSessionTitle(question)
  }
  draftQuestion.value = ''
  asking.value = true

  try {
    const response = await store.search(
      requestQuery,
      searchMode.value,
      knowledgeBase.id,
      topK.value,
      selectedChatModel.value?.id
    )
    const assistantMessage: RagMessage = {
      id: createId('msg'),
      role: 'assistant',
      content: buildAnswerContent(response, question),
      createdAt: Date.now(),
      question,
      processSummary: buildProcessSummary(response, knowledgeBase.name),
      summaryPoints: extractSummaryPoints(response, question),
      thinkingSteps: buildThinkingSteps({
        knowledgeBaseName: knowledgeBase.name,
        question,
        session,
        mode: searchMode.value,
        response,
        llmModelLabel: selectedChatModel.value?.label || null
      }),
      citations: buildCitations(response?.results || [], question),
      retrievedCount: response?.total || response?.results?.length || 0,
      llmModelId: selectedChatModel.value?.id || null,
      llmModelLabel: selectedChatModel.value?.label || null
    }
    session.messages.push(assistantMessage)
    session.updatedAt = assistantMessage.createdAt
    activeInsightMessageId.value = assistantMessage.id
  } catch {
    const errorMessage: RagMessage = {
      id: createId('msg'),
      role: 'assistant',
      content: '本次检索未能完成，请检查模型配置、知识库索引状态或稍后重试。',
      createdAt: Date.now(),
      failed: true,
      question,
      processSummary: '检索请求失败，当前未返回有效的知识文件证据。',
      summaryPoints: ['请确认知识库已完成索引。', '请检查大模型与向量检索配置。', '如问题持续，请查看后台日志。'],
      thinkingSteps: [
        { title: '请求提交', detail: '已向检索服务提交本轮问题。' },
        { title: '服务异常', detail: '当前接口未返回有效结果，因此未生成文件证据与回答摘要。' },
        { title: '建议处理', detail: '优先确认模型、向量库和知识库文档状态是否正常。' }
      ],
      citations: [],
      retrievedCount: 0,
      llmModelId: selectedChatModel.value?.id || null,
      llmModelLabel: selectedChatModel.value?.label || null
    }
    session.messages.push(errorMessage)
    session.updatedAt = errorMessage.createdAt
    activeInsightMessageId.value = errorMessage.id
    ElMessage.error('检索失败，请稍后重试')
  } finally {
    asking.value = false
  }
}

function openPreview(citation: RagCitation) {
  previewState.value = {
    knowledgeBaseId: citation.knowledgeBaseId,
    documentId: citation.documentId,
    fileName: citation.fileName,
    fileType: citation.fileType,
    downloadUrl: `/api/v1/knowledge/bases/${citation.knowledgeBaseId}/documents/${citation.documentId}/download`
  }
  previewVisible.value = true
}

function buildRequestQuery(session: RagSession, question: string) {
  if (!session.contextEnabled) return question

  const pairs = getConversationPairs(session.messages)
  const contextPairs = pairs.slice(-session.contextTurns)
  if (!contextPairs.length) return question

  const contextText = contextPairs
    .map((pair, index) => {
      return `历史第 ${index + 1} 轮问题：${pair.question}\n历史第 ${index + 1} 轮回答：${pair.answer}`
    })
    .join('\n')

  return `请基于以下历史上下文理解当前问题，但回答时优先使用本轮命中的知识库内容。\n${contextText}\n当前问题：${question}`
}

function getConversationPairs(messages: RagMessage[]) {
  const pairs: Array<{ question: string; answer: string }> = []
  for (let index = 0; index < messages.length; index += 1) {
    const current = messages[index]
    const next = messages[index + 1]
    if (current?.role === 'user' && next?.role === 'assistant' && !next.failed) {
      pairs.push({
        question: current.content,
        answer: next.content
      })
    }
  }
  return pairs
}

function buildAnswerContent(response: SearchResponse | null | undefined, question: string) {
  const answer = response?.answer?.trim()
  if (answer) return answer

  const firstResult = response?.results?.[0]
  if (!firstResult) {
    return '当前没有检索到相关文档片段，建议换一个描述方式或补充更明确的关键词。'
  }

  const summary = extractCoreExcerpt([firstResult.content], question, firstResult.sectionTitle || '')
  return `已命中 ${response?.total || response?.results?.length || 1} 条相关片段，优先参考「${firstResult.fileName}」中的核心命中内容：${summary}`
}

function buildProcessSummary(response: SearchResponse | null | undefined, knowledgeBaseName: string) {
  if (response?.processSummary?.trim()) {
    return response.processSummary.trim()
  }

  const uniqueDocs = new Set((response?.results || []).map(item => item.documentId)).size
  if (!response?.results?.length) {
    return `已在知识库「${knowledgeBaseName}」中完成检索，但当前没有找到可支撑回答的文档片段。`
  }
  return `已在知识库「${knowledgeBaseName}」中召回 ${response.total || response.results.length} 条片段，覆盖 ${uniqueDocs} 份文件，并基于命中文档生成本轮总结。`
}

function extractSummaryPoints(response: SearchResponse | null | undefined, question: string) {
  const candidateTexts: string[] = []
  if (response?.answer?.trim()) {
    candidateTexts.push(response.answer.trim())
  }

  for (const item of response?.results || []) {
    candidateTexts.push([item.sectionTitle, item.content].filter(Boolean).join('。'))
  }

  const extracted = collectRepresentativePoints(candidateTexts, question, 4)
  if (extracted.length) {
    return extracted
  }

  return (response?.results || [])
    .slice(0, 4)
    .map(item => {
      const excerpt = extractCoreExcerpt([item.content], question, item.sectionTitle || '')
      const section = item.sectionTitle ? ` / ${item.sectionTitle}` : ''
      return `${item.fileName}${section}：${excerpt}`
    })
}

function buildThinkingSteps(params: {
  knowledgeBaseName: string
  question: string
  session: RagSession
  mode: SearchMode
  response: SearchResponse | null | undefined
  llmModelLabel?: string | null
}) {
  const { knowledgeBaseName, question, session, mode, response, llmModelLabel } = params
  const resultCount = response?.total || response?.results?.length || 0
  const fileCount = new Set((response?.results || []).map(item => item.documentId)).size

  return [
    {
      title: '问题聚焦',
      detail: `识别当前问题“${shortenText(question, 28)}”，并将检索范围限定在知识库「${knowledgeBaseName}」。`
    },
    {
      title: '上下文处理',
      detail: session.contextEnabled
        ? `携带最近 ${session.contextTurns} 轮问答作为语义上下文，帮助模型理解延续性问题。`
        : '当前按单轮问题检索，不携带历史上下文。'
    },
    {
      title: '证据召回',
      detail: `采用${modeLabel(mode)}召回 ${resultCount} 条文档片段，覆盖 ${fileCount} 份候选文件。`
    },
    {
      title: '证据整合',
      detail: response?.results?.length
        ? `结合命中文档片段的相关度、章节信息和内容摘要，对证据进行排序并去重。`
        : '当前没有召回有效文档片段，因此未形成可验证的证据集合。'
    },
    {
      title: '输出总结',
      detail: response?.answer?.trim()
        ? `基于命中文档生成本轮总结${llmModelLabel ? `，回答模型为 ${llmModelLabel}` : ''}，并同步输出关键点与可预览文件。`
        : llmModelLabel
          ? `已选择模型 ${llmModelLabel}，但当前未拿到模型总结，页面改用高相关片段生成兜底说明。`
          : '当前未选择问答模型，页面使用检索结果的高相关片段生成兜底说明。'
    }
  ]
}

function buildCitations(results: SearchResultItem[], question: string) {
  const grouped = new Map<number, RagCitation & { pageSet: Set<number>; textPool: string[]; sectionPool: string[] }>()

  results.forEach((item) => {
    const existing = grouped.get(item.documentId)
    const knowledgeBaseId = Number(item.knowledgeBaseId || selectedKbId.value || 0)
    const pageNum = item.pageNum || 0

    if (!existing) {
      grouped.set(item.documentId, {
        knowledgeBaseId,
        documentId: item.documentId,
        fileName: item.fileName,
        fileType: detectFileType(item.fileName),
        sectionTitle: item.sectionTitle || '',
        excerpt: '',
        score: item.score,
        hitCount: 1,
        pageText: pageNum > 0 ? `第 ${pageNum} 页` : '页码未标注',
        pageSet: pageNum > 0 ? new Set([pageNum]) : new Set<number>(),
        textPool: [item.content],
        sectionPool: item.sectionTitle ? [item.sectionTitle] : []
      })
      return
    }

    existing.hitCount += 1
    existing.score = Math.max(existing.score, item.score)
    existing.textPool.push(item.content)
    if (item.sectionTitle) {
      existing.sectionPool.push(item.sectionTitle)
    }
    if (!existing.sectionTitle && item.sectionTitle) {
      existing.sectionTitle = item.sectionTitle
    }
    if (pageNum > 0) {
      existing.pageSet.add(pageNum)
      existing.pageText = formatPageText(existing.pageSet)
    }
  })

  return Array.from(grouped.values())
    .map(({ pageSet: _pageSet, textPool, sectionPool, ...citation }) => {
      const preferredSection = pickBestSectionTitle(sectionPool, question)
      return {
        ...citation,
        sectionTitle: preferredSection || citation.sectionTitle,
        excerpt: extractCoreExcerpt(textPool, question, preferredSection || citation.sectionTitle || '')
      }
    })
    .sort((a, b) => b.score - a.score)
}

function collectRepresentativePoints(texts: string[], question: string, limit: number) {
  const queryTerms = extractQueryTerms(question)
  const candidates = texts
    .flatMap(text => splitIntoSegments(text))
    .map((segment) => ({
      text: cleanupSegment(segment),
      score: scoreSegment(segment, queryTerms)
    }))
    .filter(item => item.text.length >= 10)
    .sort((a, b) => b.score - a.score || a.text.length - b.text.length)

  const result: string[] = []
  for (const candidate of candidates) {
    if (result.length >= limit) break
    if (result.some(existing => isSimilarText(existing, candidate.text))) continue
    result.push(shortenText(candidate.text, 72))
  }
  return result
}

function extractCoreExcerpt(texts: string[], question: string, sectionTitle = '') {
  const queryTerms = extractQueryTerms(question)
  const segments = texts
    .flatMap(text => splitIntoSegments(text))
    .map(item => cleanupSegment(item))
    .filter(Boolean)

  const bestSegment = [...segments]
    .sort((a, b) => scoreSegment(b, queryTerms, sectionTitle) - scoreSegment(a, queryTerms, sectionTitle))[0]

  if (bestSegment) {
    return shortenText(bestSegment, 88)
  }

  const fallback = cleanupSegment(texts.join(' '))
  return shortenText(fallback || '未提取到可展示的命中摘要。', 88)
}

function pickBestSectionTitle(sectionTitles: string[], question: string) {
  const titles = sectionTitles
    .map(item => cleanupSegment(item))
    .filter(Boolean)

  if (!titles.length) return ''

  const queryTerms = extractQueryTerms(question)
  return [...titles]
    .sort((a, b) => scoreSegment(b, queryTerms) - scoreSegment(a, queryTerms) || a.length - b.length)[0]
}

function splitIntoSegments(text: string) {
  return (text || '')
    .replace(/\r/g, '\n')
    .split(/[\n。！？；;]/)
    .flatMap(part => part.split(/[,:：]/))
    .map(item => item.trim())
    .filter(Boolean)
}

function cleanupSegment(text: string) {
  return text
    .replace(/\s+/g, ' ')
    .replace(/[|•]/g, ' ')
    .replace(/【来源：[^】]+】/g, '')
    .replace(/^[-*•\d.\s]+/, '')
    .trim()
}

function extractQueryTerms(question: string) {
  const raw = question.toLowerCase()
  const tokens = raw.match(/[a-z0-9_-]+|[\u4e00-\u9fa5]{2,}/g) || []
  return Array.from(new Set(tokens.filter(token => token.length >= 2)))
}

function scoreSegment(text: string, queryTerms: string[], sectionTitle = '') {
  const normalized = text.toLowerCase()
  let score = 0

  for (const term of queryTerms) {
    if (normalized.includes(term)) {
      score += Math.min(4, term.length)
    }
  }

  if (sectionTitle && normalized.includes(sectionTitle.toLowerCase())) {
    score += 2
  }

  if (text.length >= 18 && text.length <= 100) {
    score += 2
  }

  if (/命中|支持|用于|流程|配置|调用|处理|同步|异步|检索|审批|文档/.test(text)) {
    score += 1
  }

  return score
}

function isSimilarText(left: string, right: string) {
  const leftNormalized = normalizeCompareText(left)
  const rightNormalized = normalizeCompareText(right)
  if (!leftNormalized || !rightNormalized) return false
  if (leftNormalized === rightNormalized) return true
  if (leftNormalized.includes(rightNormalized) || rightNormalized.includes(leftNormalized)) return true

  const leftTerms = new Set(leftNormalized.split(' ').filter(Boolean))
  const rightTerms = new Set(rightNormalized.split(' ').filter(Boolean))
  const intersection = Array.from(leftTerms).filter(item => rightTerms.has(item)).length
  const union = new Set([...leftTerms, ...rightTerms]).size || 1
  return intersection / union >= 0.65
}

function normalizeCompareText(text: string) {
  return cleanupSegment(text)
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function formatPageText(pageSet: Set<number>) {
  const values = Array.from(pageSet).sort((a, b) => a - b)
  if (!values.length) return '页码未标注'
  if (values.length === 1) return `第 ${values[0]} 页`
  return `第 ${values[0]} / ${values[values.length - 1]} 页`
}

function isChatModel(model: LlmModel) {
  const type = (model.modelType || '').toLowerCase()
  return type !== 'embedding' && type !== 'rerank'
}

function compactModelName(model: RagModelOption) {
  const source = (model.name || model.modelId || model.label).trim()
  const compact = source.replace(/^(gpt|claude|glm|qwen|deepseek|gemini)[-_:\s]*/i, '').trim()
  return compact || source
}

function toModelOption(provider: LlmProvider, model: LlmModel): RagModelOption {
  return {
    id: model.id || 0,
    providerId: provider.id || 0,
    providerName: provider.name,
    name: model.name,
    label: `${provider.name} / ${model.name}`,
    modelId: model.modelId,
    modelType: model.modelType,
    isDefault: Boolean(model.isDefault)
  }
}

function buildSessionTitle(question: string) {
  return shortenText(question.replace(/\s+/g, ' ').trim(), 18)
}

function modeLabel(mode: SearchMode) {
  const labels: Record<SearchMode, string> = {
    hybrid: '混合检索',
    semantic: '语义检索',
    keyword: '关键词检索'
  }
  return labels[mode]
}

function detectFileType(fileName: string) {
  const segments = fileName.split('.')
  return segments.length > 1 ? segments[segments.length - 1].toLowerCase() : ''
}

function shortenText(text: string, maxLength: number) {
  if (text.length <= maxLength) return text
  return `${text.slice(0, Math.max(0, maxLength - 1))}…`
}

function createId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function formatDateTime(timestamp: number) {
  return formatDate(new Date(timestamp))
}
</script>

<style scoped lang="scss">
.rag-workspace {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 340px;
  gap: $spacing-md;
  min-height: calc(100vh - 220px);
}

.rag-shell {
  border-radius: 12px;
  border: 1px solid $border-color;
  background: $bg-container;
  color: $text-color;
  box-shadow: $shadow-sm;
  overflow: hidden;
}

.rag-sidebar,
.rag-insights {
  padding: $spacing-md;
}

.rag-sidebar {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.sidebar-section {
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid rgba(235, 238, 245, 0.9);
  padding: $spacing-md;
}

.sidebar-section--fill {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section-heading,
.insight-card__header,
.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-sm;
}

.section-label,
.insight-card__label,
.chat-title__label {
  font-size: $font-size-sm;
  font-weight: 600;
  color: $text-color;
}

.section-tip,
.chat-empty__desc,
.insight-empty__desc {
  margin-top: 4px;
  font-size: $font-size-xs;
  line-height: 1.6;
  color: $text-color-secondary;
}

.section-badge {
  min-width: 34px;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.12);
  color: $primary-color;
  font-size: $font-size-xs;
  font-weight: 600;
  text-align: center;
}

.knowledge-grid {
  margin-top: $spacing-sm;
  display: grid;
  gap: $spacing-sm;
}

.knowledge-card,
.session-item,
.citation-item {
  width: 100%;
  border: 0;
  text-align: left;
  cursor: pointer;
}

.knowledge-card {
  padding: 12px;
  border-radius: 10px;
  background: $bg-container;
  border: 1px solid $border-color;
  color: $text-color;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.knowledge-card:hover,
.session-item:hover,
.citation-item:hover {
  transform: translateY(-1px);
}

.knowledge-card:hover {
  box-shadow: $shadow-sm;
}

.knowledge-card--active {
  border-color: rgba(64, 158, 255, 0.55);
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.12);
}

.knowledge-card__title,
.knowledge-card__meta,
.session-item__meta,
.citation-item__top,
.citation-item__meta,
.composer-toolbar,
.composer-actions,
.message-bubble__footer,
.chat-filters {
  display: flex;
  align-items: center;
}

.knowledge-card__title,
.citation-item__top {
  justify-content: space-between;
  gap: $spacing-sm;
}

.knowledge-card__title span {
  font-weight: 600;
}

.knowledge-card__desc {
  margin-top: 6px;
  font-size: $font-size-xs;
  line-height: 1.6;
  color: $text-color-secondary;
}

.knowledge-card__meta {
  gap: $spacing-sm;
  margin-top: $spacing-sm;
  flex-wrap: wrap;
  font-size: $font-size-xs;
  color: $text-color-placeholder;
}

.session-list {
  margin-top: $spacing-sm;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  min-height: 0;
  overflow: auto;
}

.session-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: $bg-container;
  border: 1px solid $border-color;
  color: $text-color;
  display: flex;
  justify-content: space-between;
  gap: $spacing-sm;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.session-item:hover {
  box-shadow: $shadow-sm;
}

.session-item--active {
  border-color: rgba(64, 158, 255, 0.55);
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.12);
}

.session-item__main {
  min-width: 0;
}

.session-item__title {
  font-size: $font-size-base;
  font-weight: 600;
}

.session-item__meta,
.message-bubble__footer,
.citation-item__meta,
.composer-actions__tip {
  gap: $spacing-sm;
  flex-wrap: wrap;
  font-size: $font-size-xs;
  color: $text-color-placeholder;
}

.session-item__context {
  margin-top: 6px;
  color: $primary-color;
  font-size: $font-size-xs;
}

.rag-chat {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid $border-color;
  background: linear-gradient(180deg, #ffffff, #fbfcfe);
}

.chat-title__main {
  margin-top: 4px;
  font-size: 18px;
  line-height: 1.3;
  font-weight: 700;
}

.chat-filters {
  gap: $spacing-sm;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-stream {
  flex: 1;
  padding: $spacing-lg;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  background: $bg-container;
}

.chat-empty,
.insight-empty {
  border-radius: 12px;
  padding: $spacing-lg;
  border: 1px dashed rgba(144, 147, 153, 0.35);
  background: #fbfcfe;
}

.chat-empty__title,
.insight-empty__title,
.insight-card__title {
  font-size: 16px;
  font-weight: 700;
  color: $text-color;
}

.message-row {
  display: flex;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(78%, 760px);
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid $border-color;
  cursor: default;
}

.message-row--user .message-bubble {
  background: $primary-color;
  border-color: rgba(64, 158, 255, 0.65);
  border-top-right-radius: 6px;
}

.message-row--assistant .message-bubble {
  border-top-left-radius: 6px;
}

.message-bubble--assistant-active {
  border-color: rgba(64, 158, 255, 0.55);
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.12);
  cursor: pointer;
}

.message-bubble--loading {
  min-width: 280px;
}

.message-bubble--error {
  border-color: rgba(245, 108, 108, 0.6);
  background: rgba(245, 108, 108, 0.08);
}

.message-bubble__role {
  font-size: $font-size-xs;
  color: $text-color-placeholder;
}

.message-row--user .message-bubble__role {
  color: rgba(255, 255, 255, 0.85);
}

.message-bubble__content {
  margin-top: 6px;
  line-height: 1.75;
  white-space: pre-wrap;
  color: $text-color;
}

.message-row--user .message-bubble__content {
  color: #ffffff;
}

.message-bubble__footer {
  margin-top: 10px;
}

.message-bubble__hint {
  margin-top: 8px;
  font-size: $font-size-sm;
  color: $text-color-secondary;
}

.thinking-loader {
  display: inline-flex;
  gap: 8px;
  margin-top: 10px;
}

.thinking-loader__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.65);
  animation: pulse 1.2s ease-in-out infinite;
}

.thinking-loader__dot:nth-child(2) {
  animation-delay: 0.2s;
}

.thinking-loader__dot:nth-child(3) {
  animation-delay: 0.4s;
}

.composer-panel {
  padding: $spacing-md $spacing-lg $spacing-lg;
  border-top: 1px solid $border-color;
  background: #fbfcfe;
}

.insight-card__tags {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.composer-shell {
  border-radius: 12px;
  padding: 12px 14px;
  border: 1px solid $border-color;
  background: $bg-container;
}

.composer-shell--disabled {
  opacity: 0.76;
}

.composer-shell__body {
  min-height: 104px;
}

.composer-input {
  :deep(.el-textarea__inner) {
    min-height: 104px !important;
    padding: 0;
    border: 0;
    background: transparent;
    color: $text-color;
    box-shadow: none;
    font-size: 16px;
    line-height: 1.6;
  }

  :deep(.el-textarea__inner::placeholder) {
    color: $text-color-placeholder;
  }
}

.composer-bottom {
  margin-top: $spacing-md;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
}

.composer-bottom__left,
.composer-bottom__right,
.composer-pill,
.composer-provider-badge,
.composer-ghost-action {
  display: flex;
  align-items: center;
}

.composer-bottom__left {
  min-width: 0;
  gap: $spacing-sm;
  flex: 1;
}

.composer-bottom__right {
  justify-content: flex-end;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.composer-tipline {
  min-width: 0;
  font-size: $font-size-xs;
  line-height: 1.6;
  color: $text-color-secondary;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.composer-ghost-action,
.composer-pill,
.composer-provider-badge,
.composer-send {
  border: 0;
  transition: transform 0.18s ease, background-color 0.18s ease, color 0.18s ease, opacity 0.18s ease;
}

.composer-ghost-action,
.composer-pill {
  border-radius: 10px;
  padding: 0 12px;
  min-height: 36px;
  background: rgba(64, 158, 255, 0.1);
  color: $text-color;
  gap: 8px;
}

.composer-ghost-action {
  cursor: pointer;
  font-size: $font-size-sm;
}

.composer-pill {
  cursor: pointer;
  font-size: $font-size-sm;
}

.composer-pill--model {
  min-width: 110px;
  justify-content: space-between;
}

.composer-provider-badge {
  border-radius: 999px;
  padding: 0 12px;
  min-height: 36px;
  background: rgba(64, 158, 255, 0.12);
  color: $primary-color;
  font-size: $font-size-sm;
  font-weight: 600;
}

.composer-pill--interactive:hover,
.composer-ghost-action:hover,
.composer-send:hover:not(:disabled) {
  transform: translateY(-1px);
}

.composer-pill--interactive:hover,
.composer-ghost-action:hover {
  background: rgba(64, 158, 255, 0.16);
}

.composer-send {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: $primary-color;
  color: #ffffff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.composer-send__icon {
  font-size: 18px;
  line-height: 1;
}

.composer-ghost-action:disabled,
.composer-pill:disabled,
.composer-send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}

.composer-menu {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.composer-menu__title,
.composer-menu__group-label {
  color: $text-color-secondary;
  font-size: $font-size-xs;
  font-weight: 600;
}

.composer-menu__group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.composer-menu__group + .composer-menu__group {
  padding-top: 10px;
  border-top: 1px solid $border-color;
}

.composer-menu__item {
  width: 100%;
  border: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fafc;
  color: $text-color;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  cursor: pointer;
  text-align: left;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.composer-menu__item:hover {
  background: rgba(64, 158, 255, 0.1);
  transform: translateY(-1px);
}

.composer-menu__item--active {
  background: rgba(64, 158, 255, 0.12);
}

.composer-menu__label {
  font-size: $font-size-base;
  font-weight: 600;
}

.composer-menu__hint {
  margin-top: 4px;
  color: $text-color-secondary;
  font-size: $font-size-xs;
}

.composer-menu__check {
  min-width: 16px;
  text-align: right;
  font-size: 18px;
  color: $primary-color;
}

.rag-insights {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  overflow: auto;
}

.insight-card {
  border-radius: 10px;
  padding: $spacing-md;
  background: #fbfcfe;
  border: 1px solid $border-color;
}

.insight-card--summary {
  border-color: rgba(64, 158, 255, 0.22);
  background: rgba(64, 158, 255, 0.06);
}

.summary-content {
  margin-top: $spacing-sm;
  line-height: 1.8;
  color: $text-color;
}

.thinking-list,
.keypoint-list,
.citation-list {
  margin-top: $spacing-sm;
}

.thinking-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.thinking-item {
  display: flex;
  gap: $spacing-sm;
}

.thinking-item__index {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.12);
  color: $primary-color;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: $font-size-xs;
}

.thinking-item__title,
.citation-item__name {
  font-weight: 600;
  color: $text-color;
}

.thinking-item__detail {
  margin-top: 4px;
  line-height: 1.7;
  color: $text-color-secondary;
}

.keypoint-list {
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  color: $text-color;
  line-height: 1.7;
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.citation-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid $border-color;
  color: $text-color;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.citation-item:hover {
  box-shadow: $shadow-sm;
}

.citation-item__name {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.citation-item__meta {
  margin-top: 6px;
}

.citation-item__excerpt {
  margin-top: 8px;
  line-height: 1.7;
  color: $text-color-secondary;
}

.citation-item__excerpt :deep(mark) {
  background: rgba(64, 158, 255, 0.16);
  color: $text-color;
}

.rag-workspace :deep(.el-empty__description p) {
  color: $text-color-secondary;
}

.rag-workspace :deep(.el-button.is-text) {
  padding: 0;
}

.rag-workspace :deep(.rag-composer-popover.el-popover) {
  border-radius: 12px;
  border: 1px solid $border-color;
  background: $bg-container;
  box-shadow: $shadow-base;
  padding: $spacing-sm;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }

  50% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

@media (max-width: 1440px) {
  .rag-workspace {
    grid-template-columns: 300px minmax(0, 1fr) 320px;
  }
}

@media (max-width: 1200px) {
  .rag-workspace {
    grid-template-columns: 1fr;
  }

  .rag-sidebar,
  .rag-chat,
  .rag-insights {
    min-height: auto;
  }

  .chat-stream {
    min-height: 440px;
  }

  .message-bubble {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .chat-header,
  .composer-panel,
  .rag-sidebar,
  .rag-insights {
    padding: $spacing-md;
  }

  .chat-stream {
    padding: $spacing-md;
  }

  .chat-filters,
  .composer-bottom {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-bottom__left,
  .composer-bottom__right {
    width: 100%;
    justify-content: flex-start;
  }

  .composer-tipline {
    white-space: normal;
  }

  .message-bubble {
    max-width: 92%;
  }
}
</style>
