import request from '@/api/request'
import type { PageResult } from '@/types/api'
import type {
  BatchInvitePayload,
  BatchInviteResult,
  Invitation,
  InvitationLinkPayload,
  InvitationQuery,
  InviteApplyPayload,
  InviteInfo,
  JoinRequest,
  JoinRequestQuery,
  ReviewJoinRequestPayload,
} from '@/types/invitation'

/**
 * request 的响应拦截器已经把 { code, message, data } 拆包成 data，
 * 但它的 TS 签名仍是 AxiosResponse<T>。这里统一收口成 Promise<T>，
 * 调用方就不用每个地方都写 `const res: any = await ...`。
 */
function unwrap<T>(promise: unknown): Promise<T> {
  return promise as Promise<T>
}

/* ── 邀请记录（管理员） ── */

/** 通过链接邀请：生成一条可复制的邀请链接 */
export function createLinkInvite(data: InvitationLinkPayload): Promise<Invitation> {
  return unwrap(request.post('/v1/invitations/link', data))
}

/** 批量邀请：一次提交多个被邀请人 */
export function batchInvite(data: BatchInvitePayload): Promise<BatchInviteResult> {
  return unwrap(request.post('/v1/invitations/batch', data))
}

/** 邀请记录列表 */
export function getInvitationList(params: InvitationQuery): Promise<PageResult<Invitation>> {
  return unwrap(request.get('/v1/invitations', { params }))
}

/** 撤回邀请 */
export function revokeInvitation(id: number): Promise<Invitation> {
  return unwrap(request.put(`/v1/invitations/${id}/revoke`))
}

/** 重发邀请（换新码并重置有效期） */
export function resendInvitation(id: number): Promise<Invitation> {
  return unwrap(request.put(`/v1/invitations/${id}/resend`))
}

/* ── 申请记录（管理员） ── */

/** 添加/申请记录列表 */
export function getJoinRequestList(params: JoinRequestQuery): Promise<PageResult<JoinRequest>> {
  return unwrap(request.get('/v1/join-requests', { params }))
}

/** 审批通过 */
export function approveJoinRequest(id: number, data?: ReviewJoinRequestPayload): Promise<void> {
  return unwrap(request.put(`/v1/join-requests/${id}/approve`, data ?? {}))
}

/** 审批拒绝 */
export function rejectJoinRequest(id: number, data?: ReviewJoinRequestPayload): Promise<void> {
  return unwrap(request.put(`/v1/join-requests/${id}/reject`, data ?? {}))
}

/* ── 邀请落地页（匿名） ── */

/** 查询邀请链接信息 */
export function getInviteInfo(code: string): Promise<InviteInfo> {
  return unwrap(request.get(`/v1/public/invitations/${code}`))
}

/** 被邀请人提交资料 */
export function submitInviteApplication(code: string, data: InviteApplyPayload): Promise<void> {
  return unwrap(request.post(`/v1/public/invitations/${code}/apply`, data))
}

/**
 * 拼出可直接发给被邀请人的完整链接。
 * 用当前页面的 origin 而不是后端配置，本地开发 / 换域名都不会拼错。
 */
export function buildInviteUrl(code: string) {
  const { origin } = window.location
  return `${origin}/public/invite/${code}`
}
