import type { DynamicFieldSchema } from '@/api/modules/requirementConfig'

export interface Requirement {
  id: number
  projectId: number
  parentId: number | null
  creatorId: number
  assigneeId: number | null
  opsFollowId: number | null
  maintFollowId: number | null
  departmentId: number | null
  orgId?: number | null
  requirementNo: string | null
  title: string
  description: string
  type: string
  priority: string
  status: string
  moduleId: number | null
  iterationId: number | null
  workflowInstanceId?: number | null
  nodeStatus?: string | null
  isDraft?: boolean | null
  startDate?: string | null
  estimatedHours: number | null
  actualHours: number | null
  dueDate: string | null
  analysisCompletedAt: string | null
  confirmAt: string | null
  developmentCompletedAt: string | null
  attachments?: RequirementAttachment[]
  transitionAttachments?: TransitionAttachmentGroup[]
  ccUserIds?: number[]
  orderNum: number
  version: number
  createdAt: string
  updatedAt: string
  creatorName?: string
  /** 提出人所属组织名称（取自 creator 用户的 orgId 对应组织） */
  creatorOrgName?: string
  assigneeName?: string
  currentHandlerName?: string
  opsFollowName?: string
  maintFollowName?: string
  departmentName?: string
  childCount?: number
  // 权限相关字段
  canEdit?: boolean        // 可编辑
  canView?: boolean        // 可查看
  canApprove?: boolean     // 可审批
  canDelete?: boolean      // 可删除（后端按 delete() 同款口径计算；旧数据缺失时视为不可删）
  isParticipant?: boolean  // 是否参与人
  operationType?: 'edit' | 'approve' | 'view'  // 操作类型
  followed?: boolean       // 当前登录用户是否已关注
  /** 动态字段 schema（含当前流程节点权限与已存值） */
  dynamicFields?: DynamicFieldSchema[]
}

export interface RequirementAttachment {
  fileId?: number | null
  name: string
  url: string
  size?: number | null
  contentType?: string | null
  bucketName?: string | null
  objectName?: string | null
  uploadedAt?: string | null
  uploaderId?: number | null
  uploaderName?: string | null
  /** 内容哈希(SHA-256 hex,小写);新上传由服务端返回,历史附件经批量接口回查 */
  contentHash?: string | null
}

/**
 * 需求详情中流转节点上传的附件分组。
 * 每个节点上传的附件归为一组，便于前端按节点统一展示。
 */
export interface TransitionAttachmentGroup {
  transitionId?: number | null
  nodeName?: string | null
  action?: string | null
  operatorName?: string | null
  operatedAt?: string | null
  attachments?: RequirementAttachment[]
}

export interface RequirementCreate {
  projectId: number
  parentId?: number
  title: string
  description: string
  type: string
  priority: string
  assigneeId?: number
  ccUserIds?: number[]
  moduleId?: number
  startDate?: string
  dueDate?: string
  estimatedHours?: number
  attachments?: RequirementAttachment[]
}

export interface RequirementUpdate extends Partial<RequirementCreate> {
  id: number
  iterationId?: number
  status?: string
}

export interface RequirementDraftCreate {
  projectId: number
  parentId?: number
  title: string
  description: string
  priority: string
  assigneeId?: number
  ccUserIds?: number[]
  moduleId?: number
  startDate?: string
  dueDate?: string
  estimatedHours?: number
  attachments?: RequirementAttachment[]
}

export interface RequirementDraftUpdate extends Partial<RequirementDraftCreate> {
  id: number
  version: number
}

export interface RequirementMyListQuery {
  projectId?: number
  type?: string
  priority?: string
  status?: string
  assigneeId?: number
  keyword?: string
  /** 关键词搜索范围：all(综合) / title / requirementNo / description / assignee / comment */
  keywordScope?: string
  nodeStatus?: string
  isOverdue?: boolean
  /** 时间维度筛选（与全部需求视图共用口径） */
  createdAtStart?: string
  createdAtEnd?: string
  analysisCompletedAtStart?: string
  analysisCompletedAtEnd?: string
  confirmAtStart?: string
  confirmAtEnd?: string
  developmentCompletedAtStart?: string
  developmentCompletedAtEnd?: string
  pageNum: number
  pageSize: number
}

export interface NextNodeOption {
  nodeId: string
  nodeName: string
  bindStatusCode?: string | null
  bindStatusName?: string | null
  projectRequired?: boolean | null
}

export interface RequirementSubmit {
  version: number
  nextNodeId?: string
  projectId?: number | null
  comment?: string
}

export interface RequirementQuery {
  projectId?: number
  parentId?: number
  type?: string
  priority?: string
  status?: string
  nodeStatus?: string
  isOverdue?: boolean
  assigneeId?: number
  iterationId?: number
  keyword?: string
  /** 关键词搜索范围：all(综合) / title / requirementNo / description / assignee / comment */
  keywordScope?: string
  createdAtStart?: string
  createdAtEnd?: string
  analysisCompletedAtStart?: string
  analysisCompletedAtEnd?: string
  confirmAtStart?: string
  confirmAtEnd?: string
  developmentCompletedAtStart?: string
  developmentCompletedAtEnd?: string
  pageNum: number
  pageSize: number
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export interface RequirementHistory {
  id: number
  requirementId: number
  operatorId: number
  fieldName: string
  oldValue: string
  newValue: string
  createdAt: string
  operatorName?: string
  /** 修复 P1：后端 transition action（submit/rollback/cancel/proxy_approve 等） */
  action?: string
  /** 流转时的评审意见 */
  comment?: string
}

export interface RequirementComment {
  id: number
  requirementId: number
  userId: number
  userName?: string
  content: string
  createdAt: string
}

export interface RequirementCommentCreate {
  content: string
}

export interface RequirementApprovalSupplementCreate {
  content: string
  attachments?: RequirementAttachment[]
}

export interface RequirementApprovalEvaluation {
  id: number
  requirementId: number
  instanceId: number
  transitionId?: number | null
  nodeId: string
  nodeName: string
  /** 流转来源节点名 */
  fromNodeName?: string | null
  /** 流转目标节点名 */
  toNodeName?: string | null
  nodeStatusCode?: string | null
  nodeStatusName?: string | null
  parentId?: number | null
  isSupplement?: boolean | null
  canSupplement?: boolean | null
  evaluatorId: number
  evaluatorName?: string | null
  evaluatorUsername?: string | null
  /** 操作人所处节点配置的处理角色名（节点未按角色指派时为空） */
  assigneeRoleName?: string | null
  action?: string | null
  actionLabel?: string | null
  result?: 'SUBMIT' | 'PASS' | 'REJECT' | 'CANCEL' | 'SUPPLEMENT' | string | null
  resultLabel?: string | null
  rating?: number | null
  content?: string | null
  attachments?: RequirementAttachment[]
  createdAt: string
  supplements?: RequirementApprovalEvaluation[]
}

export interface CustomField {
  id: number
  projectId: number
  name: string
  fieldType: string
  options?: string[]
  required: boolean
  defaultValue?: string
  sortOrder: number
}

export interface CustomFieldValue {
  id: number
  requirementId: number
  fieldId: number
  valueText?: string
  valueNumber?: number
  valueDate?: string
  valueUserIds?: number[]
}

export interface RequirementTemplate {
  id?: number
  requirementTypeCode: string
  templateName: string
  templateContent: {
    contentHtml?: string
    sections?: TemplateSection[]
  }
  isActive?: number
  isDefault?: number
  sortOrder?: number
  creatorId?: number
  requirementTypeName?: string
}

export interface TemplateSection {
  sectionId: string
  sectionName: string
  fieldType: 'text' | 'richtext' | 'textarea'
  required: boolean
  placeholder?: string
  maxLength?: number
  defaultContent?: string
}

export interface RequirementTemplateSave {
  id?: number
  requirementTypeCode: string
  templateName: string
  templateContent: {
    contentHtml?: string
    sections?: TemplateSection[]
  }
  isDefault?: number
  sortOrder?: number
}

// 需求详情综合VO（批量查询接口返回）
export interface RequirementDetailVO {
  requirement: Requirement
  history: Record<string, any>[]
  children: Record<string, any>[]
  relations: Record<string, any>[]
  comments: RequirementComment[]
  approvalEvaluations: RequirementApprovalEvaluation[]
}
