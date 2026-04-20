export interface User {
  id: number
  username: string
  realName: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: string
  password?: string
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  username?: string
  realName?: string
  status?: string
  regionId?: number
  departmentId?: number
  pageNum: number
  pageSize: number
}

export interface UserOrg {
  id: number
  userId: number
  regionId: number | null
  departmentId: number | null
  positionId: number | null
  systemRole: string
  managerId: number | null
  effectiveDate: string
}

export interface Region {
  id: number
  name: string
  parentId: number | null
  code: string | null
  sortOrder: number
  children?: Region[]
}

export interface Department {
  id: number
  name: string
  parentId: number | null
  regionId: number | null
  code: string | null
  type: string | null
  sortOrder: number
  children?: Department[]
}

export interface Position {
  id: number
  name: string
  code: string | null
  level: number | null
  description: string | null
}
