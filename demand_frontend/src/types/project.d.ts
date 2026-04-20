export interface Project {
  id: number
  name: string
  description: string | null
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
