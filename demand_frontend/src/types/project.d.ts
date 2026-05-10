export interface Project {
  id: number
  name: string
  description: string | null
  companyId?: number | null
  team?: string | null
  leaderId?: number | null
  startDate?: string | null
  endDate?: string | null
  creatorId: number
  status: string
  createdAt: string
}

export interface ProjectMember {
  id: number
  projectId: number
  userId: number
  role: string
  joinedAt: string
  username?: string
  realName?: string
}

export interface ProjectImportFailure {
  rowNum: number
  projectName: string
  reason: string
}

export interface ProjectImportResult {
  successCount: number
  failCount: number
  failures: ProjectImportFailure[]
}
