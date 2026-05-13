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
  temperature: number
  maxTokens: number
  isDefault: boolean
  enabled: boolean
  testSuccess: boolean | null
  testDuration: number | null
  testError: string | null
  testAt: string | null
  createdAt?: string
  updatedAt?: string
}

export interface LlmModelForm {
  name: string
  modelId: string
  modelType: string
  temperature: number
  maxTokens: number
  isDefault: boolean
  enabled: boolean
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
  alreadyExists: boolean
}

// ---- Column Config ----

export function getColumnConfig(pageKey: string) {
  return request.get<string[]>(`/v1/column-config/${pageKey}`)
}

export function saveColumnConfig(pageKey: string, columns: string[]) {
  return request.put(`/v1/column-config/${pageKey}`, { columns })
}

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
  testModel: (providerId: number, modelId: number, data: LlmTestRequest) =>
    request.post<LlmTestResult>(`/v1/llm-providers/${providerId}/models/${modelId}/test`, data, { timeout: 60000 }),

  // Roles
  getRoles: () => request.get<string[]>('/v1/llm-providers/models/roles'),

  // Sniff
  sniffModels: (id: number) =>
    request.post<SniffedModel[]>(`/v1/llm-providers/${id}/sniff-models`, null, { timeout: 30000 }),
}
