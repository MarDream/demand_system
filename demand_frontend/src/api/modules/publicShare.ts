import request from '@/api/request'

export interface PublicShareContext {
  shareToken: string
  accessToken: string
  knowledgeBaseId: number
  documentId: number
  fileName: string
  fileType: string
  expireAt?: string | null
  requireLogin: boolean
  oneTimeAccess: boolean
}

export async function getPublicShareContext(token: string): Promise<PublicShareContext> {
  const res = await request.get<PublicShareContext>(`/v1/public/knowledge/shares/${token}/context`)
  return res as unknown as PublicShareContext
}
