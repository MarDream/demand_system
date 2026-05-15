import request from '@/api/request'

export interface OnlyOfficeEditorConfig {
  document: {
    key: string
    url: string
    title: string
    fileType: string
    permissions?: {
      edit: boolean
      download: boolean
      print: boolean
    }
  }
  documentType?: 'word' | 'cell' | 'slide' | 'pdf'
  editorConfig: {
    callbackUrl: string
    user: {
      id: string
      name: string
    }
    mode: 'edit' | 'view'
  }
  token?: string
  type?: string
  height?: string
  width?: string
}

export interface OnlyOfficeStatus {
  available: boolean
  message: string
  apiJsUrl: string
}

export async function getEditorConfig(knowledgeBaseId: number, documentId: number, mode: 'edit' | 'view' = 'edit'): Promise<OnlyOfficeEditorConfig> {
  const res = await request.post<OnlyOfficeEditorConfig>('/v1/onlyoffice/editor-config', null, {
    params: { knowledgeBaseId, documentId, mode }
  })
  return res as unknown as OnlyOfficeEditorConfig
}

export async function getPublicEditorConfig(accessToken: string, mode: 'edit' | 'view' = 'view'): Promise<OnlyOfficeEditorConfig> {
  const res = await request.post<OnlyOfficeEditorConfig>('/v1/onlyoffice/public/editor-config', null, {
    params: { accessToken, mode }
  })
  return res as unknown as OnlyOfficeEditorConfig
}

export async function getOnlyOfficeStatus(): Promise<OnlyOfficeStatus> {
  const res = await request.get<OnlyOfficeStatus>('/v1/onlyoffice/status')
  return res as unknown as OnlyOfficeStatus
}
