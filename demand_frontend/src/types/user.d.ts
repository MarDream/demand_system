export type EmployeeType = 'full_time' | 'part_time' | 'intern' | 'dispatch' | 'other'
export type WorkStatus = 'probation' | 'confirmed' | 'pending_resign' | 'resigned'

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
  systemRole?: string | null
  /** 员工类型(全职/兼职/实习/劳务派遣/其他) */
  employeeType?: EmployeeType | null
  /** 用工状态(试用期/已转正/待离职/已离职) */
  workStatus?: WorkStatus | null
  /** 入职日期 */
  hireDate?: string | null
  /** 生日 */
  birthday?: string | null
  createdAt: string
  updatedAt: string
}

export interface UserQuery {
  username?: string
  realName?: string
  status?: string
  employeeType?: string
  workStatus?: string
  hireDateFrom?: string
  hireDateTo?: string
  orgId?: number
  regionId?: number
  departmentId?: number
  pageNum: number
  pageSize: number
}

export interface RosterStats {
  active: number
  /** 待入职（未激活账号） */
  inactive: number
  probation: number
  confirmed: number
  pendingResign: number
  resigned: number
  fullTime: number
  partTime: number
  intern: number
  dispatch: number
  other: number
}

/** 人事模块场景统计（各模块首屏数字） */
export interface HrOverview {
  probation: number
  pendingResign: number
  inactive: number
  missingPhone: number
  noContract: number
  birthdayThisMonth: number
  birthdayUsers: Array<{ id: number; realName: string; birthday: string; day: number }>
}

export interface RosterImportResult {
  successCount: number
  failCount: number
  failures: string[]
}

export interface RosterExportLog {
  id: number
  fileName: string
  total: number
  operatorName?: string | null
  createdAt: string
}

export type HrRecordType =
  | 'onboarding'
  | 'newcomer'
  | 'regularization'
  | 'transfer'
  | 'resignation'
  | 'contract'
  | 'retirement'
  | 'care'
  | 'safety'

export type HrRecordStatus = 'processing' | 'done' | 'cancelled'

export interface HrRecord {
  id: number
  recordType: HrRecordType
  userId: number
  userName: string
  departmentName?: string | null
  title: string
  detail?: Record<string, any> | null
  recordDate?: string | null
  status: HrRecordStatus
  operatorName?: string | null
  createdAt: string
  updatedAt: string
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
