import { get, post, del } from './request'
import type { PageResult } from './request'

export interface Requirement {
  id: number
  requirementNo?: string | null
  title: string
  description: string
  type: string
  priority: string
  status: string
  nodeStatus?: string | null
  isDraft?: boolean | null
  dueDate?: string | null
  createdAt: string
  updatedAt: string
  creatorName?: string
  assigneeName?: string | null
  currentHandlerName?: string | null
  childCount?: number
  followed?: boolean
}

export interface RequirementComment {
  id: number
  requirementId: number
  userId: number
  userName?: string
  content: string
  createdAt: string
}

export interface AssigneeCandidate {
  id: number
  name: string
}

export interface AvailableTransition {
  toNodeId: string
  toNodeName: string
  label?: string | null
  assigneeDisplayName?: string | null
  assigneeScopeName?: string | null
  assigneeCandidates?: AssigneeCandidate[] | null
  defaultAssigneeId?: number | null
}

export interface WorkflowAvailableActions {
  canTransition: boolean
  currentNodeName?: string | null
  currentNodeStatusName?: string | null
  transitions: AvailableTransition[]
  lockVersion?: number | null
  canEdit?: boolean | null
  canDelete?: boolean | null
  workflowActive?: boolean | null
  currentNodeRequireComment?: boolean | null
}

export interface TransitionHistoryItem {
  id: number
  fromNodeName?: string | null
  toNodeName?: string | null
  action?: string | null
  comment?: string | null
  operatorName?: string | null
  operatorRoleName?: string | null
  completedAt?: string | null
  createdAt?: string | null
  durationDisplay?: string | null
}

export interface RequirementHistoryItem {
  id: number
  action?: string | null
  detail?: string | null
  operatorName?: string | null
  createdAt?: string | null
}

export interface RequirementQuery {
  keyword?: string
  projectId?: number
  type?: string
  priority?: string
  status?: string
  pageNum?: number
  pageSize?: number
}

export interface MyListQuery {
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export function getList(params: RequirementQuery) {
  return get<PageResult<Requirement>>('/v1/requirements', params)
}

export function getMyPending(params: MyListQuery) {
  return get<PageResult<Requirement>>('/v1/requirements/my-pending', params)
}

export function getMyFollows(params: MyListQuery) {
  return get<PageResult<Requirement>>('/v1/requirements/my-follows', params)
}

export function getMyDone(params: MyListQuery) {
  return get<PageResult<Requirement>>('/v1/requirements/my-done', params)
}

export function getById(id: number) {
  return get<Requirement>(`/v1/requirements/${id}`)
}

export function getComments(id: number) {
  return get<RequirementComment[]>(`/v1/requirements/${id}/comments`)
}

export function createComment(id: number, content: string) {
  return post<void>(`/v1/requirements/${id}/comments`, { content })
}

export function getAvailableActions(id: number) {
  return get<WorkflowAvailableActions>(`/v1/workflow-engine/actions/${id}`)
}

export interface FlowTransitionRequest {
  requirementId: number
  toNodeId: string
  comment?: string
  lockVersion?: number | null
  selectedAssigneeId?: number | null
}

export function transition(data: FlowTransitionRequest) {
  return post<void>('/v1/workflow-engine/transition', data)
}

export function getTransitionHistory(id: number) {
  return get<TransitionHistoryItem[]>(`/v1/workflow-engine/transitions/${id}`)
}

export function getHistory(id: number) {
  return get<RequirementHistoryItem[]>(`/v1/requirements/${id}/history`)
}

export function followRequirement(id: number) {
  return post<void>(`/v1/requirements/${id}/follow`)
}

export function unfollowRequirement(id: number) {
  return del<void>(`/v1/requirements/${id}/follow`)
}
