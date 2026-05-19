import request from '@/api/request'

export interface OmniDocStatus {
  available: boolean
  message: string
  apiJsUrl: string
}

export async function getEditorConfig(_knowledgeBaseId: number, _documentId: number, _mode: 'edit' | 'view' = 'edit'): Promise<any> {
  return {}
}

export async function getPublicEditorConfig(_accessToken: string, _mode: 'edit' | 'view' = 'view'): Promise<any> {
  return {}
}

export async function getOmniDocStatus(): Promise<OmniDocStatus> {
  return { available: false, message: '已切换到 kkFileView 预览服务', apiJsUrl: '' }
}
