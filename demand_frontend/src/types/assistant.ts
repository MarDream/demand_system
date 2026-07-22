export interface AssistantAction {
  type: string
  label: string
  description?: string
  targetPath?: string
  permission?: string | null
}

export interface AssistantSource {
  code?: string
  title?: string
  path?: string
  reason?: string
}

export interface AssistantPageContext {
  route?: string
  routeName?: string
  pageTitle?: string
  activeMenu?: string
  entityType?: string
  entityId?: string
}

export type AssistantMessageId = number | string

export interface ThinkingStep {
  stepType: string
  title: string
  detail: string
  score?: number
  metadata?: Record<string, any>
}

export interface CitationReference {
  index?: number
  documentId?: number
  fileName?: string
  hitCount?: number
  maxScore?: number
  sources?: string[]
}

export interface AssistantMessage {
  id: AssistantMessageId
  sessionId: number
  role: "user" | "assistant"
  content: string
  status?: string
  intent?: string | null
  pageContext?: AssistantPageContext | null
  actions: AssistantAction[]
  sources: AssistantSource[]
  thinkingSteps?: ThinkingStep[]
  processSummary?: string | null
  retrievedCount?: number | null
  citations?: CitationReference[]
  createdAt?: string
}

export interface AssistantSession {
  id: number
  title: string
  lastMessagePreview?: string | null
  lastMessageAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface AssistantChatRequest {
  message: string
  pageContext?: AssistantPageContext
  /**
   * 知识库问答范围：
   * - null/undefined：通用操作助手（不检索知识库）
   * - -1：全部知识库（跨库 RAG 问答）
   * - 具体正值：仅检索指定知识库
   */
  knowledgeBaseId?: number | null
  /** 指定聊天模型 ID，null/undefined 由后端自动选取默认模型 */
  llmModelId?: number | null
  /**
   * 知识库检索模式（仅知识库问答时生效）：
   * - hybrid：混合检索（语义+关键词，默认）
   * - semantic：纯语义检索
   * - keyword：纯关键词检索
   */
  mode?: 'hybrid' | 'semantic' | 'keyword'
  /**
   * 知识库检索召回片段数量（仅知识库问答时生效）
   * 默认 10
   */
  topK?: number
}

export interface AssistantMetaPayload {
  sessionId: number
  userMessageId?: number
  assistantMessageId?: number
}

export interface AssistantActionPayload {
  intent?: string | null
  actions: AssistantAction[]
  sources: AssistantSource[]
}
