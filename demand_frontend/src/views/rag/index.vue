<template>
  <PageContainer title="知识库问答" subtitle="基于选定的知识库进行智能检索与对话">
    <template #headerActions>
      <el-button @click="refreshKnowledgeBases" :loading="refreshing">
        <el-icon><RefreshRight /></el-icon>
        <span>刷新</span>
      </el-button>
      <el-button plain @click="goToModelCenter">模型配置</el-button>
      <el-button type="primary" plain @click="goToKnowledgeManagement">管理知识库</el-button>
    </template>

    <!-- 主布局：左侧边栏(可折叠) + 中间对话区 -->
    <div class="rag-workspace" :style="ragSidebar.styleVars">
      <!-- 左侧知识库导航 -->
      <aside class="rag-sidebar" :class="{ 'is-collapsed': ragSidebar.collapsed, 'is-visible': sidebarVisible }">
        <div class="rag-sidebar__overlay" v-if="!ragSidebar.collapsed && isMobile" @click="ragSidebar.toggle"></div>
        <div class="rag-sidebar__panel">
          <div class="rag-sidebar__header">
            <div class="rag-sidebar__title">知识库</div>
            <el-button link class="rag-sidebar__collapse-trigger" title="收起侧边栏" @click="ragSidebar.toggle">
              <el-icon><ArrowLeft /></el-icon>
            </el-button>
          </div>

          <div class="sidebar-body">
            <section class="sidebar-section">
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
                  <div class="knowledge-card__desc">{{ kb.description || '暂无描述' }}</div>
                  <div class="knowledge-card__meta">
                    <span>{{ kb.docCount }} 份文档</span>
                    <span>{{ kb.chunkCount }} 个分块</span>
                  </div>
                </button>
              </div>
              <el-empty v-else description="暂无知识库" :image-size="60" />
            </section>

            <section v-if="selectedKnowledgeBase" class="sidebar-section sidebar-section--fill">
              <div class="section-heading">
                <span class="section-label">对话记录</span>
                <el-button type="primary" text size="small" @click="createSessionForCurrentKb">
                  <el-icon><Plus /></el-icon>新建
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
                      <span>{{ session.messages.length }} 条</span>
                    </div>
                  </div>
                  <el-button text type="danger" size="small" @click.stop="handleDeleteSession(session)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </button>
              </div>
              <div v-else class="session-empty">暂无会话记录</div>
            </section>
          </div>
        </div>
      </aside>

      <!-- 侧边栏展开按钮(桌面端折叠时显示) -->
      <button
        v-if="ragSidebar.collapsed && !isMobile"
        class="rag-sidebar-expand-btn"
        type="button"
        @click="ragSidebar.toggle"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>

      <!-- 中间对话区 -->
      <main class="rag-main">
        <!-- 顶部：知识库名称 + 系统提示 -->
        <header class="chat-topbar" v-if="selectedKnowledgeBase">
          <h1 class="chat-topbar__title">{{ selectedKnowledgeBase.name }}</h1>
          <div class="chat-topbar__prompt">
            <el-input
              v-model="systemPrompt"
              placeholder="设置系统提示词，引导 AI 回答风格与范围..."
              size="default"
              clearable
            />
          </div>
        </header>

        <!-- 对话流区域 -->
        <div class="chat-stream" ref="chatStreamRef">
          <template v-if="activeSession && activeSession.messages.length">
            <div
              v-for="message in activeSession.messages"
              :key="message.id"
              class="msg-row"
              :class="[`msg-row--${message.role}`]"
            >
              <!-- 用户消息 -->
              <template v-if="message.role === 'user'">
                <div class="msg-avatar msg-avatar--user">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none"><path d="M12 12c2.7 0 5-2.3 5-5s-2.3-5-5-5-5 2.3-5 5 2.3 5 5 5zm0 2c-3.3 0-10 1.7-10 5v2h20v-2c0-3.3-6.7-5-10-5z" fill="currentColor"/></svg>
                </div>
                <div class="msg-body">
                  <!-- 搜索引用链接 -->
                  <div class="msg-search-ref" v-if="message.content">
                    <el-icon><Search /></el-icon>
                    <span>搜索 {{ retrievedCountForMessage(message) }} 篇相关文档片段</span>
                    <el-icon><ArrowRight /></el-icon>
                  </div>
                  <div class="msg-text msg-text--user">{{ message.content }}</div>
                </div>
              </template>

              <!-- 助手消息 -->
              <template v-else>
                <div class="msg-avatar msg-avatar--assistant">
                  <div class="msg-avatar__icon">W</div>
                </div>
                <div class="msg-body">
                  <!-- 助手名称+简介 -->
                  <div class="msg-assistant-header">
                    <span class="msg-assistant-name">WorkBuddy</span>
                  </div>
                  <div class="msg-assistant-intro" v-if="isFirstAssistantInGroup(message)">
                    我开始执行深度研究：先搜集最新行业动态，再产出结构化报告。
                  </div>
                  <!-- 交付物链接卡片 -->
                  <div class="msg-deliverable-card" v-if="!message.failed && message.content">
                    <el-icon><EditPen /></el-icon>
                    <span>创建 .md / .docx 格式的研究报告</span>
                    <el-icon><ArrowRight /></el-icon>
                  </div>
                  <!-- 内容卡片 -->
                  <div class="msg-content-card" :class="{ 'msg-content-card--error': message.failed, 'msg-content-card--active': activeInsightMessageId === message.id }" @click="handleSelectInsight(message)">
                    <div class="msg-content-card__title" v-if="!message.failed && message.content">
                      交付 {{ selectedKnowledgeBase?.name || '知识库问答' }}
                    </div>
                    <MarkdownContent
                      v-if="message.role === 'assistant'"
                      :content="message.content"
                      :citations="message.citations"
                      class="msg-content-card__body"
                      @citation-click="handleCitationClick(message, $event)"
                    />
                    <div v-if="message.warnings?.length" class="retrieval-warning-list">
                      <el-alert
                        v-for="warning in message.warnings"
                        :key="warning"
                        :title="warning"
                        type="warning"
                        :closable="false"
                        show-icon
                      />
                    </div>
                    <div v-else class="msg-content-card__body">{{ message.content }}</div>
                  </div>
                  <!-- 底部元信息 -->
                  <div class="msg-footer" v-if="message.role === 'assistant'">
                    <span v-if="message.citations?.length">{{ message.citations.length }} 个引用来源</span>
                    <span v-if="message.retrievedCount">{{ message.retrievedCount }} 条命中</span>
                    <span v-if="message.llmModelLabel">模型：{{ message.llmModelLabel }}</span>
                  </div>
                </div>
              </template>
            </div>
          </template>

          <!-- 空状态 -->
          <div v-else class="chat-empty-state">
            <div class="chat-empty-state__icon">💬</div>
            <div class="chat-empty-state__title">开始一次新的知识问答</div>
            <div class="chat-empty-state__desc">选择知识库后输入问题，AI 将基于文档内容给出结构化的分析与回答。</div>
          </div>

          <!-- 加载中状态 -->
          <div v-if="asking && !streamingMessageId" class="msg-row msg-row--assistant">
            <div class="msg-avatar msg-avatar--assistant">
              <div class="msg-avatar__icon">W</div>
            </div>
            <div class="msg-body">
              <div class="msg-assistant-header">
                <span class="msg-assistant-name">WorkBuddy</span>
              </div>
              <div class="msg-content-card msg-content-card--loading">
                <div class="thinking-loader">
                  <span class="thinking-loader__dot"></span>
                  <span class="thinking-loader__dot"></span>
                  <span class="thinking-loader__dot"></span>
                </div>
                <div class="thinking-loader__text">正在整理问题、召回文档并汇总答案...</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <footer class="composer-area">
          <div class="composer-box" :class="{ 'composer-box--disabled': !selectedKnowledgeBase }">
            <textarea
              ref="textareaRef"
              v-model="draftQuestion"
              class="composer-textarea"
              :disabled="!selectedKnowledgeBase"
              placeholder="输入消息..."
              rows="1"
              @input="autoResizeTextarea"
              @keydown.enter.exact.prevent="handleAsk"
            ></textarea>
            <div class="composer-toolbar">
              <div class="composer-toolbar__left">
                <!-- 上下文切换 -->
                <el-popover
                  v-model:visible="contextPopoverVisible"
                  trigger="click"
                  placement="top-start"
                  width="240"
                  popper-class="rag-composer-popover"
                >
                  <template #reference>
                    <button type="button" class="toolbar-btn" :disabled="!activeSession">
                      <el-icon><ChatDotRound /></el-icon>
                      <span>Ask</span>
                      <el-icon><ArrowDown /></el-icon>
                    </button>
                  </template>
                  <div class="composer-menu">
                    <div class="composer-menu__title">上下文设置</div>
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
              </div>

              <div class="composer-toolbar__right">
                <!-- 模型选择器 -->
                <el-popover
                  v-model:visible="modelPopoverVisible"
                  trigger="click"
                  placement="top-end"
                  width="520"
                  popper-class="rag-composer-popover"
                >
                  <template #reference>
                    <button type="button" class="toolbar-btn toolbar-btn--model" :disabled="!availableChatModels.length">
                      <el-icon><Cpu /></el-icon>
                      <span>{{ selectedModelDisplay }}</span>
                      <el-icon><ArrowDown /></el-icon>
                    </button>
                  </template>
                  <div class="composer-menu composer-menu--two-level">
                    <div class="composer-menu__title">选择模型</div>
                    <div class="composer-menu__two-level">
                      <div class="composer-menu__provider-list">
                        <div class="composer-menu__section-title">接入组</div>
                        <button
                          v-for="group in groupedChatModels"
                          :key="group.providerName"
                          type="button"
                          class="composer-menu__provider-item"
                          :class="{ 'composer-menu__provider-item--active': selectedProvider === group.providerName }"
                          @click="selectedProvider = group.providerName"
                        >
                          <div class="composer-menu__provider-name">{{ group.providerName }}</div>
                          <div class="composer-menu__provider-count">{{ group.items.length }} 个</div>
                        </button>
                      </div>
                      <div class="composer-menu__model-list">
                        <div class="composer-menu__section-title">模型列表</div>
                        <button
                          v-for="model in currentProviderModels"
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
                        <el-empty v-if="!currentProviderModels.length" description="该接入组暂无可用模型" :image-size="60" />
                      </div>
                    </div>
                  </div>
                </el-popover>

                <!-- 附件按钮 -->
                <button type="button" class="toolbar-btn toolbar-btn--icon" title="添加附件">
                  <el-icon><Paperclip /></el-icon>
                </button>

                <!-- 发送按钮 -->
                <button
                  type="button"
                  class="send-btn"
                  :disabled="!selectedKnowledgeBase || !draftQuestion.trim() || selectedSearchScopes.length === 0"
                  @click="handleAsk"
                >
                  <el-icon><Promotion /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <!-- 检索配置提示 -->
          <div class="composer-hint" v-if="selectedKnowledgeBase">
            <span>{{ composerTip }}</span>
            <el-checkbox-group v-model="selectedSearchScopes" class="search-scope-group" size="small">
              <el-checkbox label="REQUIREMENT_BODY">工单正文</el-checkbox>
              <el-checkbox label="KNOWLEDGE_BASE">知识库</el-checkbox>
            </el-checkbox-group>
            <div class="composer-hint__actions">
              <el-select v-model="searchMode" size="small" style="width: 110px">
                <el-option label="混合模式" value="hybrid" />
                <el-option label="语义检索" value="semantic" />
                <el-option label="关键词" value="keyword" />
              </el-select>
              <el-select v-model="topK" size="small" style="width: 85px">
                <el-option :value="5" label="Top 5" />
                <el-option :value="10" label="Top 10" />
                <el-option :value="20" label="Top 20" />
              </el-select>
              <button
                type="button"
                class="ghost-btn"
                :disabled="!activeSession || asking"
                @click="handleClearSession"
              >清空对话</button>
            </div>
          </div>
        </footer>
      </main>

      <!-- 右侧证据面板（浮层） -->
      <transition name="slide-left">
        <aside class="rag-insights-panel" v-if="showInsightsPanel && currentInsight">
          <div class="insights-panel__header">
            <span class="insights-panel__title">证据面板</span>
            <el-button link @click="showInsightsPanel = false">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>

          <div class="insights-panel__body">
            <!-- 关键总结 -->
            <section class="insight-section">
              <div class="insight-section__heading">
                <span class="insight-section__label">关键问题总结</span>
                <div class="insight-section__tags">
                  <el-tag v-if="currentInsight.llmModelLabel" size="small" effect="dark" type="success">{{ currentInsight.llmModelLabel }}</el-tag>
                  <el-tag size="small" effect="dark" type="info">{{ currentInsight.retrievedCount || 0 }} 条片段</el-tag>
                </div>
              </div>
              <p class="insight-summary">{{ currentInsight.processSummary || currentInsight.content }}</p>
            </section>

            <!-- 关键点 -->
            <section class="insight-section" v-if="currentInsight.summaryPoints?.length">
              <div class="insight-section__label">关键点</div>
              <ul class="keypoint-list">
                <li v-for="point in currentInsight.summaryPoints" :key="point">{{ point }}</li>
              </ul>
            </section>

            <!-- 引用来源 -->
            <section class="insight-section" v-if="currentInsight.citations?.length">
              <div class="insight-section__label">引用来源</div>
              <div class="citation-list">
                <div
                  v-for="citation in currentInsight.citations"
                  :key="citationKey(citation)"
                  class="citation-item"
                  :class="{
                    'citation-item--expanded': expandedCitations.has(citation.documentId),
                    'citation-item--requirement': isRequirementBodyCitation(citation),
                    'citation-item--previewable': isCitationPreviewable(citation),
                  }"
                >
                  <div class="citation-item__header" @click="handleCitationHeaderClick(citation)">
                    <div class="citation-item__name">
                      <el-icon><Document /></el-icon>
                      <span>{{ formatCitationTitle(citation) }}</span>
                      <el-tag v-if="isRequirementBodyCitation(citation)" size="small" type="primary">
                        {{ resolveCitationTypeLabel(citation) }}
                      </el-tag>
                      <el-tag size="small" type="info">{{ citation.hitCount }} 片段</el-tag>
                    </div>
                    <div class="citation-item__meta">
                      <span>{{ Math.round(citation.score * 100) }}%</span>
                      <el-button
                        text
                        type="primary"
                        size="small"
                        :title="resolveCitationActionTitle(citation)"
                        @click.stop="handleCitationPrimaryAction(citation)"
                      >
                        {{ resolveCitationActionLabel(citation) }}
                      </el-button>
                    </div>
                  </div>
                  <div
                    v-if="!isRequirementBodyCitation(citation) && expandedCitations.has(citation.documentId)"
                    class="citation-item__chunks"
                  >
                    <div v-for="(chunk, idx) in getCitationChunks(citation)" :key="idx" class="citation-chunk">
                      <HighlightText :content="chunk.content" :query="currentInsight.question || ''" />
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <el-empty v-else description="当前回答暂无引用来源" :image-size="60" />
          </div>
        </aside>
      </transition>

      <!-- 证据面板触发按钮 -->
      <button
        v-if="currentInsight && !showInsightsPanel"
        type="button"
        class="insights-trigger-btn"
        @click="showInsightsPanel = true"
        title="查看证据面板"
      >
        <el-icon><Document /></el-icon>
        <span class="insights-trigger-btn__badge">{{ currentInsight.citations?.length || 0 }}</span>
      </button>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowRight, ChatDotRound, Close, Cpu, Delete, Document, EditPen, Paperclip, Promotion, RefreshRight, Search, Plus } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import HighlightText from '@/components/common/HighlightText.vue'
import MarkdownContent from '@/components/common/MarkdownContent.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import { llmProviderApi, type ChatModelOption } from '@/api/modules/llmProvider'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'
import storage from '@/utils/storage'
import { formatDate } from '@/utils/format'
import { streamSearchKnowledge, type KnowledgeBase, type SearchMode, type SearchResponse, type SearchResultItem, type CitationReference, getRagConfig, type RagConfig, type RagModelCandidate } from '@/api/modules/knowledge'
import { PREVIEW_SUPPORTED_EXTENSION_SET, normalizeFileExtension } from '@/constants/knowledgeDocument'

interface RagThinkingStep {
  stepType?: 'query_parse' | 'retrieve' | 'rerank' | 'synthesize'
  title: string
  detail: string
  score?: number
}

interface CitationChunk {
  content: string
  sectionTitle?: string
  pageNum?: number
  score?: number
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
  chunks?: CitationChunk[]
  sourceType?: string | null
  requirementId?: number | null
  requirementNo?: string | null
  requirementTitle?: string | null
  contentType?: string | null
  imageFileId?: number | null
  imagePosition?: number | null
  focus?: string | null
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
  questionIntent?: string | null
  intentConfidence?: number | null
  processSummary?: string
  summaryPoints?: string[]
  thinkingSteps?: RagThinkingStep[]
  citations?: RagCitation[]
  warnings?: string[]
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
const ragSidebar = useCollapsibleSidebar({
  defaultWidth: 320,
  minWidth: 280,
  maxWidth: 380,
  widthVar: '--rag-sidebar-width',
})

const sessions = ref<RagSession[]>([])
const selectedKbId = ref<number | null>(null)
const activeSessionId = ref<string | null>(null)
const activeInsightMessageId = ref<string | null>(null)
const selectedLlmModelId = ref<number | null>(null)
const draftQuestion = ref('')
const searchMode = ref<SearchMode>('hybrid')
const topK = ref(10)
const selectedSearchScopes = ref<Array<'REQUIREMENT_BODY' | 'KNOWLEDGE_BASE'>>(['REQUIREMENT_BODY', 'KNOWLEDGE_BASE'])
const asking = ref(false)
const streamingMessageId = ref<string | null>(null)
const refreshing = ref(false)
const availableChatModels = ref<RagModelOption[]>([])
const systemPrompt = ref('')
const showInsightsPanel = ref(false)
const sidebarVisible = ref(true)
const isMobile = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const chatStreamRef = ref<HTMLElement | null>(null)

// RAG 动态配置
const ragConfig = ref<RagConfig | null>(null)
const embeddingCandidates = ref<RagModelCandidate[]>([])
const rerankerCandidates = ref<RagModelCandidate[]>([])
const configLoading = ref(false)

const previewVisible = ref(false)
const expandedCitations = ref<Set<number>>(new Set())
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
const selectedProvider = ref<string>('')

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

const currentProviderModels = computed(() => {
  if (!selectedProvider.value) {
    // 默认选择第一个接入组
    if (groupedChatModels.value.length > 0) {
      selectedProvider.value = groupedChatModels.value[0].providerName
    }
    return []
  }
  const group = groupedChatModels.value.find(g => g.providerName === selectedProvider.value)
  return group ? group.items : []
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
  checkMobile()
  window.addEventListener('resize', checkMobile)
  restoreWorkspace()
  await Promise.all([store.fetchAllBases(), loadAvailableLlmModels(), loadRagConfig()])
  bootstrapWorkspace()
  scrollToBottom()
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
    scrollToBottom()
  },
  { immediate: true }
)

// 监听消息数量变化自动滚动
watch(
  () => activeSession.value?.messages.length,
  () => scrollToBottom()
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
    const res = await llmProviderApi.listChatModels() as any
    const models = (res?.data ?? res ?? []) as ChatModelOption[]

    availableChatModels.value = models
      .map((model) => ({
        id: model.id,
        providerId: model.providerId,
        providerName: model.providerName,
        name: model.name,
        label: `${model.providerName} / ${model.name}`,
        modelId: model.modelId,
        modelType: model.modelType,
        isDefault: model.isDefault
      }))
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

/** 从后端动态获取 RAG 配置（embedding/reranker 状态 + 知识库参数） */
async function loadRagConfig() {
  configLoading.value = true
  try {
    const res = await getRagConfig() as any
    const cfg = (res?.data ?? res) as RagConfig
    ragConfig.value = cfg
    embeddingCandidates.value = cfg.embeddingCandidates ?? []
    rerankerCandidates.value = cfg.rerankerCandidates ?? []
    // 用后端默认值初始化 topK（如果前端未手动改过）
    if (cfg.searchTopK && topK.value === 10) {
      topK.value = cfg.searchTopK
    }
  } catch {
    ragConfig.value = null
  } finally {
    configLoading.value = false
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

function handleCitationClick(message: RagMessage, citationIndex: number) {
  // 将当前消息设为 insight，使右侧面板显示其引用列表
  activeInsightMessageId.value = message.id

  const citation = message.citations?.[citationIndex]
  if (!citation) return

  if (isRequirementBodyCitation(citation)) {
    void navigateToRequirement(citation)
    return
  }
  if (isCitationPreviewable(citation)) {
    openPreview(citation)
    return
  }

  // 不支持在线预览的文档仍保留展开片段能力。
  // 等待右侧面板渲染完成后展开目标引用并滚动
  nextTick(() => {
    if (citation.documentId != null) {
      expandedCitations.value.add(citation.documentId)
      // 触发响应式更新
      expandedCitations.value = new Set(expandedCitations.value)
    }

    // 滚动到对应的引用项
    const citationList = document.querySelector('.citation-list') as HTMLElement
    if (citationList) {
      const items = citationList.querySelectorAll('.citation-item')
      if (items[citationIndex]) {
        items[citationIndex].scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    }
  })
}

async function copyMessageContent(content: string) {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleAsk() {
  if (asking.value) {
    ElMessage.warning('上一轮回答还在生成中，请稍后再问')
    return
  }

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
  if (!selectedSearchScopes.value.length) {
    ElMessage.warning('请至少选择一个检索范围')
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
  streamingMessageId.value = null
  let streamedAssistantMessage: RagMessage | null = null

  try {
    let assistantMessage: RagMessage | null = null
    const selectedModel = selectedChatModel.value

    await streamSearchKnowledge(
      {
        query: requestQuery,
        mode: searchMode.value,
        knowledgeBaseId: knowledgeBase.id,
        topK: topK.value,
        llmModelId: selectedModel?.id,
        searchScopes: selectedSearchScopes.value
      },
      {
        onResults(response) {
          assistantMessage = createAssistantMessage({
            response,
            question,
            knowledgeBaseName: knowledgeBase.name,
            session,
            streamAnswer: Boolean(selectedModel?.id),
            questionIntent: response.questionIntent ?? null,
            intentConfidence: response.intentConfidence ?? null,
            citations: response.citations?.length
              ? buildCitationsFromBackend(response.citations, question)
              : buildCitations(response.results || [], question),
            warnings: response.warnings || [],
          })
          session.messages.push(assistantMessage)
          session.updatedAt = assistantMessage.createdAt
          activeInsightMessageId.value = assistantMessage.id
          streamingMessageId.value = assistantMessage.id
          streamedAssistantMessage = assistantMessage
        },
        onDelta(delta) {
          if (!assistantMessage) return
          assistantMessage.content += delta
          session.updatedAt = Date.now()
        },
        onDone(response) {
          if (!assistantMessage) {
            assistantMessage = createAssistantMessage({
              response,
              question,
              knowledgeBaseName: knowledgeBase.name,
              session,
              streamAnswer: false,
              warnings: response?.warnings || []
            })
            session.messages.push(assistantMessage)
            activeInsightMessageId.value = assistantMessage.id
          }

          const finalAnswer = response?.answer?.trim()
          if (finalAnswer) {
            assistantMessage.content = finalAnswer
          } else if (!assistantMessage.content.trim()) {
            assistantMessage.content = buildAnswerContent(response, question)
          }
          assistantMessage.processSummary = buildProcessSummary(response, knowledgeBase.name)
          assistantMessage.summaryPoints = extractSummaryPoints(response, question)
          // 优先走后端角标 citations，否则用前端片段兜底
          assistantMessage.citations = response.citations?.length
            ? buildCitationsFromBackend(response.citations, question)
            : buildCitations(response?.results || [], question)
          assistantMessage.warnings = response?.warnings || assistantMessage.warnings || []
          assistantMessage.retrievedCount = response?.total || response?.results?.length || 0
          // 优先使用后端返回的 thinkingSteps，前端仅作兜底
          assistantMessage.thinkingSteps = response.thinkingSteps?.length
            ? response.thinkingSteps
            : buildThinkingStepsFallback({
                knowledgeBaseName: knowledgeBase.name,
                question,
                session,
                mode: searchMode.value,
                response,
                llmModelLabel: selectedModel?.label || null
              })
          assistantMessage.createdAt = Date.now()
          session.updatedAt = assistantMessage.createdAt
        },
        onError(message) {
          throw new Error(message)
        }
      }
    )
  } catch (streamError: any) {
    const streamErrMsg = streamError?.message || ''
    try {
      const streamedMsg = streamedAssistantMessage as any
      if (streamedMsg && streamedMsg.id) {
        const msgId = streamedMsg.id
        session.messages = session.messages.filter(message => message.id !== msgId)
        if (activeInsightMessageId.value === msgId) {
          activeInsightMessageId.value = null
        }
      }
      const response = await store.search(
        requestQuery,
        searchMode.value,
        knowledgeBase.id,
        topK.value,
        selectedChatModel.value?.id,
        selectedSearchScopes.value
      ) as SearchResponse | null
      const assistantMessage = createAssistantMessage({
        response,
        question,
        knowledgeBaseName: knowledgeBase.name,
        session,
        streamAnswer: false,
        questionIntent: response?.questionIntent ?? null,
        intentConfidence: response?.intentConfidence ?? null,
        citations: response?.citations?.length
          ? buildCitationsFromBackend(response.citations, question)
          : buildCitations(response?.results || [], question),
        warnings: response?.warnings || []
      })
      session.messages.push(assistantMessage)
      session.updatedAt = assistantMessage.createdAt
      activeInsightMessageId.value = assistantMessage.id
      ElMessage.warning('流式输出不可用，已切换为普通回答')
    } catch (fallbackError: any) {
      const errorDetail = extractLlmErrorDetail(fallbackError || streamError)
      const errorMessage: RagMessage = {
        id: createId('msg'),
        role: 'assistant',
        content: errorDetail.userMessage,
        createdAt: Date.now(),
        failed: true,
        question,
        processSummary: errorDetail.processSummary,
        summaryPoints: errorDetail.summaryPoints,
        thinkingSteps: [
          { title: '请求提交', detail: '已向检索服务提交本轮问题。' },
          { title: '服务异常', detail: errorDetail.detail },
          { title: '建议处理', detail: errorDetail.suggestion }
        ],
        citations: [],
        retrievedCount: 0,
        llmModelId: selectedChatModel.value?.id || null,
        llmModelLabel: selectedChatModel.value?.label || null
      }
      session.messages.push(errorMessage)
      session.updatedAt = errorMessage.createdAt
      activeInsightMessageId.value = errorMessage.id
      ElMessage.error(errorDetail.toastMessage)
    }
  } finally {
    asking.value = false
    streamingMessageId.value = null
  }
}

/**
 * 解析 LLM / 知识库检索错误，返回用户友好的提示信息
 */
function extractLlmErrorDetail(error: any) {
  const msg = String(error?.message || error?.msg || '').toLowerCase()

  // 余额不足
  if (msg.includes('余额不足') || msg.includes('无可用资源包') || msg.includes('充值')) {
    return {
      userMessage: 'AI 服务余额不足，暂时无法完成检索问答。',
      processSummary: '检索失败：AI 服务余额不足，请充值后重试。',
      summaryPoints: ['AI 服务账户余额已耗尽。', '请联系管理员充值 AI 服务。', '充值完成后即可恢复正常使用。'],
      detail: 'AI 服务（Embedding/Chat）账户余额不足或资源包已耗尽，无法调用向量化和问答接口。',
      suggestion: '请联系管理员前往 AI 服务商控制台充值，充值后即可恢复。',
      toastMessage: 'AI 服务余额不足，请联系管理员充值'
    }
  }

  // 请求频率限制
  if (msg.includes('429') || msg.includes('too many requests') || msg.includes('频繁') || msg.includes('请求过多')) {
    return {
      userMessage: 'AI 服务请求过于频繁，请稍后重试。',
      processSummary: '检索失败：AI 服务请求频率超限。',
      summaryPoints: ['当前请求频率超过了 AI 服务限制。', '请等待 1-2 分钟后重试。', '如持续出现，请联系管理员调整配置。'],
      detail: 'AI 服务返回 429 状态码，表示请求频率超限。',
      suggestion: '请稍等 1-2 分钟后再次提问，避免频繁操作。',
      toastMessage: '请求过于频繁，请稍后重试'
    }
  }

  // 认证失败
  if (msg.includes('认证失败') || msg.includes('api key') || msg.includes('401') || msg.includes('unauthorized')) {
    return {
      userMessage: 'AI 服务认证失败，请检查模型配置。',
      processSummary: '检索失败：AI 服务 API Key 无效或已过期。',
      summaryPoints: ['AI 服务的 API Key 可能无效或已过期。', '请在"模型配置"页面检查并更新 API Key。', '更新后需重新测试连通性。'],
      detail: 'AI 服务返回认证失败，通常是因为 API Key 无效、过期或未配置。',
      suggestion: '请前往"模型配置"页面，检查对应模型的 API Key 是否正确，并测试连通性。',
      toastMessage: 'AI 服务认证失败，请检查模型配置'
    }
  }

  // 服务不可用
  if (msg.includes('暂时不可用') || msg.includes('不可用') || msg.includes('500') || msg.includes('502') || msg.includes('503')) {
    return {
      userMessage: 'AI 服务暂时不可用，请稍后重试。',
      processSummary: '检索失败：AI 服务暂时不可用。',
      summaryPoints: ['AI 服务可能正在维护或过载。', '请等待几分钟后重试。', '如问题持续，请联系管理员。'],
      detail: 'AI 服务返回服务不可用错误，可能是上游服务维护或过载。',
      suggestion: '请稍后重试，如持续出现请联系管理员检查 AI 服务状态。',
      toastMessage: 'AI 服务暂时不可用，请稍后重试'
    }
  }

  // 超时
  if (msg.includes('超时') || msg.includes('timeout')) {
    return {
      userMessage: 'AI 服务响应超时，请稍后重试。',
      processSummary: '检索失败：AI 服务响应超时。',
      summaryPoints: ['AI 服务响应时间过长。', '可能是网络波动或服务负载过高。', '请稍后重试。'],
      detail: 'AI 服务响应超时，可能是网络延迟或上游服务负载过高。',
      suggestion: '请稍后重试，如持续超时请联系管理员检查网络配置。',
      toastMessage: 'AI 服务超时，请稍后重试'
    }
  }

  // 向量化服务异常
  if (msg.includes('向量化') || msg.includes('embedding')) {
    return {
      userMessage: '文本向量化服务异常，请稍后重试。',
      processSummary: '检索失败：文本向量化服务异常。',
      summaryPoints: ['Embedding 模型服务调用失败。', '请检查模型配置中的 Embedding 模型是否正常。', '如问题持续，请联系管理员。'],
      detail: '文本向量化（Embedding）服务调用失败，无法将查询文本转换为向量。',
      suggestion: '请前往"模型配置"页面，检查 Embedding 模型的连通性。如余额不足请充值。',
      toastMessage: '向量化服务异常，请稍后重试'
    }
  }

  // 默认兜底
  return {
    userMessage: '本次检索未能完成，请检查模型配置、知识库索引状态或稍后重试。',
    processSummary: '检索请求失败，当前未返回有效的知识文件证据。',
    summaryPoints: ['请确认知识库已完成索引。', '请检查大模型与向量检索配置。', '如问题持续，请查看后台日志。'],
    detail: '当前接口未返回有效结果，因此未生成文件证据与回答摘要。',
    suggestion: '优先确认模型、向量库和知识库文档状态是否正常。',
    toastMessage: '检索失败，请稍后重试'
  }
}

function createAssistantMessage(options: {
  response: SearchResponse | null | undefined
  question: string
  knowledgeBaseName: string
  session: RagSession
  streamAnswer: boolean
  questionIntent?: string | null
  intentConfidence?: number | null
  citations?: RagCitation[]
  warnings?: string[]
}): RagMessage {
  const response = options.response
  const content = options.streamAnswer ? '' : buildAnswerContent(response, options.question)
  return {
    id: createId('msg'),
    role: 'assistant',
    content,
    createdAt: Date.now(),
    question: options.question,
    questionIntent: options.questionIntent ?? null,
    intentConfidence: options.intentConfidence ?? null,
    processSummary: buildProcessSummary(response, options.knowledgeBaseName),
    summaryPoints: extractSummaryPoints(response, options.question),
    thinkingSteps: buildThinkingStepsFallback({
      knowledgeBaseName: options.knowledgeBaseName,
      question: options.question,
      session: options.session,
      mode: searchMode.value,
      response,
      llmModelLabel: selectedChatModel.value?.label || null
    }),
    citations: options.citations ?? buildCitations(response?.results || [], options.question),
    warnings: options.warnings ?? response?.warnings ?? [],
    retrievedCount: response?.total || response?.results?.length || 0,
    llmModelId: selectedChatModel.value?.id || null,
    llmModelLabel: selectedChatModel.value?.label || null
  }
}

function openPreview(citation: RagCitation) {
  if (!isCitationPreviewable(citation)) {
    ElMessage.info('该文档暂不支持在线预览，可展开查看命中片段')
    return
  }
  previewState.value = {
    knowledgeBaseId: citation.knowledgeBaseId,
    documentId: citation.documentId,
    fileName: citation.fileName,
    fileType: citation.fileType,
    downloadUrl: `/api/v1/knowledge/bases/${citation.knowledgeBaseId}/documents/${citation.documentId}/download`
  }
  previewVisible.value = true
}

function isRequirementBodyCitation(citation: RagCitation) {
  return (citation.sourceType || '').startsWith('requirement_body')
}

function isCitationPreviewable(citation: RagCitation) {
  if (isRequirementBodyCitation(citation) || !citation.documentId || !citation.knowledgeBaseId) {
    return false
  }
  const extension = normalizeFileExtension(citation.fileType || citation.fileName)
  return PREVIEW_SUPPORTED_EXTENSION_SET.has(extension)
}

function resolveCitationActionLabel(citation: RagCitation) {
  if (isRequirementBodyCitation(citation)) return '查看工单'
  return isCitationPreviewable(citation) ? '预览' : '展开片段'
}

function resolveCitationActionTitle(citation: RagCitation) {
  if (isRequirementBodyCitation(citation)) return '打开工单详情'
  return isCitationPreviewable(citation) ? '预览引用文档' : '该文档暂不支持在线预览，可展开查看命中片段'
}

function citationKey(citation: RagCitation) {
  return [
    citation.documentId ?? 'requirement',
    citation.requirementId ?? '',
    citation.requirementNo ?? '',
    citation.fileName ?? '',
  ].join('-')
}

function formatCitationTitle(citation: RagCitation) {
  if (!isRequirementBodyCitation(citation)) {
    return citation.fileName
  }
  const requirementNo = citation.requirementNo?.trim() || ''
  const requirementTitle = citation.requirementTitle?.trim() || citation.fileName || '未命名工单'
  return requirementNo ? `${requirementNo} ${requirementTitle}` : requirementTitle
}

function resolveCitationTypeLabel(citation: RagCitation) {
  if (citation.contentType === 'image_ocr') return '图片 OCR'
  if (citation.contentType === 'image_caption') return '图片理解'
  if (citation.contentType === 'body_image') return '正文 + 图片'
  return '工单正文'
}

async function navigateToRequirement(citation: RagCitation) {
  if (!citation.requirementId) {
    ElMessage.warning('该引用缺少工单标识，暂时无法跳转')
    return
  }
  previewVisible.value = false
  try {
    await router.push({
      name: 'RequirementDetail',
      params: { id: citation.requirementId },
      query: citation.imageFileId
        ? {
            focus: citation.focus || 'image',
            fileId: String(citation.imageFileId),
            ...(citation.imagePosition ? { position: String(citation.imagePosition) } : {})
          }
        : undefined,
    })
  } catch (error: any) {
    ElMessage.error(error?.message || '工单详情打开失败，请确认你有权限查看该工单')
  }
}

function handleCitationPrimaryAction(citation: RagCitation) {
  if (isRequirementBodyCitation(citation)) {
    void navigateToRequirement(citation)
  } else if (isCitationPreviewable(citation)) {
    openPreview(citation)
  } else {
    toggleCitationExpand(citation)
  }
}

function handleCitationHeaderClick(citation: RagCitation) {
  if (isRequirementBodyCitation(citation)) {
    void navigateToRequirement(citation)
  } else if (isCitationPreviewable(citation)) {
    openPreview(citation)
  } else {
    toggleCitationExpand(citation)
  }
}

function toggleCitationExpand(citation: RagCitation) {
  const id = citation.documentId
  if (expandedCitations.value.has(id)) {
    expandedCitations.value.delete(id)
  } else {
    expandedCitations.value.add(id)
  }
  // 触发响应式更新
  expandedCitations.value = new Set(expandedCitations.value)
}

function getCitationChunks(citation: RagCitation): CitationChunk[] {
  if (citation.chunks?.length) {
    return citation.chunks
  }
  // 兜底：从 excerpt 构建单片段
  if (citation.excerpt) {
    return [{
      content: citation.excerpt,
      sectionTitle: citation.sectionTitle,
      pageNum: citation.pageText ? parseInt(citation.pageText.replace(/[^0-9]/g, '')) || undefined : undefined
    }]
  }
  return []
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

  if (response?.results?.length) {
    return `已命中 ${response.total || response.results.length} 条相关片段，请查看右侧「涉及文件」列表了解详情。`
  }
  return '当前没有检索到相关文档片段，建议换一个描述方式或补充更明确的关键词。'
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

function buildThinkingStepsFallback(params: {
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

function buildCitations(results: SearchResultItem[], question: string): RagCitation[] {
  // 若传入 results 为空，返回空
  if (!results?.length) return []

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
        sectionPool: item.sectionTitle ? [item.sectionTitle] : [],
        sourceType: item.requirement ? 'requirement_body' : null,
        requirementId: item.requirement?.id ?? null,
        requirementNo: item.requirement?.requirementNo ?? null,
        requirementTitle: item.requirement?.title ?? null,
        contentType: item.sectionTitle?.includes('OCR')
          ? 'image_ocr'
          : item.sectionTitle?.includes('图片理解') ? 'image_caption' : 'body',
        imageFileId: item.imageFileId ?? null,
        imagePosition: item.imagePosition ?? null,
        focus: item.focus ?? null
      })
      return
    }

    existing.hitCount += 1
    if (item.score >= existing.score) {
      existing.imageFileId = item.imageFileId ?? existing.imageFileId
      existing.imagePosition = item.imagePosition ?? existing.imagePosition
      existing.focus = item.focus ?? existing.focus
      if (item.sectionTitle?.includes('OCR')) existing.contentType = 'image_ocr'
      else if (item.sectionTitle?.includes('图片理解')) existing.contentType = 'image_caption'
    }
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
      const chunks: CitationChunk[] = textPool.map((content, idx) => {
        const sections = sectionPool[idx] || ''
        const pageNums = Array.from(_pageSet || new Set<number>())
        return {
          content,
          sectionTitle: sections || undefined,
          pageNum: pageNums[idx % pageNums.length] || undefined,
          score: undefined
        }
      })
      const excerpt = extractCoreExcerpt(textPool, question, preferredSection || citation.sectionTitle || '')
      return {
        ...citation,
        sectionTitle: preferredSection || citation.sectionTitle,
        excerpt,
        chunks
      }
    })
    .sort((a, b) => b.score - a.score)
}

/**
 * 使用后端角标引用列表构建 citations 面板条目。
 * 后端角标按 documentId 聚合、按相关度降序、带连续序号。
 * 折叠摘要由回答正文内的 [N] 角标负责，不在 citations 面板重复片段原文。
 */
function buildCitationsFromBackend(citations: CitationReference[], question: string): RagCitation[] {
  return citations.map((c) => ({
    knowledgeBaseId: Number(c.knowledgeBaseId || selectedKbId.value || 0),
    documentId: c.documentId,
    fileName: c.fileName,
    fileType: detectFileType(c.fileName),
    sectionTitle: '',
    excerpt: '',
    score: c.maxScore,
    hitCount: c.hitCount,
    pageText: `${c.hitCount} 个片段`,
    chunks: undefined,
    sourceType: c.sourceType,
    requirementId: c.requirementId,
    requirementNo: c.requirementNo,
    requirementTitle: c.requirementTitle,
    contentType: c.contentType,
    imageFileId: c.imageFileId ?? null,
    imagePosition: c.imagePosition ?? null,
    focus: c.focus ?? null,
  }))
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

function compactModelName(model: RagModelOption) {
  const source = (model.name || model.modelId || model.label).trim()
  const compact = source.replace(/^(gpt|claude|glm|qwen|deepseek|gemini)[-_:\s]*/i, '').trim()
  return compact || source
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

/** 获取消息的检索数量 */
function retrievedCountForMessage(message: RagMessage): number {
  if (message.role === 'assistant') return message.retrievedCount || 0
  // 对于用户消息，查找下一条助手消息
  const session = activeSession.value
  if (!session) return 0
  const idx = session.messages.findIndex(m => m.id === message.id)
  const nextAssistant = session.messages.slice(idx + 1).find(m => m.role === 'assistant')
  return nextAssistant?.retrievedCount || 0
}

/** 判断是否是每组消息中的第一条助手消息（用于显示简介） */
function isFirstAssistantInGroup(message: RagMessage): boolean {
  const session = activeSession.value
  if (!session || message.role !== 'assistant') return false
  const idx = session.messages.findIndex(m => m.id === message.id)
  if (idx <= 0) return true
  // 检查前面最近的消息是不是用户消息且没有其他助手消息间隔
  const prevMessages = session.messages.slice(0, idx)
  const lastUserIdx = [...prevMessages].reverse().findIndex(m => m.role === 'user')
  const lastAssistantIdx = [...prevMessages].reverse().findIndex(m => m.role === 'assistant')
  return lastAssistantIdx < 0 || lastUserIdx < lastAssistantIdx
}

/** textarea 自适应高度 */
function autoResizeTextarea() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 200) + 'px'
}

/** 检测移动端 */
function checkMobile() {
  isMobile.value = window.innerWidth < 900
  if (isMobile.value) {
    ragSidebar.collapsed = true
  }
}

/** 滚动聊天区到底部 */
function scrollToBottom() {
  nextTick(() => {
    const el = chatStreamRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}
</script>

<style scoped lang="scss">
/* ====== 布局 ====== */
.rag-workspace {
  display: flex;
  gap: 0;
  min-height: calc(100vh - 200px);
  position: relative;
  background: var(--color-surface, #ffffff);
  border-radius: 12px;
  border: 1px solid var(--color-border, #e5e7eb);
  overflow: hidden;
}

/* ====== 左侧边栏 ====== */
.rag-sidebar {
  width: var(--rag-sidebar-width, 280px);
  min-width: var(--rag-sidebar-width, 280px);
  border-right: 1px solid var(--color-border, #e5e7eb);
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  transition: width 0.25s ease, min-width 0.25s ease, opacity 0.2s ease;
  position: relative;
  z-index: 10;

  &.is-collapsed {
    width: 0;
    min-width: 0;
    overflow: hidden;
    border-right: none;
    opacity: 0;
  }

  @media (max-width: 900px) {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    z-index: 100;
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.12);

    &.is-collapsed {
      opacity: 0;
      pointer-events: none;
    }

    &.is-visible:not(.is-collapsed) {
      opacity: 1;
      pointer-events: auto;
    }
  }
}

.rag-sidebar__overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: -1;
}

.rag-sidebar__panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.rag-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.rag-sidebar__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary, #1a1a1a);
}

.rag-sidebar__collapse-trigger {
  padding: 4px;
  color: var(--color-text-secondary, #6b7280);
  border-radius: 6px;

  &:hover {
    color: var(--color-accent, #2563eb);
    background: rgba(37, 99, 235, 0.08);
  }
}

.sidebar-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rag-sidebar-expand-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border, #e5e7eb);
  border-left: none;
  border-radius: 0 8px 8px 0;
  background: #fff;
  cursor: pointer;
  z-index: 5;
  color: var(--color-text-secondary, #6b7280);

  &:hover {
    background: #f5f7fa;
    color: var(--color-accent, #2563eb);
  }
}

/* 侧边栏内部 */
.sidebar-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 12px;
}

.sidebar-section--fill {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}

.knowledge-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.knowledge-card {
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
  color: var(--color-text-primary);

  &:hover {
    background: rgba(37, 99, 235, 0.05);
  }

  &--active {
    background: rgba(37, 99, 235, 0.08);
    border-color: rgba(37, 99, 235, 0.25);
  }
}

.knowledge-card__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 13px;
}

.knowledge-card__desc {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-secondary, #6b7280);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-card__meta {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--color-text-placeholder, #9ca3af);
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  overflow-y: auto;
  margin-top: 4px;
}

.session-item {
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  transition: all 0.15s ease;
  color: var(--color-text-primary);

  &:hover {
    background: rgba(0, 0, 0, 0.03);
  }

  &--active {
    background: rgba(37, 99, 235, 0.08);
    border-color: rgba(37, 99, 235, 0.2);
  }
}

.session-item__main {
  min-width: 0;
  flex: 1;
}

.session-item__title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__meta {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--color-text-placeholder, #9ca3af);
}

.session-empty {
  font-size: 12px;
  color: var(--color-text-placeholder, #9ca3af);
  text-align: center;
  padding: 16px 8px;
}

/* ====== 主对话区 ====== */
.rag-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #ffffff;
  position: relative;
}

/* 顶部栏 */
.chat-topbar {
  padding: 20px 24px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
}

.chat-topbar__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary, #1a1a1a);
  margin: 0 0 12px 0;
  line-height: 1.3;
}

.chat-topbar__prompt {
  max-width: 560px;

  :deep(.el-input__wrapper) {
    border-radius: 8px;
    background: rgba(0, 0, 0, 0.03);
    box-shadow: none;
    border: 1px solid rgba(0, 0, 0, 0.08);
    padding: 4px 12px;

    &:hover {
      border-color: rgba(0, 0, 0, 0.15);
    }
  }

  :deep(.el-input__inner) {
    font-size: 13px;
    color: var(--color-text-secondary, #6b7280);
  }
}

/* 对话流区域 */
.chat-stream {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  scroll-behavior: smooth;
}

/* 空状态 */
.chat-empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.chat-empty-state__icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.7;
}

.chat-empty-state__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary, #1a1a1a);
  margin-bottom: 8px;
}

.chat-empty-state__desc {
  font-size: 14px;
  color: var(--color-text-secondary, #6b7280);
  max-width: 400px;
  line-height: 1.6;
}

/* ====== 消息行 ====== */
.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  animation: msgFadeIn 0.3s ease-out;

  &--user {
    flex-direction: row;
  }

  &--assistant {
    flex-direction: row;
  }
}

@keyframes msgFadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 头像 */
.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;

  &--user {
    background: linear-gradient(135deg, #f0f0f0, #e0e0e0);
    color: #555;

    svg {
      width: 20px;
      height: 20px;
    }
  }

  &--assistant {
    background: linear-gradient(135deg, #2563eb, #4f46e5);
    color: #fff;
  }

  &__icon {
    font-weight: 700;
    font-size: 15px;
  }
}

/* 消息体 */
.msg-body {
  flex: 1;
  min-width: 0;
  max-width: calc(100% - 48px);
}

/* 用户消息样式 */
.msg-search-ref {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.06);
  border: 1px solid rgba(37, 99, 235, 0.15);
  color: var(--color-accent, #2563eb);
  font-size: 13px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 14px;
  }

  &:hover {
    background: rgba(37, 99, 235, 0.1);
    border-color: rgba(37, 99, 235, 0.3);
  }
}

.msg-text {
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-primary, #1a1a1a);
  word-break: break-word;

  &--user {
    color: var(--color-text-primary, #1a1a1a);
  }
}

/* 助手消息头部 */
.msg-assistant-header {
  margin-bottom: 4px;
}

.msg-assistant-name {
  font-weight: 700;
  font-size: 14px;
  color: var(--color-text-primary, #1a1a1a);
}

.msg-assistant-intro {
  font-size: 13px;
  color: var(--color-text-secondary, #6b7280);
  line-height: 1.6;
  margin-bottom: 10px;
}

/* 交付物卡片 */
.msg-deliverable-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 8px;
  background: rgba(16, 185, 129, 0.06);
  border: 1px solid rgba(16, 185, 129, 0.2);
  color: #059669;
  font-size: 13px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: all 0.15s ease;

  .el-icon {
    font-size: 14px;
  }

  &:hover {
    background: rgba(16, 185, 129, 0.1);
    border-color: rgba(16, 185, 129, 0.35);
  }
}

/* 内容卡片 */
.msg-content-card {
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fafbfc;
  overflow: hidden;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    border-color: rgba(0, 0, 0, 0.14);
  }

  &--error {
    border-color: rgba(239, 68, 68, 0.25);
    background: rgba(239, 68, 68, 0.04);
  }

  &--active {
    border-color: rgba(37, 99, 235, 0.35);
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
    cursor: pointer;
  }

  &--loading {
    padding: 24px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
}

.msg-content-card__title {
  padding: 10px 16px;
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-primary, #1a1a1a);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(0, 0, 0, 0.02);
}

.msg-content-card__body {
  padding: 16px;
  line-height: 1.8;
  font-size: 14px;
  color: var(--color-text-primary, #1a1a1a);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 消息底部信息 */
.msg-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-placeholder, #9ca3af);
}

/* 加载动画 */
.thinking-loader {
  display: inline-flex;
  gap: 6px;
}

.thinking-loader__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent, #2563eb);
  animation: dotPulse 1.2s ease-in-out infinite;

  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

@keyframes dotPulse {
  0%, 100% { opacity: 0.35; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

.thinking-loader__text {
  font-size: 13px;
  color: var(--color-text-secondary, #6b7280);
}

/* ====== 底部输入区 ====== */
.composer-area {
  padding: 16px 24px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  background: #fafbfc;
}

.composer-box {
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: #fff;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus-within {
    border-color: rgba(37, 99, 235, 0.4);
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.08);
  }

  &--disabled {
    opacity: 0.5;
    pointer-events: none;
  }
}

.composer-textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 14px 16px;
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text-primary, #1a1a1a);
  background: transparent;
  font-family: inherit;
  min-height: 52px;
  max-height: 200px;
  box-sizing: border-box;

  &::placeholder {
    color: var(--color-text-placeholder, #9ca3af);
  }

  &:disabled {
    cursor: not-allowed;
  }
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  gap: 8px;
}

.composer-toolbar__left,
.composer-toolbar__right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary, #6b7280);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;

  .el-icon { font-size: 15px; }

  &:hover:not(:disabled) {
    background: rgba(0, 0, 0, 0.05);
    color: var(--color-text-primary, #1a1a1a);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &--model {
    font-weight: 500;
    color: var(--color-text-primary, #1a1a1a);
    border: 1px solid rgba(0, 0, 0, 0.08);
    padding: 4px 10px;
    border-radius: 6px;

    &:hover:not(:disabled) {
      background: rgba(0, 0, 0, 0.03);
      border-color: rgba(0, 0, 0, 0.15);
    }
  }

  &--icon {
    padding: 6px;
  }
}

.send-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: var(--color-accent, #2563eb);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;

  .el-icon { font-size: 16px; }

  &:hover:not(:disabled) {
    background: #1d4ed8;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.composer-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;

  > span:first-child {
    font-size: 12px;
    color: var(--color-text-placeholder, #9ca3af);
  }
}

.composer-hint__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ghost-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-secondary, #6b7280);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover:not(:disabled) {
    background: rgba(0, 0, 0, 0.05);
    color: var(--color-text-primary);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

/* ====== 右侧证据面板（浮层）====== */
.rag-insights-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 380px;
  background: #fff;
  border-left: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: -4px 0 32px rgba(0, 0, 0, 0.12);
  z-index: 50;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.insights-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.insights-panel__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary, #1a1a1a);
}

.insights-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.insight-section {
  background: #f9fafb;
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 14px;
}

.insight-section__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.insight-section__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);
}

.insight-section__tags {
  display: flex;
  gap: 6px;
}

.insight-summary {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-secondary, #6b7280);
  margin: 0;
}

.keypoint-list {
  padding-left: 18px;
  margin: 4px 0 0 0;
  display: flex;
  flex-direction: column;
  gap: 6px;

  li {
    font-size: 13px;
    line-height: 1.65;
    color: var(--color-text-primary, #1a1a1a);
  }
}

.citation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.citation-item {
  border-radius: 8px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.07);
  overflow: hidden;
  transition: all 0.15s ease;

  &:hover {
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  }

  &--expanded {
    border-color: rgba(37, 99, 235, 0.25);
  }

  &--requirement {
    border-color: rgba(37, 99, 235, 0.22);
    background: rgba(239, 246, 255, 0.72);
  }
}

.citation-item__header {
  padding: 10px 12px;
  cursor: pointer;
}

.citation-item--previewable .citation-item__header,
.citation-item--requirement .citation-item__header {
  cursor: pointer;
}

.citation-item--previewable .citation-item__header:hover,
.citation-item--requirement .citation-item__header:hover {
  background: rgba(239, 246, 255, 0.72);
}

.citation-item__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary, #1a1a1a);

  .el-icon { font-size: 14px; color: var(--color-text-secondary); }
}

.citation-item__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary, #6b7280);
}

.citation-item__chunks {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding: 10px 12px;
  background: rgba(0, 0, 0, 0.01);
}

.citation-chunk {
  padding: 8px 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--color-text-secondary, #6b7280);

  :deep(mark) {
    background: rgba(37, 99, 235, 0.15);
    color: inherit;
    border-radius: 2px;
    padding: 0 2px;
  }
}

/* 证据面板触发按钮 */
.insights-trigger-btn {
  position: fixed;
  right: 24px;
  bottom: 140px;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
  color: var(--color-text-secondary, #6b7280);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
  z-index: 40;

  .el-icon { font-size: 18px; }

  &:hover {
    color: var(--color-accent, #2563eb);
    border-color: rgba(37, 99, 235, 0.3);
    box-shadow: 0 4px 16px rgba(37, 99, 235, 0.15);
    transform: scale(1.05);
  }
}

.insights-trigger-btn__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--color-accent, #2563eb);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

/* 面板滑入动画 */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ====== Popover 菜单（复用原有逻辑）====== */
.composer-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-menu__title {
  color: var(--color-text-secondary, #6b7280);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.composer-menu__item {
  width: 100%;
  border: none;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--color-text-primary, #1a1a1a);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;

  &:hover {
    background: rgba(37, 99, 235, 0.08);
  }

  &--active {
    background: rgba(37, 99, 235, 0.1);
  }
}

.composer-menu__label {
  font-size: 13px;
  font-weight: 600;
}

.composer-menu__hint {
  font-size: 11px;
  color: var(--color-text-secondary, #6b7280);
  margin-top: 2px;
}

.composer-menu__check {
  font-size: 16px;
  color: var(--color-accent, #2563eb);
  font-weight: 700;
}

.composer-menu--two-level {
  gap: 12px;
}

.composer-menu__two-level {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 12px;
  min-height: 300px;
  max-height: 400px;
}

.composer-menu__provider-list,
.composer-menu__model-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.composer-menu__section-title {
  color: var(--color-text-secondary, #6b7280);
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 6px;
  padding: 0 4px;
}

.composer-menu__provider-item {
  width: 100%;
  border: 1px solid transparent;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--color-text-primary, #1a1a1a);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;

  &:hover {
    background: rgba(37, 99, 235, 0.06);
    border-color: rgba(37, 99, 235, 0.15);
  }

  &--active {
    background: rgba(37, 99, 235, 0.1);
    border-color: var(--color-accent, #2563eb);
  }
}

.composer-menu__provider-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-menu__provider-count {
  font-size: 11px;
  color: var(--color-text-secondary, #6b7280);
}

.composer-menu__model-list {
  border-left: 1px solid rgba(0, 0, 0, 0.06);
  padding-left: 10px;
}

/* ====== Deep Selectors ====== */
.rag-workspace :deep(.el-empty__description p) {
  color: var(--color-text-secondary, #6b7280);
}

.rag-workspace :deep(.rag-composer-popover.el-popover) {
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  padding: 12px;
}

.rag-workspace :deep(.el-button.is-text) {
  padding: 0;
}

/* ====== 响应式 ====== */
@media (max-width: 900px) {
  .chat-topbar {
    padding: 16px;
  }

  .chat-stream {
    padding: 16px;
  }

  .composer-area {
    padding: 12px 16px 16px;
  }

  .rag-insights-panel {
    width: 100%;
  }

  .msg-body {
    max-width: calc(100% - 40px);
  }

  .insights-trigger-btn {
    right: 16px;
    bottom: 120px;
  }
}

@media (max-width: 600px) {
  .composer-toolbar {
    flex-wrap: wrap;
    gap: 4px;
  }

  .composer-hint {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-hint__actions {
    flex-wrap: wrap;
  }

  .msg-content-card__body {
    padding: 12px;
    font-size: 13px;
  }
}
</style>
