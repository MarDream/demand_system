/** 邀请方式：link=通过链接邀请，batch=批量邀请 */
export type InvitationType = 'link' | 'batch'

/** 邀请状态：pending=待接受，accepted=已接受，expired=已过期，revoked=已撤回 */
export type InvitationStatus = 'pending' | 'accepted' | 'expired' | 'revoked'

/** 申请来源：link=邀请链接，batch=批量邀请，admin=管理员添加，self=自助申请 */
export type JoinRequestSource = 'link' | 'batch' | 'admin' | 'self'

/** 申请状态：pending=待处理，approved=已通过，rejected=已拒绝 */
export type JoinRequestStatus = 'pending' | 'approved' | 'rejected'

export interface Invitation {
  id: number
  inviteCode: string
  inviteType: InvitationType
  inviteTypeText: string
  target: string | null
  targetName: string | null
  orgId: number | null
  orgName: string | null
  roleIds: number[]
  roleNames: string[]
  status: InvitationStatus
  statusText: string
  maxUses: number | null
  usedCount: number | null
  expiresAt: string | null
  invitedBy: number | null
  inviterName: string | null
  acceptedBy: number | null
  acceptedAt: string | null
  remark: string | null
  createdAt: string | null
}

export interface InvitationQuery {
  inviteType?: InvitationType | ''
  status?: InvitationStatus | ''
  keyword?: string
  pageNum: number
  pageSize: number
}

export interface InvitationLinkPayload {
  orgId?: number | null
  roleIds?: number[]
  expireDays?: number | null
  neverExpire?: boolean
  maxUses?: number
  remark?: string
}

export interface BatchInviteMember {
  name: string
  phone?: string
  email?: string
}

export interface BatchInvitePayload {
  orgId?: number | null
  roleIds?: number[]
  expireDays?: number | null
  remark?: string
  members: BatchInviteMember[]
}

export interface BatchInviteSkipped {
  name: string | null
  target: string | null
  reason: string
}

export interface BatchInviteResult {
  successCount: number
  skipped: BatchInviteSkipped[]
}

export interface JoinRequest {
  id: number
  invitationId: number | null
  inviteCode: string | null
  userId: number | null
  username: string | null
  applicantName: string
  applicantPhone: string | null
  applicantEmail: string | null
  orgId: number | null
  orgName: string | null
  roleIds: number[]
  roleNames: string[]
  source: JoinRequestSource
  sourceText: string
  status: JoinRequestStatus
  statusText: string
  applyRemark: string | null
  reviewedBy: number | null
  reviewerName: string | null
  reviewedAt: string | null
  reviewRemark: string | null
  createdAt: string | null
}

export interface JoinRequestQuery {
  status?: JoinRequestStatus | ''
  source?: JoinRequestSource | ''
  keyword?: string
  pageNum: number
  pageSize: number
}

export interface ReviewJoinRequestPayload {
  orgId?: number | null
  roleIds?: number[]
  reviewRemark?: string
}

/** 邀请链接的对外信息（匿名接口） */
export interface InviteInfo {
  inviteCode: string
  inviteType: InvitationType
  inviterName: string | null
  orgName: string | null
  expiresAt: string | null
  valid: boolean
  invalidReason: string | null
  targetName: string | null
}

/** 被邀请人提交的资料（匿名接口） */
export interface InviteApplyPayload {
  name: string
  phone: string
  email: string
  remark?: string
}
