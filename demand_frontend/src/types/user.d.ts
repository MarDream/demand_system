export interface User {
  id: number
  username: string
  realName: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: string
  jobNumber?: string | null
  password?: string
  orgId?: number | null
  regionId?: number | null
  departmentId?: number | null
  positionId?: number | null
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  username?: string
  realName?: string
  status?: string
  orgId?: number
  regionId?: number
  departmentId?: number
  pageNum: number
  pageSize: number
}

export interface Position {
  id: number
  name: string
  code: string | null
  level: number | null
  description: string | null
  sortOrder?: number
}

export interface OrgNode {
  id: number
  name: string
  parentId: number | null
  orgType: 'region' | 'company' | 'bureau' | 'department' | 'group'
  code: string | null
  leaderId: number | null
  leaderName: string | null
  description: string | null
  sortOrder: number
  path: string | null
  level: number
  memberCount?: number
  createdAt: string
  updatedAt: string
  children?: OrgNode[]
}
