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
  /** 知识库文档 ID（知识库检索来源时有效） */
  documentId?: number | null
  /** 知识库 ID（知识库检索来源时有效） */
  knowledgeBaseId?: number | null
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

/** 任务日志行 */
export interface TaskLog {
  timestamp: number
  level: string  // 'info' | 'warn' | 'error'
  message: string
}

/** 检索任务节点（对标 WorkBuddy 任务列表） */
export interface AssistantTask {
  id: string
  title: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  startedAt?: number | null
  completedAt?: number | null
  logs: TaskLog[]
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
  /** 任务列表（检索过程） */
  tasks?: AssistantTask[]
  processSummary?: string | null
  retrievedCount?: number | null
  citations?: CitationReference[]
  /** 深度思考内容（LLM reasoning，可为 null） */
  reasoning?: string | null
  /** 输入（提示词）token 数 */
  inputTokens?: number | null
  /** 输出（生成）token 数 */
  outputTokens?: number | null
  /** 总 token 数 */
  totalTokens?: number | null
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
  /**
   * 是否启用联网搜索（仅通用助手模式 knowledgeBaseId=null 下生效）。
   * 开启后：轻量检索全部本地知识库 + LLM 联网搜索，综合给出结果。
   */
  webSearch?: boolean
  /** 上传的文件附件（文件ID + 客户端提取的文本内容） */
  files?: AssistantFileAttachment[]
}

export interface AssistantFileAttachment {
  fileId: number
  name: string
  size: number
  contentType: string
  extractedText?: string | null
}

export interface AssistantMetaPayload {
  sessionId: number
  userMessageId?: number
  assistantMessageId?: number
}

export interface AssistantUsage {
  inputTokens?: number | null
  outputTokens?: number | null
  totalTokens?: number | null
}

export interface AssistantActionPayload {
  intent?: string | null
  actions: AssistantAction[]
  sources: AssistantSource[]
  /** 任务列表（知识库问答时携带） */
  tasks?: AssistantTask[]
}
