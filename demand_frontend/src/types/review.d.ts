export interface Review {
  id: number
  requirementId: number
  reviewerId: number
  result: string
  comment: string | null
  suggestions: string | null
  reviewedAt: string | null
  reviewerName?: string
}
