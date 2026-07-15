import request from '../request'

// ---- Provider ----

export interface LlmProvider {
  id?: number
  name: string
  protocol: 'openai' | 'anthropic'
  baseUrl: string
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
}

export interface LlmApplicationUpdateForm {
  modelId: number | null
  enabled?: boolean
}

export interface ChatModelOption {
  id: number
  providerId: number
  providerName: string
  name: string
  modelId: string
  modelType: string
  isDefault: boolean
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

  // Chat Models (for RAG)
  listChatModels: () => request.get<ChatModelOption[]>('/v1/llm-providers/chat-models'),

  // Translate (for role code generation)
  translate: (text: string) => request.post<string | null>('/v1/llm-providers/translate', { text }, { timeout: 15000 }),

  // Sniff
  sniffModels: (id: number) =>
    request.post<SniffedModel[]>(`/v1/llm-providers/${id}/sniff-models`, null, { timeout: 30000 }),
}
