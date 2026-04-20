export interface Iteration {
  id: number
  projectId: number
  name: string
  description: string | null
  startDate: string
  endDate: string
  capacity: number | null
  status: string
  creatorId: number
  createdAt: string
  requirementCount?: number
  progress?: number
}
