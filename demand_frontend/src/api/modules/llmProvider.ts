import request from '../request'

// ---- Provider ----

export interface LlmProvider {
  id?: number
  name: string
  protocol: 'openai' | 'anthropic'
  baseUrl: string
  websiteUrl?: string | null
  maskedApiKey: string
  enabled: boolean
  models?: LlmModel[]
  createdAt?: string
  updatedAt?: string
}

export interface LlmProviderForm {
  name: string
  protocol: string
  baseUrl: string
  websiteUrl?: string | null
  apiKey: string
  enabled: boolean
}

// ---- Model ----

export interface LlmModel {
  id?: number
  providerId: number
  name: string
  modelId: string
  modelType: string
  dimension?: number | null
  contextWindow?: number | null
  ownedBy?: string | null
  modelCreated?: number | null
  temperature: number
  maxTokens: number
  isDefault: boolean
  enabled: boolean
  testSuccess: boolean | null
  testDuration: number | null
  testError: string | null
  testAt: string | null
  /** 最近测试响应内容（完整响应文本） */
  testContent: string | null
  /** 最近测试请求 Token 数 */
  testPromptTokens: number | null
  /** 最近测试响应 Token 数 */
  testCompletionTokens: number | null
  /** 最近测试总 Token 数 */
  testTotalTokens: number | null
  /** 最近测试实际响应的模型名 */
  testResponseModel: string | null
  /** 文本分块大小（仅 embedding 模型使用） */
  chunkSize?: number | null
  /** 文本分块重叠大小（仅 embedding 模型使用） */
  chunkOverlap?: number | null
  /** 检索返回 TopK（仅 embedding 模型使用） */
  searchTopK?: number | null
  /** 完整测试结果（前端缓存，非后端字段） */
  testResult?: LlmTestResult | null
  createdAt?: string
  updatedAt?: string
}

export interface LlmModelForm {
  name: string
  modelId: string
  modelType: string
  dimension?: number | null
  contextWindow?: number | null
  ownedBy?: string | null
  modelCreated?: number | null
  temperature: number
  maxTokens: number
  isDefault: boolean
  enabled: boolean
  /** 文本分块大小（仅 embedding 模型使用） */
  chunkSize?: number | null
  /** 文本分块重叠大小（仅 embedding 模型使用） */
  chunkOverlap?: number | null
  /** 检索返回 TopK（仅 embedding 模型使用） */
  searchTopK?: number | null
}

// ---- Test ----

export interface LlmTestRequest {
  userMessage: string
  systemPrompt?: string
}

export interface LlmTestResult {
  success: boolean
  content: string | null
  errorMessage: string | null
  durationMs: number
  promptTokens: number | null
  completionTokens: number | null
  totalTokens: number | null
  model: string | null
}

export interface SniffedModel {
  modelId: string
  ownedBy: string | null
  contextWindow: number | null
  created: number | null
  alreadyExists: boolean
  inferredType: string
}


export interface LlmApplication {
  id: number
  code: string
  name: string
  description?: string | null
  modelType: 'chat' | 'embedding' | 'rerank' | string
  modelId?: number | null
  modelName?: string | null
  modelCode?: string | null
  providerName?: string | null
  modelAvailable: boolean
  enabled: boolean
  sortOrder: number
  /** 所属分组ID，null=未分组 */
  groupId?: number | null
}

export interface LlmApplicationUpdateForm {
  modelId: number | null
  enabled?: boolean
}

// ---- 功能点分组（目录树） ----

export interface LlmApplicationGroup {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  /** 该分组下直接挂载的功能点数量（不含子分组） */
  applicationCount?: number
  /** 该分组及其子孙分组下的功能点总数 */
  totalApplicationCount?: number
  children?: LlmApplicationGroup[]
}

export interface LlmApplicationGroupCreateForm {
  name: string
  parentId?: number | null
}

export interface LlmApplicationGroupMoveForm {
  parentId: number | null
  sortOrder?: number | null
}

export interface ChatModelOption {
  id: number
  providerId: number
  providerName: string
  name: string
  modelId: string
  modelType: string
  isDefault: boolean
  /** 是否为模型应用(assistant.chat)绑定的默认模型 */
  appDefault?: boolean
  /** 最近一次连通性测试结果，null 表示从未测试 */
  testSuccess?: boolean | null
  testDuration?: number | null
}

// ---- Column Config (re-export from common) ----
export { getColumnConfig, saveColumnConfig } from './columnConfig'

// ---- API ----

export const llmProviderApi = {
  // Provider
  list: () => request.get<LlmProvider[]>('/v1/llm-providers'),
  getById: (id: number) => request.get<LlmProvider>(`/v1/llm-providers/${id}`),
  create: (data: LlmProviderForm) => request.post<LlmProvider>('/v1/llm-providers', data),
  update: (id: number, data: LlmProviderForm) => request.put<LlmProvider>(`/v1/llm-providers/${id}`, data),
  delete: (id: number) => request.delete(`/v1/llm-providers/${id}`),
  toggle: (id: number) => request.patch(`/v1/llm-providers/${id}/toggle`),
  getApiKey: (id: number) => request.get<{ apiKey: string }>(`/v1/llm-providers/${id}/api-key`),

  // Model
  addModel: (providerId: number, data: LlmModelForm) =>
    request.post<LlmModel>(`/v1/llm-providers/${providerId}/models`, data),
  updateModel: (providerId: number, modelId: number, data: LlmModelForm) =>
    request.put<LlmModel>(`/v1/llm-providers/${providerId}/models/${modelId}`, data),
  deleteModel: (providerId: number, modelId: number) =>
    request.delete(`/v1/llm-providers/${providerId}/models/${modelId}`),
  toggleModel: (providerId: number, modelId: number) =>
    request.patch(`/v1/llm-providers/${providerId}/models/${modelId}/toggle`),
  toggleDefault: (providerId: number, modelId: number) =>
    request.patch(`/v1/llm-providers/${providerId}/models/${modelId}/toggle-default`),
  testModel: (providerId: number, modelId: number, data: LlmTestRequest) =>
    request.post<LlmTestResult>(`/v1/llm-providers/${providerId}/models/${modelId}/test`, data, { timeout: 60000 }),

  // Roles
  getRoles: () => request.get<string[]>('/v1/llm-providers/models/roles'),

  // 应用功能点模型配置
  listApplications: () => request.get<LlmApplication[]>('/v1/llm-applications'),
  updateApplication: (code: string, data: LlmApplicationUpdateForm) =>
    request.put<LlmApplication>(`/v1/llm-applications/${encodeURIComponent(code)}`, data),
  moveApplicationToGroup: (code: string, groupId: number | null) =>
    request.put<LlmApplication>(`/v1/llm-applications/${encodeURIComponent(code)}/group`, { groupId }),

  // 功能点分组（目录树，模型应用页左侧）
  listApplicationGroups: () => request.get<LlmApplicationGroup[]>('/v1/llm-application-groups/tree'),
  createApplicationGroup: (data: LlmApplicationGroupCreateForm) =>
    request.post<number>('/v1/llm-application-groups', data),
  renameApplicationGroup: (id: number, name: string) =>
    request.put(`/v1/llm-application-groups/${id}`, { name }),
  moveApplicationGroup: (id: number, data: LlmApplicationGroupMoveForm) =>
    request.put(`/v1/llm-application-groups/${id}/move`, data),
  deleteApplicationGroup: (id: number) => request.delete(`/v1/llm-application-groups/${id}`),

  // Chat Models (for RAG)
  listChatModels: () => request.get<ChatModelOption[]>('/v1/llm-providers/chat-models'),

  // Translate (for role code generation)
  translate: (text: string) => request.post<string | null>('/v1/llm-providers/translate', { text }, { timeout: 15000 }),

  // Sniff
  sniffModels: (id: number) =>
    request.post<SniffedModel[]>(`/v1/llm-providers/${id}/sniff-models`, null, { timeout: 30000 }),

  // 保存前测试接入配置连通性（不落库）
  testProviderConfig: (data: { providerId?: number; protocol: string; baseUrl: string; apiKey?: string }) =>
    request.post<LlmTestResult>('/v1/llm-providers/test-config', data, { timeout: 60000 }),
}
