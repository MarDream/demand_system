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
