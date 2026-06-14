export interface Review {
  id: number
  requirementId: number
  requirementTitle?: string
  reviewerId: number
  result: string
  comment: string | null
  suggestions: string | null
  reviewedAt: string | null
  reviewerName?: string
}

export interface ReviewListQuery {
  requirementId?: number
  result?: string
  keyword?: string
  pageNum: number
  pageSize: number
}
