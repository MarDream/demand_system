/** Git 平台 */
export interface GitPlatform {
  id: number
  name: string
  platformType: 'GITLAB' | 'GITHUB' | 'GITEE' | 'GITEA'
  baseUrl: string
  authType: 'TOKEN' | 'SSH_KEY' | 'PASSWORD'
  /** 后端脱敏为 ****** */
  credential: string
  /** 0/1 */
  isDefault: number
  status: 'CONNECTED' | 'DISCONNECTED' | 'EXPIRED'
  lastCheckedAt?: string
  createdAt?: string
}

/** Git 仓库 */
export interface GitRepository {
  id: number
  platformId: number
  projectId?: number
  name: string
  fullPath: string
  description?: string
  defaultBranch: string
  cloneUrlSsh?: string
  cloneUrlHttps?: string
  remoteId?: string
  status: 'ACTIVE' | 'ARCHIVED' | 'DELETED'
  /** 联表 */
  platformName?: string
  /** 联表 */
  projectName?: string
  createdAt?: string
}

/** 分支保护规则 */
export interface BranchProtectionRule {
  id: number
  repoId: number
  ruleSetId?: number
  ruleName: string
  branchPattern: string
  priority: number
  forbidPush: number
  forbidForcePush: number
  forbidDelete: number
  requireMr: number
  minApprovals: number
  dismissStaleApprovals: number
  blockSelfApprove: number
  requireCodeownerApproval: number
  requireThreadResolved: number
  requireCiPass: number
  requireUpToDate: number
  ciContexts?: string
  /** JSON 数组字符串 */
  whitelistUsers?: string
  /** JSON 数组字符串 */
  whitelistRoles?: string
  enabled: number
}

/** 保护规则集 */
export interface ProtectionRuleSet {
  id: number
  name: string
  description?: string
  createdAt?: string
}

/** 合并请求 */
export interface MergeRequest {
  id: number
  repoId: number
  sourceBranch: string
  targetBranch: string
  title: string
  description?: string
  authorId: number
  status: 'OPEN' | 'APPROVED' | 'MERGED' | 'CLOSED' | 'CONFLICT'
  mergeStrategy: 'MERGE' | 'SQUASH' | 'REBASE'
  ciStatus?: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  requirementId?: number
  mergedAt?: string
  createdAt?: string
  authorName?: string
  repoName?: string
}

/** Git 审计日志 */
export interface GitAuditLog {
  id: number
  operatorId?: number
  operatorName?: string
  operatorIp?: string
  userAgent?: string
  targetType: string
  targetId?: number
  targetName?: string
  action: string
  detail?: string
  createdAt?: string
}

/** 测试连接结果 */
export interface GitTestResult {
  connected: boolean
  message: string
}
