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

export interface IterationRequirementOption {
  id: number
  title: string
  type: string
  priority: string
  status: string
  iterationId?: number | null
}

export interface IterationFormData {
  id?: number
  name: string
  description: string
  startDate: string
  endDate: string
  capacity: number
  requirementIds: number[]
}
