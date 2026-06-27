<template>
  <div class="workflow-editor-page">
    <el-card shadow="never" class="editor-card">
      <template #header>
        <div class="editor-header">
          <div class="header-left">
            <el-button @click="goBack">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
            <div class="title-info">
              <h2>{{ workflowEditorTitle }}</h2>
              <div class="scope-tag">适用范围：{{ workflowScopeLabel }}</div>
              <template v-if="currentVersion || !isViewMode">
                <span v-if="isViewMode" class="version-tag">
                  V{{ currentVersion?.version }} - {{ currentVersion?.name }}
                </span>
                <div v-else class="version-editor">
                  <div class="version-input-group">
                    <div class="version-number-input">
                      <span class="version-prefix">V</span>
                      <el-input
                        v-model="versionForm.version"
                        placeholder="1.0.0"
                        maxlength="20"
                      />
                    </div>
                    <div v-if="versionMetaHint" class="version-meta-hint" :class="versionMetaHint.type">
                      {{ versionMetaHint.message }}
                    </div>
                  </div>
                  <div class="version-input-group">
                    <el-input
                      v-model="versionForm.name"
                      class="version-name-input"
                      maxlength="50"
                      show-word-limit
                      placeholder="请输入版本名称"
                    />
                  </div>
                </div>
              </template>
            </div>
          </div>
          <div class="header-right">
            <AppButton v-if="!isViewMode" permission="button:workflow:update" @click="handleSave" :loading="saving">
              <el-icon><DocumentCopy /></el-icon>
              {{ ['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') && !configDirty ? '保存名称' : '保存草稿' }}
            </AppButton>
            <AppButton v-if="!isViewMode && (!['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') || configDirty)" type="primary" permission="button:workflow:update" @click="handleSubmit" :loading="submitting">
              <el-icon><Check /></el-icon>
              提交审核
            </AppButton>
          </div>
        </div>
        <div v-if="['approved', 'inactive', 'rejected'].includes(currentVersion?.activationStatus ?? '') && !isViewMode" class="approved-edit-warning">
          <el-icon><WarningFilled /></el-icon>
          当前版本{{ currentVersion?.activationStatus === 'approved' ? '已审核通过' : currentVersion?.activationStatus === 'inactive' ? '已停用' : '审核被拒绝' }}。修改节点或连线配置后保存，版本将回退为草稿状态，需重新提交审核。仅修改版本名称则保持原状态。
        </div>
      </template>

      <div class="editor-container">
        <!-- 左侧工具栏 -->
        <div class="toolbar-left">
          <div class="toolbar-title">节点类型</div>
          <div class="node-palette">
            <div
              v-for="node in nodeTypes"
              :key="node.type"
              class="palette-node"
              :class="[node.type, { 'is-readonly': isViewMode }]"
              @mousedown.prevent="handleNodeDragStart(node)"
            >
              <div class="node-icon">{{ node.icon }}</div>
              <div class="node-label">{{ node.label }}</div>
            </div>
          </div>

          <div class="toolbar-section help-section">
            <div class="toolbar-title">绘制说明</div>
            <ol>
              <li>按住节点类型拖到画布。</li>
              <li>从节点锚点拖向目标节点创建连线。</li>
              <li>点击节点或连线，在右侧配置。</li>
              <li>完成后保存草稿，再提交审核。</li>
            </ol>
          </div>

          <div class="toolbar-section">
            <div class="toolbar-title">操作</div>
            <div class="toolbar-actions">
              <el-button class="toolbar-action-btn" @click="handleZoomIn">
                <el-icon><ZoomIn /></el-icon>
                放大
              </el-button>
              <el-button class="toolbar-action-btn" @click="handleZoomOut">
                <el-icon><ZoomOut /></el-icon>
                缩小
              </el-button>
              <el-button class="toolbar-action-btn" @click="handleResetZoom">
                <el-icon><Refresh /></el-icon>
                重置
              </el-button>
              <AppButton
                class="toolbar-action-btn"
                @click="handleFormatLayout"
                permission="button:workflow:update"
                :disabled="isViewMode"
              >
                <el-icon><Grid /></el-icon>
                格式化排版
              </AppButton>
              <AppButton class="toolbar-action-btn" @click="handleClearCanvas" type="danger" permission="button:workflow:update" :disabled="isViewMode">
                <el-icon><Delete /></el-icon>
                清空画布
              </AppButton>
            </div>
          </div>
        </div>

        <!-- 中间画布区域 -->
        <div class="canvas-container">
          <div ref="logicFlowContainer" class="logicflow-container"></div>
        </div>

        <!-- 右侧配置面板 -->
        <el-drawer
          v-model="drawerVisible"
          :title="drawerTitle"
          direction="rtl"
          :size="drawerSize"
          :before-close="handleDrawerClose"
          class="config-drawer"
        >
          <!-- 拖拽调整手柄 -->
          <div
            class="drawer-resize-handle"
            :class="{ active: isResizing }"
            @mousedown="onResizeStart"
          >
            <div class="resize-bar"></div>
          </div>
          <div v-if="selectedNode" class="node-config-panel">
            <el-form :model="nodeForm" label-width="100px" label-position="top" :disabled="isViewMode">
              <el-form-item label="节点名称">
                <el-input v-model="nodeForm.nodeName" placeholder="请输入节点名称" />
              </el-form-item>

              <el-form-item label="节点类型">
                <el-tag>{{ getNodeTypeLabel(nodeForm.nodeType) }}</el-tag>
              </el-form-item>

              <!-- 显示可流转的下个节点名称 -->
              <el-form-item v-if="nextNodeNames.length > 0" label="可流转节点">
                <el-select
                  :model-value="selectedNextNode"
                  placeholder="请选择目标节点"
                  clearable
                  @update:model-value="onNextNodeChange"
                >
                  <el-option
                    v-for="name in nextNodeNames"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </el-select>
              </el-form-item>

              <el-form-item label="绑定节点状态">
                <el-select v-model="nodeForm.nodeStatusCode" placeholder="请选择节点状态" clearable filterable>
                  <el-option
                    v-for="status in nodeStatusOptions"
                    :key="status.code"
                    :label="status.name"
                    :value="status.code"
                  />
                </el-select>
              </el-form-item>

              <el-form-item v-if="nodeForm.nodeType !== 'end'" label="节点规则">
                <div class="node-rule-grid">
                  <el-checkbox v-model="nodeForm.allowCancel">允许取消</el-checkbox>
                  <el-checkbox v-if="showProjectRequiredCheckbox" v-model="nodeForm.projectRequired">
                    项目必选
                  </el-checkbox>
                  <el-checkbox v-model="nodeForm.requireAttachment">
                    必须上传附件
                  </el-checkbox>
                </div>
              </el-form-item>

              <!-- 评分配置（仅审批节点） -->
              <el-form-item v-if="nodeForm.nodeType === 'approval'" label="节点评价">
                <div class="rating-config-panel">
                  <div class="rating-toggle-row">
                    <el-switch v-model="nodeForm.ratingConfig.enabled" />
                    <span class="rating-toggle-label">启用评分</span>
                    <el-tooltip content="关闭后此节点不展示评价" placement="top">
                      <el-icon class="rating-help"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </div>
                  <template v-if="nodeForm.ratingConfig.enabled">
                    <div class="rating-required-row">
                      <el-checkbox v-model="nodeForm.ratingConfig.required">设为必填</el-checkbox>
                      <el-checkbox v-model="nodeForm.ratingConfig.showInStatistics">纳入统计</el-checkbox>
                    </div>
                    <el-divider content-position="left">评分维度</el-divider>
                    <div v-for="(dim, idx) in nodeForm.ratingConfig.dimensions" :key="idx" class="rating-dimension-item">
                      <el-input v-model="dim.name" placeholder="维度名称（如：需求质量）" size="small" />
                      <el-input v-model="dim.description" placeholder="评价说明" type="textarea" :rows="2" size="small" />
                      <el-row :gutter="8">
                        <el-col :span="12">
                          <el-input v-model="dim.minLabel" placeholder="1星标签（如：很差）" size="small" />
                        </el-col>
                        <el-col :span="12">
                          <el-input v-model="dim.maxLabel" placeholder="5星标签（如：非常好）" size="small" />
                        </el-col>
                      </el-row>
                      <el-button type="danger" text size="small" @click="removeRatingDimension(idx)">删除维度</el-button>
                    </div>
                    <el-button v-if="nodeForm.ratingConfig.dimensions.length === 0" type="primary" plain size="small" @click="addRatingDimension">
                      + 添加评分维度
                    </el-button>
                    <el-button v-else type="primary" plain size="small" @click="addRatingDimension">
                      + 再添加一个维度
                    </el-button>
                    <div v-if="nodeForm.ratingConfig.dimensions.length === 0" class="rating-tip">
                      留空则使用单一评分模式（1-5星整体评分）
                    </div>
                  </template>
                </div>
              </el-form-item>

              <!-- 审批节点和抄送节点的配置 -->
              <template v-if="nodeForm.nodeType === 'approval' || nodeForm.nodeType === 'cc'">
                <el-form-item label="处理人类型">
                  <el-select v-model="nodeForm.assigneeType" placeholder="请选择处理人类型">
                    <el-option label="指定用户" value="SPECIFIED_USER" />
                    <el-option label="指定角色" value="SPECIFIED_ROLE" />
                    <el-option label="指定角色组" value="SPECIFIED_ROLE_GROUP" />
                    <el-option label="指定组织" value="SPECIFIED_ORG" />
                    <el-option label="提交人" value="CREATOR" />
                    <el-option label="上一节点处理人" value="PREV_APPROVER" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ROLE'" label="指定角色">
                  <el-select v-model="nodeForm.assigneeRoleId" placeholder="请选择角色" :loading="assigneeOptionsLoading">
                    <el-option
                      v-for="role in roleSelectOptions"
                      :key="role.id"
                      :label="role.name"
                      :value="role.id"
                      :disabled="role.disabled"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ROLE_GROUP'" label="指定角色组">
                  <el-select v-model="nodeForm.assigneeRoleGroupId" placeholder="请选择角色组" :loading="assigneeOptionsLoading">
                    <el-option
                      v-for="group in roleGroupSelectOptions"
                      :key="group.id"
                      :label="group.name"
                      :value="group.id"
                      :disabled="group.disabled"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_USER'" label="指定用户">
                  <el-select v-model="nodeForm.assigneeUserIds" multiple placeholder="请选择用户" :loading="assigneeOptionsLoading">
                    <el-option
                      v-for="user in userSelectOptions"
                      :key="user.id"
                      :label="user.realName || user.username"
                      :value="user.id"
                      :disabled="user.disabled"
                    />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ORG'" label="指定组织">
                  <el-tree-select
                    v-model="nodeForm.assigneeOrgId"
                    :data="orgTreeData"
                    :props="{ label: 'name', value: 'id', children: 'children' }"
                    placeholder="请选择组织节点"
                    check-strictly
                    filterable
                    clearable
                  />
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ORG'" label="组织层级范围">
                  <el-radio-group v-model="nodeForm.orgScopeType">
                    <el-radio value="current">仅当前层级</el-radio>
                    <el-radio value="include_children">当前层级及子层级</el-radio>
                  </el-radio-group>
                </el-form-item>

                <el-form-item v-if="nodeForm.nodeType === 'approval'" label="超时时间（小时）">
                  <el-input-number
                    v-model="nodeForm.timeoutHours"
                    :min="0"
                    :max="720"
                    placeholder="0表示不限制"
                  />
                </el-form-item>

                <el-form-item v-if="nodeForm.nodeType === 'approval' && nodeForm.timeoutHours" label="超时动作">
                  <el-select v-model="nodeForm.timeoutAction" placeholder="请选择超时动作">
                    <el-option label="自动通过" value="AUTO_APPROVE" />
                    <el-option label="自动拒绝" value="AUTO_REJECT" />
                    <el-option label="转交上级" value="ESCALATE" />
                  </el-select>
                </el-form-item>

                <!-- 会签配置 -->
                <CountersignConfig
                  v-if="nodeForm.nodeType === 'approval'"
                  :enabled="nodeForm.countersignEnabled ?? false"
                  :strategy="nodeForm.countersignStrategy ?? 'ALL'"
                  :mode="nodeForm.countersignMode ?? 'FIXED'"
                  :approvers="nodeForm.countersignApprovers ?? []"
                  :users="allUserList"
                  @update:enabled="handleCountersignEnabledChange"
                  @update:strategy="nodeForm.countersignStrategy = $event"
                  @update:mode="nodeForm.countersignMode = $event"
                  @update:approvers="handleCountersignApproversChange"
                />
              </template>

              <template v-if="nodeForm.nodeType === 'parallel'">
                <ParallelConfig
                  :parallel-type="nodeForm.parallelType"
                  :branches="nodeForm.parallelBranches"
                  @update:parallel-type="nodeForm.parallelType = $event as 'AND' | 'OR'"
                  @update:branches="nodeForm.parallelBranches = $event"
                />
              </template>

              <!-- 条件节点配置 -->
              <template v-if="nodeForm.nodeType === 'condition'">
                <ConditionBranchConfig
                  :condition-desc="nodeForm.properties?.conditionDesc || ''"
                  :branches="nodeForm.conditionBranches || []"
                  :disabled="isViewMode"
                  @update:condition-desc="nodeForm.properties = { ...nodeForm.properties, conditionDesc: $event }"
                  @update:branches="nodeForm.conditionBranches = $event"
                />
              </template>
            </el-form>

            <div v-if="!isViewMode" class="node-config-footer">
              <AppButton type="primary" :icon="Check" permission="button:workflow:update" @click="handleSaveNodeConfig">保存</AppButton>
              <AppButton type="danger" :icon="Delete" permission="button:workflow:update" @click="handleDeleteNode">删除</AppButton>
            </div>
          </div>

          <div v-else-if="selectedEdge" class="edge-config-panel">
            <el-form :model="edgeForm" label-width="100px" label-position="top" :disabled="isViewMode">
              <el-form-item label="连线标签">
                <el-input v-model="edgeForm.label" placeholder="请输入连线标签（可选）" />
              </el-form-item>

              <el-form-item label="条件">
                <ConditionConfig
                  :model-value="edgeConditionModel"
                  @update:model-value="onEdgeConditionModelUpdate"
                  @update:expr="edgeForm.conditionExpr = $event"
                />
              </el-form-item>
              <el-form-item label="条件表达式（高级）">
                <el-input
                  v-model="edgeForm.conditionExpr"
                  type="textarea"
                  :rows="2"
                  placeholder="例如：priority == 'P0'"
                />
              </el-form-item>
            </el-form>

            <div v-if="!isViewMode" class="node-config-footer">
              <AppButton type="primary" :icon="Check" permission="button:workflow:update" @click="handleSaveEdgeConfig">保存</AppButton>
              <AppButton type="danger" :icon="Delete" permission="button:workflow:update" @click="handleDeleteEdge">删除连线</AppButton>
            </div>
          </div>
        </el-drawer>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, reactive, watch } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppButton from '@/components/common/AppButton.vue'
import CountersignConfig from './components/CountersignConfig.vue'
import ParallelConfig from './components/ParallelConfig.vue'
import ConditionConfig from './components/ConditionConfig.vue'
import ConditionBranchConfig from './components/ConditionBranchConfig.vue'
import {
  ArrowLeft,
  DocumentCopy,
  Check,
  ZoomIn,
  ZoomOut,
  Refresh,
  Grid,
  Delete,
  WarningFilled
} from '@element-plus/icons-vue'
import LogicFlow from '@logicflow/core'
import dagre from '@dagrejs/dagre'
import '@logicflow/core/dist/index.css'
import { registerCustomNodes } from './logicflow-config'
import {
  GLOBAL_WORKFLOW_PROJECT_ID,
  saveWorkflowConfig,
  submitForApproval,
  getVersionConfig,
  getVersionHistory,
} from '@/api/modules/workflow-visual'
import { nodeStatusApi, type NodeStatus } from '@/api/modules/workflow-engine'
import * as roleApi from '@/api/modules/role'
import * as userApi from '@/api/modules/user'
import { resolveActiveMenuPath } from '@/utils/menuNavigation'
import {
  compareWorkflowVersion,
  isWorkflowVersion,
  sameWorkflowVersion,
  suggestNextWorkflowVersion,
} from '@/utils/workflowVersion'
import type {
  WorkflowVersionDTO,
  WorkflowVersionMetaUpdateDTO,
  WorkflowNodeDTO,
  WorkflowEdgeDTO,
  WorkflowConfigDTO,
  ConditionBranch,
  ConditionConfig as ConditionConfigType,
  ConditionRule
} from '@/types/workflow-visual'

const router = useRouter()
const route = useRoute()

const logicFlowContainer = ref<HTMLElement>()
let lf: LogicFlow | null = null

const isViewMode = ref(false)
const isEditMode = ref(false)
const currentVersion = ref<WorkflowVersionDTO>()
const saving = ref(false)
const submitting = ref(false)
const configDirty = ref(false)
const versionHistory = ref<WorkflowVersionDTO[]>([])
const roleList = ref<Array<{ id: number; name: string; code: string }>>([])
const roleGroupList = ref<Array<{ id: number; name: string }>>([])
const allUserList = ref<Array<{ id: number; realName: string; username: string }>>([])
const orgTreeData = ref<any[]>([])
const assigneeOptionsLoading = ref(false)

const drawerVisible = ref(false)
const drawerTitle = ref('')
/** 抽屉宽度：默认 500px，条件节点自适应加宽到 680px */
const drawerSize = ref('500px')
/** 拖拽调整宽度 */
const isResizing = ref(false)
const MIN_DRAWER_WIDTH = 420
const MAX_DRAWER_WIDTH_RATIO = 0.85
const selectedNode = ref<any>(null)
const selectedEdge = ref<any>(null)
const selectedNextNode = ref<string>('')
const SELECTED_NEXT_NODE_PROPERTY = 'selectedNextNode'

// 选择可流转节点时的处理
const onNextNodeChange = (nodeName: string | null | undefined) => {
  selectedNextNode.value = nodeName || ''
  nodeForm.properties = {
    ...(nodeForm.properties || {}),
    [SELECTED_NEXT_NODE_PROPERTY]: selectedNextNode.value
  }
}
const versionForm = reactive<{
  version: string
  name: string
}>({
  version: '',
  name: ''
})
const nodeStatusOptions = ref<NodeStatus[]>([])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始', icon: '▶' },
  { type: 'approval', label: '审批', icon: '✓' },
  { type: 'cc', label: '抄送', icon: '📧' },
  { type: 'condition', label: '条件', icon: '◆' },
  { type: 'parallel', label: '并行', icon: '⑂' },
  { type: 'end', label: '结束', icon: '■' }
]

// 计算当前节点可流转的下个节点名称列表
const nextNodeNames = computed(() => {
  if (!lf || !selectedNode.value?.id) return []

  const graphData = lf.getGraphData() as {
    nodes?: Array<{ id?: string; text?: { value: string } }>
    edges?: Array<{ sourceNodeId?: string; targetNodeId?: string }>
  }

  if (!graphData?.edges) return []

  // 找出所有从当前节点出发的边
  const outgoingEdges = (graphData.edges || []).filter(
    (edge) => edge.sourceNodeId === selectedNode.value!.id
  )

  if (outgoingEdges.length === 0) return []

  // 构建节点ID到名称的映射
  const nodeNameMap = new Map<string, string>()
  ;(graphData.nodes || []).forEach((node) => {
    if (node.id) {
      nodeNameMap.set(node.id, node.text?.value || node.id)
    }
  })

  // 获取所有下一个节点的名称
  return outgoingEdges
    .map((edge) => nodeNameMap.get(edge.targetNodeId || ''))
    .filter((name): name is string => !!name)
})

// 节点表单
const nodeForm = reactive<Partial<WorkflowNodeDTO> & {
  nodeStatusCode?: string
  allowCancel?: boolean
  projectRequired?: boolean
  requireAttachment?: boolean
  ratingConfig?: {
    enabled: boolean
    required: boolean
    showInStatistics: boolean
    dimensions: Array<{ key: string; name: string; description: string; minLabel: string; maxLabel: string }>
  }
  assigneeRoleGroupId?: number
  assigneeOrgId?: number
  orgScopeType?: 'current' | 'include_children'
  countersignEnabled?: boolean
  countersignStrategy?: 'ALL' | 'ANY' | 'MAJORITY'
  countersignMode?: 'FIXED' | 'DYNAMIC'
  countersignApprovers?: number[]
  parallelType?: 'AND' | 'OR'
  parallelBranches?: Array<{ branchId: string; branchName: string; condition: { field?: string; operator?: string; value?: string } }>
  conditionBranches?: ConditionBranch[]
}>({
  nodeId: '',
  nodeType: 'approval',
  nodeName: '',
  positionX: 0,
  positionY: 0,
  assigneeType: undefined,
  assigneeRoleId: undefined,
  assigneeRoleGroupId: undefined,
  assigneeOrgId: undefined,
  orgScopeType: 'include_children',
  assigneeUserIds: [],
  timeoutHours: undefined,
  timeoutAction: undefined,
  nodeStatusCode: undefined,
  allowCancel: true,
  projectRequired: false,
  requireAttachment: false,
  ratingConfig: {
    enabled: false,
    required: false,
    showInStatistics: true,
    dimensions: []
  },
  properties: {},
  countersignEnabled: false,
  countersignStrategy: 'ALL',
  countersignMode: 'FIXED',
  countersignApprovers: [],
  parallelType: 'AND',
  parallelBranches: [],
  conditionBranches: []
})

// 会签配置：需求提出人占位 ID（与 CountersignConfig 中保持一致）
const COUNTERSIGN_CREATOR_PLACEHOLDER_ID = -1

// 用户首次启用会签开关时，若会签人列表为空，自动填入「需求提出人」占位
watch(
  () => nodeForm.countersignEnabled,
  (newVal, oldVal) => {
    if (newVal && !oldVal) {
      ensureDefaultCountersignApprover()
    }
  },
)

function ensureDefaultCountersignApprover() {
  if (!nodeForm.countersignApprovers || nodeForm.countersignApprovers.length === 0) {
    nodeForm.countersignApprovers = [COUNTERSIGN_CREATOR_PLACEHOLDER_ID]
  }
}

function handleCountersignEnabledChange(value: boolean) {
  nodeForm.countersignEnabled = value
  if (value) {
    ensureDefaultCountersignApprover()
  }
}

function handleCountersignApproversChange(value: number[]) {
  nodeForm.countersignApprovers = value
}

// 边表单
const edgeForm = reactive({
  label: '',
  conditionExpr: ''
})

const edgeConditionModel = ref<{ logic: 'AND' | 'OR'; rules: ConditionRule[] }>({
  logic: 'AND',
  rules: [{ field: 'type', operator: 'eq', value: '' }],
})

function onEdgeConditionModelUpdate(val: { logic: string; rules: ConditionRule[]; expr: string }) {
  edgeConditionModel.value = { logic: (val.logic === 'OR' ? 'OR' : 'AND'), rules: val.rules }
  edgeForm.conditionExpr = val.expr || ''
}

const NODE_LAYOUT_SIZE: Record<string, { width: number; height: number }> = {
  start: { width: 80, height: 80 },
  approval: { width: 120, height: 60 },
  cc: { width: 120, height: 60 },
  condition: { width: 80, height: 80 },
  parallel: { width: 80, height: 80 },
  end: { width: 80, height: 80 }
}

const LAYOUT_START_X = 180
const LAYOUT_START_Y = 180
const LAYOUT_LEVEL_GAP = 170
const LAYOUT_ROW_GAP = 130
const LAYOUT_LEVEL_BRANCH_GAP = 36
const LAYOUT_LEVEL_MERGE_COMPACT_GAP = -18
const LAYOUT_LEVEL_CHAIN_COMPACT_GAP = -28
const LAYOUT_BRANCH_PADDING = 58
const LAYOUT_BRANCH_CLUSTER_GAP = 24
const LAYOUT_BRANCH_BALANCE_FACTOR = 0.35
const LAYOUT_MIN_NODE_VERTICAL_GAP = 28

type LayoutNode = {
  id: string
  type: string
  x: number
  y: number
}

type LayoutEdge = {
  sourceNodeId: string
  targetNodeId: string
}

type LayoutPosition = {
  x: number
  y: number
}

const resolveWorkflowProjectId = (rawValue: unknown) => {
  const parsedValue = Number(rawValue)
  return Number.isFinite(parsedValue) && parsedValue >= 0 ? parsedValue : GLOBAL_WORKFLOW_PROJECT_ID
}

const getNodeSize = (type?: string) => NODE_LAYOUT_SIZE[type || 'approval'] || NODE_LAYOUT_SIZE.approval

const currentProjectId = computed(() => resolveWorkflowProjectId(route.query.projectId || route.params.projectId))
const returnMenuPath = computed(() => resolveActiveMenuPath(route))
const workflowScopeLabel = computed(() => currentProjectId.value === GLOBAL_WORKFLOW_PROJECT_ID ? '全局标准流程' : `项目 ${currentProjectId.value}`)
const workflowEditorTitle = computed(() => {
  const scopeText = currentProjectId.value === GLOBAL_WORKFLOW_PROJECT_ID ? '全局工作流' : '工作流'
  if (isViewMode.value) return `查看${scopeText}`
  if (isEditMode.value) return `编辑${scopeText}`
  return `新建${scopeText}`
})
const nodeDrawerTitle = computed(() => isViewMode.value ? '节点详情' : '节点配置')
const edgeDrawerTitle = computed(() => isViewMode.value ? '连线详情' : '连线配置')
const showProjectRequiredCheckbox = computed(() => !hasProjectRequiredInPredecessors(nodeForm.nodeId))
const roleSelectOptions = computed(() => {
  const options = roleList.value.map(role => ({ ...role, disabled: false }))
  const selectedRoleId = nodeForm.assigneeRoleId
  if (
    selectedRoleId !== undefined &&
    selectedRoleId !== null &&
    !options.some(role => role.id === selectedRoleId)
  ) {
    options.unshift({
      id: selectedRoleId,
      name: assigneeOptionsLoading.value ? '角色加载中...' : '角色不存在或已删除',
      code: '',
      disabled: true
    })
  }
  return options
})
const roleGroupSelectOptions = computed(() => {
  const options = roleGroupList.value.map(group => ({ ...group, disabled: false }))
  const selectedGroupId = nodeForm.assigneeRoleGroupId
  if (
    selectedGroupId !== undefined &&
    selectedGroupId !== null &&
    !options.some(group => group.id === selectedGroupId)
  ) {
    options.unshift({
      id: selectedGroupId,
      name: assigneeOptionsLoading.value ? '角色组加载中...' : '角色组不存在或已删除',
      disabled: true
    })
  }
  return options
})
const userSelectOptions = computed(() => {
  const options = allUserList.value.map(user => ({ ...user, disabled: false }))
  const selectedUserIds = nodeForm.assigneeUserIds || []
  selectedUserIds
    .filter(userId => !options.some(user => user.id === userId))
    .forEach(userId => {
      options.unshift({
        id: userId,
        realName: assigneeOptionsLoading.value ? '用户加载中...' : '用户不存在或已删除',
        username: '',
        disabled: true
      })
    })
  return options
})
const trimmedVersionName = computed(() => versionForm.name.trim())
const duplicatedVersionRecord = computed(() => {
  const trimmedVersion = versionForm.version.trim()
  if (!trimmedVersion) return undefined
  return versionHistory.value.find((item) => {
    if (!sameWorkflowVersion(item.version, trimmedVersion)) return false
    if (currentVersion.value?.id && item.id === currentVersion.value.id) return false
    return true
  })
})
const versionMetaHint = computed(() => {
  if (isViewMode.value) return null
  const trimmedVersion = versionForm.version.trim()
  if (!trimmedVersion && !trimmedVersionName.value) {
    return { type: 'info', message: '支持直接编辑版本号和版本名称' }
  }
  if (!trimmedVersion) {
    return { type: 'warning', message: '版本号不能为空' }
  }
  if (!isWorkflowVersion(trimmedVersion)) {
    return { type: 'warning', message: '版本号格式需为正整数或 1.0.0' }
  }
  if (duplicatedVersionRecord.value) {
    return { type: 'error', message: `版本号 V${trimmedVersion} 已存在` }
  }
  if (!trimmedVersionName.value) {
    return { type: 'warning', message: '版本名称不能为空' }
  }
  return { type: 'success', message: '版本信息可保存' }
})

const applyCurrentVersion = (version: WorkflowVersionDTO) => {
  currentVersion.value = {
    ...currentVersion.value,
    ...version,
    config: version.config ?? currentVersion.value?.config
  }
}

const syncVersionForm = (version?: WorkflowVersionDTO) => {
  versionForm.version = version?.version || ''
  versionForm.name = version?.name || ''
}

const getDesiredVersionMeta = (): WorkflowVersionMetaUpdateDTO | null => {
  const version = versionForm.version.trim()
  const name = versionForm.name.trim()
  const hasAnyInput = version.length > 0 || name.length > 0 || !!currentVersion.value

  if (!hasAnyInput) {
    return null
  }

  if (!version) {
    ElMessage.warning('请输入版本号')
    return null
  }

  if (!isWorkflowVersion(version)) {
    ElMessage.warning('版本号格式需为正整数或 1.0.0')
    return null
  }

  if (duplicatedVersionRecord.value) {
    ElMessage.warning(`版本号 V${version} 已存在，请重新输入`)
    return null
  }

  if (!name) {
    ElMessage.warning('请输入版本名称')
    return null
  }

  return { version, name }
}

const syncEditorVersionRoute = async (versionId: number, projectId: number) => {
  const nextVersionId = String(versionId)
  const currentRouteVersionId = route.query.versionId ? String(route.query.versionId) : ''
  const currentProjectId = route.query.projectId ? String(route.query.projectId) : ''
  if (currentRouteVersionId === nextVersionId && currentProjectId === String(projectId) && route.query.mode === 'edit') {
    return
  }

  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      versionId: nextVersionId,
      projectId: String(projectId),
      mode: 'edit'
    }
  })
}

const applySuggestedVersionMeta = () => {
  if (currentVersion.value || isViewMode.value) return
  if (versionForm.version.trim() || trimmedVersionName.value) return

  const latestVersion = [...versionHistory.value]
    .sort((left, right) => compareWorkflowVersion(right.version, left.version))[0]?.version
  const nextVersion = suggestNextWorkflowVersion(latestVersion)
  versionForm.version = nextVersion
  versionForm.name = `草稿版本 v${nextVersion}`
}

const compareByCanvasPosition = (a: LayoutNode, b: LayoutNode) => {
  if (a.y !== b.y) return a.y - b.y
  if (a.x !== b.x) return a.x - b.x
  return a.id.localeCompare(b.id)
}

const getLayoutGraphData = () => {
  if (!lf) return null

  const graphData = lf.getGraphData() as {
    nodes?: Array<{
      id?: string
      type?: string
      x?: number
      y?: number
    }>
    edges?: Array<{
      sourceNodeId?: string
      targetNodeId?: string
    }>
  }

  return {
    nodes: (graphData.nodes || [])
      .filter((node): node is Required<LayoutNode> => !!node.id && !!node.type && typeof node.x === 'number' && typeof node.y === 'number')
      .map((node) => ({
        id: node.id,
        type: node.type,
        x: node.x,
        y: node.y
      })),
    edges: (graphData.edges || [])
      .filter((edge): edge is Required<LayoutEdge> => !!edge.sourceNodeId && !!edge.targetNodeId)
      .map((edge) => ({
        sourceNodeId: edge.sourceNodeId,
        targetNodeId: edge.targetNodeId
      }))
  }
}

const getWorkflowGraphData = () => {
  if (!lf) return null

  return lf.getGraphData() as {
    nodes?: Array<{
      id?: string
      properties?: Record<string, any>
    }>
    edges?: Array<{
      sourceNodeId?: string
      targetNodeId?: string
    }>
  }
}

const hasNodeProjectRequired = (node?: { properties?: Record<string, any> }) => {
  return Boolean(node?.properties?.projectRequired ?? node?.properties?.properties?.projectRequired)
}

const hasProjectRequiredInPredecessors = (nodeId?: string) => {
  if (!nodeId) return false

  const graphData = getWorkflowGraphData()
  if (!graphData) return false

  const nodeMap = new Map((graphData.nodes || []).filter(node => !!node.id).map(node => [node.id as string, node]))
  const parentMap = new Map<string, string[]>()

  ;(graphData.edges || []).forEach((edge) => {
    if (!edge.sourceNodeId || !edge.targetNodeId) return
    const parents = parentMap.get(edge.targetNodeId) || []
    parents.push(edge.sourceNodeId)
    parentMap.set(edge.targetNodeId, parents)
  })

  const visited = new Set<string>()
  const stack = [...(parentMap.get(nodeId) || [])]

  while (stack.length > 0) {
    const currentId = stack.pop()
    if (!currentId || visited.has(currentId)) continue
    visited.add(currentId)

    const currentNode = nodeMap.get(currentId)
    if (hasNodeProjectRequired(currentNode)) {
      return true
    }

    stack.push(...(parentMap.get(currentId) || []))
  }

  return false
}

const normalizeCurrentNodeProjectRequired = () => {
  if (hasProjectRequiredInPredecessors(nodeForm.nodeId)) {
    nodeForm.projectRequired = false
  }
}

const buildFormattedLayout = (nodes: LayoutNode[], edges: LayoutEdge[]) => {
  const nodeMap = new Map(nodes.map(node => [node.id, node]))
  const childrenMap = new Map<string, string[]>()
  const parentsMap = new Map<string, string[]>()
  const incomingCount = new Map<string, number>()
  const outgoingCount = new Map<string, number>()
  const levelMap = new Map<string, number>()
  const positions = new Map<string, LayoutPosition>()
  const treeChildrenMap = new Map<string, string[]>()
  const primaryParentMap = new Map<string, string>()
  const subtreeSpanCache = new Map<string, number>()
  const sameLaneChildMap = new Map<string, string>()
  const levelXMap = new Map<number, number>()

  nodes.forEach((node) => {
    childrenMap.set(node.id, [])
    parentsMap.set(node.id, [])
    incomingCount.set(node.id, 0)
    outgoingCount.set(node.id, 0)
    levelMap.set(node.id, 0)
    treeChildrenMap.set(node.id, [])
  })

  edges.forEach((edge) => {
    if (!nodeMap.has(edge.sourceNodeId) || !nodeMap.has(edge.targetNodeId)) return
    childrenMap.get(edge.sourceNodeId)!.push(edge.targetNodeId)
    parentsMap.get(edge.targetNodeId)!.push(edge.sourceNodeId)
    incomingCount.set(edge.targetNodeId, (incomingCount.get(edge.targetNodeId) || 0) + 1)
    outgoingCount.set(edge.sourceNodeId, (outgoingCount.get(edge.sourceNodeId) || 0) + 1)
  })

  childrenMap.forEach((childIds, nodeId) => {
    childIds.sort((leftId, rightId) => compareByCanvasPosition(nodeMap.get(leftId)!, nodeMap.get(rightId)!))
    childrenMap.set(nodeId, childIds)
  })

  parentsMap.forEach((parentIds, nodeId) => {
    parentIds.sort((leftId, rightId) => compareByCanvasPosition(nodeMap.get(leftId)!, nodeMap.get(rightId)!))
    parentsMap.set(nodeId, parentIds)
  })

  const sortedNodes = [...nodes].sort((left, right) => {
    const typeDelta = Number(right.type === 'start') - Number(left.type === 'start')
    if (typeDelta !== 0) return typeDelta
    return compareByCanvasPosition(left, right)
  })

  const roots = sortedNodes.filter(node => node.type === 'start' || (incomingCount.get(node.id) || 0) === 0)
  const queue = roots.length > 0 ? roots.map(node => node.id) : [sortedNodes[0]?.id].filter(Boolean) as string[]
  const remainingIncoming = new Map(incomingCount)
  const topoOrder: string[] = []

  while (queue.length > 0) {
    queue.sort((leftId, rightId) => compareByCanvasPosition(nodeMap.get(leftId)!, nodeMap.get(rightId)!))
    const currentId = queue.shift()!
    const currentLevel = levelMap.get(currentId) || 0
    topoOrder.push(currentId)

    for (const childId of childrenMap.get(currentId) || []) {
      const nextLevel = currentLevel + 1
      if (nextLevel > (levelMap.get(childId) || 0)) {
        levelMap.set(childId, nextLevel)
      }

      const nextIncoming = (remainingIncoming.get(childId) || 0) - 1
      remainingIncoming.set(childId, nextIncoming)
      if (nextIncoming === 0) {
        queue.push(childId)
      }
    }
  }

  nodes.forEach((node) => {
    if (!topoOrder.includes(node.id)) {
      topoOrder.push(node.id)
    }
  })

  const dagreGraph = new dagre.graphlib.Graph({ multigraph: true, compound: false })
  dagreGraph.setGraph({
    rankdir: 'LR',
    align: 'UL',
    ranksep: LAYOUT_LEVEL_GAP,
    nodesep: Math.max(80, LAYOUT_ROW_GAP - 12),
    edgesep: 26,
    marginx: 40,
    marginy: 40
  })
  dagreGraph.setDefaultEdgeLabel(() => ({}))

  nodes.forEach((node) => {
    const size = getNodeSize(node.type)
    dagreGraph.setNode(node.id, {
      width: size.width,
      height: size.height
    })
  })

  edges.forEach((edge, index) => {
    dagreGraph.setEdge(edge.sourceNodeId, edge.targetNodeId, {
      weight:
        1 +
        ((outgoingCount.get(edge.sourceNodeId) || 0) === 1 ? 2 : 0) +
        ((incomingCount.get(edge.targetNodeId) || 0) === 1 ? 2 : 0),
      minlen: 1
    }, `${edge.sourceNodeId}_${edge.targetNodeId}_${index}`)
  })

  dagre.layout(dagreGraph)

  const dagrePositionMap = new Map<string, LayoutPosition>()
  nodes.forEach((node) => {
    const dagreNode = dagreGraph.node(node.id)
    if (!dagreNode) return
    dagrePositionMap.set(node.id, {
      x: dagreNode.x,
      y: dagreNode.y
    })
  })

  const compareByDagrePosition = (leftId: string, rightId: string) => {
    const leftPosition = dagrePositionMap.get(leftId)
    const rightPosition = dagrePositionMap.get(rightId)
    if (leftPosition && rightPosition) {
      if (Math.abs(leftPosition.y - rightPosition.y) >= 1) {
        return leftPosition.y - rightPosition.y
      }
      if (Math.abs(leftPosition.x - rightPosition.x) >= 1) {
        return leftPosition.x - rightPosition.x
      }
    }
    return compareByCanvasPosition(nodeMap.get(leftId)!, nodeMap.get(rightId)!)
  }

  childrenMap.forEach((childIds, nodeId) => {
    childIds.sort(compareByDagrePosition)
    childrenMap.set(nodeId, childIds)
  })

  parentsMap.forEach((parentIds, nodeId) => {
    parentIds.sort(compareByDagrePosition)
    parentsMap.set(nodeId, parentIds)
  })

  const pathScoreMap = new Map<string, number>()
  topoOrder.forEach((nodeId) => {
    const node = nodeMap.get(nodeId)!
    const parentIds = parentsMap.get(nodeId) || []

    if (parentIds.length === 0) {
      pathScoreMap.set(nodeId, node.type === 'start' ? 2000 : 0)
      return
    }

    let bestScore = Number.NEGATIVE_INFINITY
    let bestParentId = parentIds[0]

    parentIds.forEach((parentId) => {
      const parentNode = nodeMap.get(parentId)!
      const parentScore = pathScoreMap.get(parentId) || 0
      const chainBonus =
        ((outgoingCount.get(parentId) || 0) === 1 ? 240 : 0) +
        ((incomingCount.get(nodeId) || 0) === 1 ? 220 : 0)
      const nodeTypeBonus =
        (node.type === 'end' ? 140 : 0) +
        (parentNode.type === 'start' ? 80 : 0) +
        (parentNode.type === 'condition' ? 40 : 0)
      const dagreBonus = -Math.abs((dagrePositionMap.get(parentId)?.y || 0) - (dagrePositionMap.get(nodeId)?.y || 0)) * 0.25
      const candidateScore = parentScore + 1000 + chainBonus + nodeTypeBonus + dagreBonus

      if (
        candidateScore > bestScore ||
        (candidateScore === bestScore && compareByDagrePosition(parentId, bestParentId) < 0)
      ) {
        bestScore = candidateScore
        bestParentId = parentId
      }
    })

    primaryParentMap.set(nodeId, bestParentId)
    pathScoreMap.set(nodeId, bestScore)
  })

  const leafIds = topoOrder.filter((nodeId) => (childrenMap.get(nodeId) || []).length === 0)
  const candidateEndIds = leafIds.length > 0
    ? leafIds
    : topoOrder.filter((nodeId) => nodeMap.get(nodeId)?.type === 'end')

  const bestEndId = [...candidateEndIds].sort((leftId, rightId) => {
    const scoreDelta = (pathScoreMap.get(rightId) || 0) - (pathScoreMap.get(leftId) || 0)
    if (scoreDelta !== 0) return scoreDelta
    const levelDelta = (levelMap.get(rightId) || 0) - (levelMap.get(leftId) || 0)
    if (levelDelta !== 0) return levelDelta
    return compareByDagrePosition(leftId, rightId)
  })[0]

  const mainPathIds: string[] = []
  const mainPathSet = new Set<string>()
  let cursorId = bestEndId
  while (cursorId) {
    mainPathIds.push(cursorId)
    mainPathSet.add(cursorId)
    const nextCursor = primaryParentMap.get(cursorId)
    if (!nextCursor || mainPathSet.has(nextCursor)) break
    cursorId = nextCursor
  }
  mainPathIds.reverse()

  const mainPathIndexMap = new Map(mainPathIds.map((nodeId, index) => [nodeId, index]))
  mainPathIds.forEach((nodeId, index) => {
    const nextNodeId = mainPathIds[index + 1]
    if (nextNodeId) {
      sameLaneChildMap.set(nodeId, nextNodeId)
    }
  })

  nodes.forEach((node) => {
    const parentId = primaryParentMap.get(node.id)
    if (!parentId) return
    treeChildrenMap.get(parentId)!.push(node.id)
  })

  treeChildrenMap.forEach((childIds, nodeId) => {
    childIds.sort((leftId, rightId) => {
      const leftIsMain = mainPathIndexMap.has(leftId)
      const rightIsMain = mainPathIndexMap.has(rightId)
      if (leftIsMain !== rightIsMain) {
        return leftIsMain ? -1 : 1
      }
      return compareByDagrePosition(leftId, rightId)
    })
    treeChildrenMap.set(nodeId, childIds)
  })

  const pickPrimaryChild = (parentId: string, childIds: string[]) => {
    const parentNode = nodeMap.get(parentId)
    if (!parentNode) return childIds[0]

    return [...childIds].sort((leftId, rightId) => {
      const leftNode = nodeMap.get(leftId)!
      const rightNode = nodeMap.get(rightId)!
      const leftDistance = Math.abs((dagrePositionMap.get(leftId)?.y || leftNode.y) - (dagrePositionMap.get(parentId)?.y || parentNode.y))
      const rightDistance = Math.abs((dagrePositionMap.get(rightId)?.y || rightNode.y) - (dagrePositionMap.get(parentId)?.y || parentNode.y))
      if (leftDistance !== rightDistance) return leftDistance - rightDistance

      const leftOutCount = outgoingCount.get(leftId) || 0
      const rightOutCount = outgoingCount.get(rightId) || 0
      if (leftOutCount !== rightOutCount) return rightOutCount - leftOutCount

      const leftMainDistance = mainPathIndexMap.has(leftId) ? 0 : 1
      const rightMainDistance = mainPathIndexMap.has(rightId) ? 0 : 1
      if (leftMainDistance !== rightMainDistance) return leftMainDistance - rightMainDistance

      return compareByDagrePosition(leftId, rightId)
    })[0]
  }

  const calcSubtreeSpan = (nodeId: string, visited = new Set<string>()): number => {
    if (subtreeSpanCache.has(nodeId)) {
      return subtreeSpanCache.get(nodeId)!
    }
    if (visited.has(nodeId)) {
      return 1
    }

    visited.add(nodeId)
    const childIds = treeChildrenMap.get(nodeId) || []
    if (childIds.length === 0) {
      subtreeSpanCache.set(nodeId, 1)
      return 1
    }

    const sameLaneChildId = sameLaneChildMap.get(nodeId) || (
      childIds.length === 1
        ? childIds[0]
        : (mainPathSet.has(nodeId) ? undefined : pickPrimaryChild(nodeId, childIds))
    )

    const branchChildIds = childIds.filter((childId) => childId !== sameLaneChildId)

    let mainLaneSpan = 1
    if (sameLaneChildId) {
      mainLaneSpan = calcSubtreeSpan(sameLaneChildId, new Set(visited))
    }

    if (branchChildIds.length === 0) {
      const span = Math.max(1, mainLaneSpan)
      subtreeSpanCache.set(nodeId, span)
      return span
    }

    const branchSpan = branchChildIds.reduce((total, childId, index) => {
      const childSpan = calcSubtreeSpan(childId, new Set(visited))
      return total + childSpan + (index === branchChildIds.length - 1 ? 0 : 1)
    }, 0)
    const span = Math.max(mainLaneSpan, branchSpan)
    subtreeSpanCache.set(nodeId, span)
    return span
  }

  const computeLevelXPositions = () => {
    const maxLevel = Math.max(...[...levelMap.values()], 0)
    const levelWidths = Array.from({ length: maxLevel + 1 }, (_, level) => {
      const levelNodeWidths = nodes
        .filter((node) => (levelMap.get(node.id) || 0) === level)
        .map((node) => getNodeSize(node.type).width)
      return levelNodeWidths.length > 0 ? Math.max(...levelNodeWidths) : getNodeSize().width
    })

    let currentX = LAYOUT_START_X
    levelXMap.set(0, currentX)

    for (let level = 1; level <= maxLevel; level += 1) {
      const previousLevelNodeIds = nodes
        .filter((node) => (levelMap.get(node.id) || 0) === level - 1)
        .map((node) => node.id)
      const currentLevelNodeIds = nodes
        .filter((node) => (levelMap.get(node.id) || 0) === level)
        .map((node) => node.id)
      const previousSingleNodeId = previousLevelNodeIds[0]
      const currentSingleNodeId = currentLevelNodeIds[0]

      const hasBranching = previousLevelNodeIds.some((nodeId) => (outgoingCount.get(nodeId) || 0) > 1)
      const hasMerge = currentLevelNodeIds.some((nodeId) => (incomingCount.get(nodeId) || 0) > 1)
      const levelNodeCount = currentLevelNodeIds.length
      const isStraightChainLevel =
        previousLevelNodeIds.length === 1 &&
        currentLevelNodeIds.length === 1 &&
        !!previousSingleNodeId &&
        !!currentSingleNodeId &&
        (
          sameLaneChildMap.get(previousSingleNodeId) === currentSingleNodeId ||
          (
            (childrenMap.get(previousSingleNodeId) || []).length === 1 &&
            (parentsMap.get(currentSingleNodeId) || []).length === 1 &&
            (childrenMap.get(previousSingleNodeId) || [])[0] === currentSingleNodeId &&
            (parentsMap.get(currentSingleNodeId) || [])[0] === previousSingleNodeId
          )
        )

      const gap =
        LAYOUT_LEVEL_GAP +
        (hasBranching || levelNodeCount > 1 ? LAYOUT_LEVEL_BRANCH_GAP : 0) +
        (hasMerge ? LAYOUT_LEVEL_MERGE_COMPACT_GAP : 0) +
        (isStraightChainLevel ? LAYOUT_LEVEL_CHAIN_COMPACT_GAP : 0)

      currentX += levelWidths[level - 1] / 2 + gap + levelWidths[level] / 2
      levelXMap.set(level, currentX)
    }
  }

  computeLevelXPositions()

  const resolveBranchBuckets = (nodeId: string, branchChildIds: string[]) => {
    const parentDagreY = dagrePositionMap.get(nodeId)?.y || nodeMap.get(nodeId)?.y || 0
    const branchChildren = [...branchChildIds].sort(compareByDagrePosition)
    const upperChildIds: string[] = []
    const lowerChildIds: string[] = []

    const currentUpperSpan = () => upperChildIds.reduce((total, childId, index) => {
      const childSpan = calcSubtreeSpan(childId)
      return total + childSpan + (index === upperChildIds.length - 1 ? 0 : 1)
    }, 0)

    const currentLowerSpan = () => lowerChildIds.reduce((total, childId, index) => {
      const childSpan = calcSubtreeSpan(childId)
      return total + childSpan + (index === lowerChildIds.length - 1 ? 0 : 1)
    }, 0)

    branchChildren.forEach((childId) => {
      const childDagreY = dagrePositionMap.get(childId)?.y || nodeMap.get(childId)?.y || 0
      if (childDagreY < parentDagreY - 1) {
        upperChildIds.push(childId)
        return
      }
      if (childDagreY > parentDagreY + 1) {
        lowerChildIds.push(childId)
        return
      }

      if (currentUpperSpan() <= currentLowerSpan()) {
        upperChildIds.push(childId)
      } else {
        lowerChildIds.push(childId)
      }
    })

    if (upperChildIds.length === 0 && lowerChildIds.length > 1) {
      const normalizedChildren = [...lowerChildIds]
      upperChildIds.push(...normalizedChildren.filter((_, index) => index % 2 === 0))
      lowerChildIds.splice(0, lowerChildIds.length, ...normalizedChildren.filter((_, index) => index % 2 === 1))
    } else if (lowerChildIds.length === 0 && upperChildIds.length > 1) {
      const normalizedChildren = [...upperChildIds]
      upperChildIds.splice(0, upperChildIds.length, ...normalizedChildren.filter((_, index) => index % 2 === 0))
      lowerChildIds.push(...normalizedChildren.filter((_, index) => index % 2 === 1))
    }

    upperChildIds.sort(compareByDagrePosition)
    lowerChildIds.sort(compareByDagrePosition)

    return { upperChildIds, lowerChildIds }
  }

  const placeSubtree = (nodeId: string, centerY: number) => {
    const level = levelMap.get(nodeId) || 0
    const x = levelXMap.get(level) || (LAYOUT_START_X + level * LAYOUT_LEVEL_GAP)
    const y = Math.max(getNodeSize(nodeMap.get(nodeId)?.type).height / 2, centerY)

    positions.set(nodeId, { x, y })

    const childIds = treeChildrenMap.get(nodeId) || []
    if (childIds.length === 0) return

    const sameLaneChildId = sameLaneChildMap.get(nodeId) || (
      childIds.length === 1
        ? childIds[0]
        : (mainPathSet.has(nodeId) ? undefined : pickPrimaryChild(nodeId, childIds))
    )
    const branchChildIds = childIds.filter((childId) => childId !== sameLaneChildId)

    if (sameLaneChildId) {
      placeSubtree(sameLaneChildId, y)
    }

    if (branchChildIds.length === 0) {
      return
    }

    const { upperChildIds, lowerChildIds } = resolveBranchBuckets(nodeId, branchChildIds)
    const getClusterSpan = (childIds: string[]) => childIds.reduce((total, childId, index) => {
      return total + calcSubtreeSpan(childId) * LAYOUT_ROW_GAP + (index === 0 ? 0 : LAYOUT_BRANCH_CLUSTER_GAP)
    }, 0)
    const placeDirectionalChildren = (childIds: string[], direction: -1 | 1, padding: number) => {
      if (childIds.length === 0) return

      const orderedChildIds = direction < 0 ? [...childIds].reverse() : childIds
      let cursor = y + direction * padding

      orderedChildIds.forEach((childId, index) => {
        const childSpan = calcSubtreeSpan(childId) * LAYOUT_ROW_GAP
        cursor += direction * (childSpan / 2)
        placeSubtree(childId, cursor)
        cursor += direction * (childSpan / 2)
        if (index < orderedChildIds.length - 1) {
          cursor += direction * LAYOUT_BRANCH_CLUSTER_GAP
        }
      })
    }

    const upperClusterSpan = getClusterSpan(upperChildIds)
    const lowerClusterSpan = getClusterSpan(lowerChildIds)
    const upperPadding = LAYOUT_BRANCH_PADDING + Math.max(0, (lowerClusterSpan - upperClusterSpan) * LAYOUT_BRANCH_BALANCE_FACTOR)
    const lowerPadding = LAYOUT_BRANCH_PADDING + Math.max(0, (upperClusterSpan - lowerClusterSpan) * LAYOUT_BRANCH_BALANCE_FACTOR)

    placeDirectionalChildren(upperChildIds, -1, upperPadding)
    placeDirectionalChildren(lowerChildIds, 1, lowerPadding)
  }

  const rootIds = sortedNodes
    .filter(node => !primaryParentMap.has(node.id))
    .map(node => node.id)
    .sort((leftId, rightId) => {
      const leftNode = nodeMap.get(leftId)!
      const rightNode = nodeMap.get(rightId)!
      const typeDelta = Number(rightNode.type === 'start') - Number(leftNode.type === 'start')
      if (typeDelta !== 0) return typeDelta
      return compareByDagrePosition(leftId, rightId)
    })

  const mainPathCenterY = (() => {
    const mainPathYs = mainPathIds
      .map((nodeId) => dagrePositionMap.get(nodeId)?.y)
      .filter((value): value is number => typeof value === 'number')
    if (mainPathYs.length === 0) {
      return LAYOUT_START_Y
    }

    const sortedY = [...mainPathYs].sort((left, right) => left - right)
    const middleIndex = Math.floor(sortedY.length / 2)
    if (sortedY.length % 2 === 1) {
      return sortedY[middleIndex]
    }
    return (sortedY[middleIndex - 1] + sortedY[middleIndex]) / 2
  })()

  if (rootIds.length > 0) {
    const mainRootId = mainPathIds[0] && rootIds.includes(mainPathIds[0]) ? mainPathIds[0] : rootIds[0]
    if (mainRootId) {
      placeSubtree(mainRootId, mainPathCenterY)
    }
  }

  let rootCursorY = mainPathCenterY + LAYOUT_ROW_GAP * 2
  rootIds
    .filter((rootId) => !positions.has(rootId))
    .forEach((rootId) => {
      const rootSpan = calcSubtreeSpan(rootId)
      placeSubtree(rootId, rootCursorY + ((rootSpan - 1) * LAYOUT_ROW_GAP) / 2)
      rootCursorY += rootSpan * LAYOUT_ROW_GAP + LAYOUT_ROW_GAP
    })

  nodes
    .filter(node => !positions.has(node.id))
    .sort(compareByCanvasPosition)
    .forEach((node) => {
      placeSubtree(node.id, rootCursorY)
      rootCursorY += LAYOUT_ROW_GAP
    })

  const levelGroups = new Map<number, string[]>()
  nodes.forEach((node) => {
    const level = levelMap.get(node.id) || 0
    const levelNodeIds = levelGroups.get(level) || []
    levelNodeIds.push(node.id)
    levelGroups.set(level, levelNodeIds)
  })

  const getMinGap = (upperId: string, lowerId: string) => {
    return (getNodeSize(nodeMap.get(upperId)?.type).height + getNodeSize(nodeMap.get(lowerId)?.type).height) / 2 + LAYOUT_MIN_NODE_VERTICAL_GAP
  }

  const getTargetY = (nodeId: string) => {
    if (mainPathSet.has(nodeId)) {
      return mainPathCenterY
    }

    const structuralY = dagrePositionMap.get(nodeId)?.y || positions.get(nodeId)?.y || LAYOUT_START_Y
    const parentYs = (parentsMap.get(nodeId) || [])
      .map((parentId) => positions.get(parentId)?.y)
      .filter((value): value is number => typeof value === 'number')
    const childYs = (childrenMap.get(nodeId) || [])
      .map((childId) => positions.get(childId)?.y)
      .filter((value): value is number => typeof value === 'number')
    const connectedYs = [...parentYs, ...childYs]

    if ((incomingCount.get(nodeId) || 0) > 1 && parentYs.length > 0) {
      return parentYs.reduce((total, value) => total + value, 0) / parentYs.length
    }
    if (connectedYs.length === 0) {
      return structuralY
    }

    const linkedCenter = connectedYs.reduce((total, value) => total + value, 0) / connectedYs.length
    const straightLaneBoost = (incomingCount.get(nodeId) || 0) <= 1 && (outgoingCount.get(nodeId) || 0) <= 1 ? 0.84 : 0.72
    return linkedCenter * straightLaneBoost + structuralY * (1 - straightLaneBoost)
  }

  const applyLevelCollisionResolution = (lockedNodeIds = new Set<string>()) => {
    levelGroups.forEach((levelNodeIds) => {
      const fixedNodeIds = levelNodeIds
        .filter((nodeId) => mainPathSet.has(nodeId) || lockedNodeIds.has(nodeId))
        .sort((leftId, rightId) => {
          const leftIndex = mainPathIndexMap.get(leftId)
          const rightIndex = mainPathIndexMap.get(rightId)
          if (typeof leftIndex === 'number' && typeof rightIndex === 'number' && leftIndex !== rightIndex) {
            return leftIndex - rightIndex
          }
          return getTargetY(leftId) - getTargetY(rightId)
        })

      fixedNodeIds.forEach((nodeId) => {
        if (!mainPathSet.has(nodeId)) return
        const currentPosition = positions.get(nodeId)
        if (!currentPosition) return
        positions.set(nodeId, {
          ...currentPosition,
          y: mainPathCenterY
        })
      })

      const sortedLevelIds = [...levelNodeIds].sort((leftId, rightId) => {
        const targetDelta = getTargetY(leftId) - getTargetY(rightId)
        if (Math.abs(targetDelta) >= 1) return targetDelta
        const leftY = positions.get(leftId)?.y || 0
        const rightY = positions.get(rightId)?.y || 0
        if (leftY !== rightY) return leftY - rightY
        return compareByDagrePosition(leftId, rightId)
      })

      const applyMinGap = (upperId: string, lowerId: string) => {
        const upperPosition = positions.get(upperId)
        const lowerPosition = positions.get(lowerId)
        if (!upperPosition || !lowerPosition) return
        const minGap = getMinGap(upperId, lowerId)
        if (lowerPosition.y - upperPosition.y < minGap) {
          positions.set(lowerId, {
            ...lowerPosition,
            y: upperPosition.y + minGap
          })
        }
      }

      if (fixedNodeIds.length === 1) {
        const fixedNodeId = fixedNodeIds[0]
        const fixedIndex = sortedLevelIds.indexOf(fixedNodeId)

        for (let index = fixedIndex + 1; index < sortedLevelIds.length; index += 1) {
          applyMinGap(sortedLevelIds[index - 1], sortedLevelIds[index])
        }

        for (let index = fixedIndex - 1; index >= 0; index -= 1) {
          const currentId = sortedLevelIds[index]
          const nextId = sortedLevelIds[index + 1]
          const currentPosition = positions.get(currentId)
          const nextPosition = positions.get(nextId)
          if (!currentPosition || !nextPosition) continue
          const minGap = getMinGap(currentId, nextId)
          if (nextPosition.y - currentPosition.y < minGap) {
            positions.set(currentId, {
              ...currentPosition,
              y: nextPosition.y - minGap
            })
          }
        }
        return
      }

      for (let index = 1; index < sortedLevelIds.length; index += 1) {
        applyMinGap(sortedLevelIds[index - 1], sortedLevelIds[index])
      }

      const desiredCenter = sortedLevelIds.reduce((total, nodeId) => total + getTargetY(nodeId), 0) / Math.max(sortedLevelIds.length, 1)
      const actualCenter = sortedLevelIds.reduce((total, nodeId) => total + (positions.get(nodeId)?.y || 0), 0) / Math.max(sortedLevelIds.length, 1)
      const shiftDelta = desiredCenter - actualCenter

      sortedLevelIds.forEach((nodeId) => {
        if (fixedNodeIds.includes(nodeId)) return
        const currentPosition = positions.get(nodeId)
        if (!currentPosition) return
        positions.set(nodeId, {
          ...currentPosition,
          y: currentPosition.y + shiftDelta
        })
      })
    })
  }

  const smoothMergeNodes = () => {
    nodes.forEach((node) => {
      const currentPosition = positions.get(node.id)
      if (!currentPosition) return

      if ((incomingCount.get(node.id) || 0) > 1) {
        if (mainPathSet.has(node.id)) {
          positions.set(node.id, {
            ...currentPosition,
            y: mainPathCenterY
          })
          return
        }

        const parentYs = (parentsMap.get(node.id) || [])
          .map((parentId) => positions.get(parentId)?.y)
          .filter((value): value is number => typeof value === 'number')
        if (parentYs.length > 0) {
          positions.set(node.id, {
            ...currentPosition,
            y: parentYs.reduce((total, value) => total + value, 0) / parentYs.length
          })
        }
      }
    })
  }

  const straightenSimpleChains = () => {
    edges
      .filter((edge) => (outgoingCount.get(edge.sourceNodeId) || 0) === 1 && (incomingCount.get(edge.targetNodeId) || 0) === 1)
      .forEach((edge) => {
        const parentPosition = positions.get(edge.sourceNodeId)
        const childPosition = positions.get(edge.targetNodeId)
        if (!parentPosition || !childPosition) return

        positions.set(edge.targetNodeId, {
          ...childPosition,
          y: parentPosition.y
        })
      })
  }

  const alignMainPath = () => {
    mainPathIds.forEach((nodeId) => {
      const currentPosition = positions.get(nodeId)
      if (!currentPosition) return
      positions.set(nodeId, {
        ...currentPosition,
        y: mainPathCenterY
      })
    })
  }

  const simpleChainNodeIds = new Set<string>()
  edges
    .filter((edge) => (outgoingCount.get(edge.sourceNodeId) || 0) === 1 && (incomingCount.get(edge.targetNodeId) || 0) === 1)
    .forEach((edge) => {
      simpleChainNodeIds.add(edge.sourceNodeId)
      simpleChainNodeIds.add(edge.targetNodeId)
    })

  applyLevelCollisionResolution()
  smoothMergeNodes()
  alignMainPath()
  applyLevelCollisionResolution(simpleChainNodeIds)
  smoothMergeNodes()
  straightenSimpleChains()
  alignMainPath()

  return positions
}

const applyEditorEditConfig = () => {
  if (!lf) return

  const editable = !isViewMode.value
  lf.updateEditConfig({
    adjustNodePosition: editable,
    adjustEdge: editable,
    adjustEdgeMiddle: editable,
    adjustEdgeStartAndEnd: editable,
    adjustEdgeStart: editable,
    adjustEdgeEnd: editable,
    textEdit: editable,
    nodeTextEdit: editable,
    edgeTextEdit: editable,
    edgeSelectedOutline: true,
    nodeSelectedOutline: true,
    hideAnchors: false,
    stopScrollGraph: true,
    stopZoomGraph: true
  })
}

const syncSelectedNodePosition = (nodeId: string, position: LayoutPosition) => {
  if (selectedNode.value?.id !== nodeId) return

  selectedNode.value = {
    ...selectedNode.value,
    x: position.x,
    y: position.y
  }
  nodeForm.positionX = position.x
  nodeForm.positionY = position.y
}

// 初始化LogicFlow
const initLogicFlow = () => {
  if (!logicFlowContainer.value) return

  lf = new LogicFlow({
    container: logicFlowContainer.value,
    width: logicFlowContainer.value.offsetWidth,
    height: logicFlowContainer.value.offsetHeight,
    grid: {
      size: 10,
      visible: true,
      type: 'dot'
    },
    keyboard: {
      enabled: true
    },
    stopScrollGraph: true,
    stopZoomGraph: true,
    style: {
      rect: {
        rx: 5,
        ry: 5,
        strokeWidth: 2
      },
      circle: {
        r: 40,
        strokeWidth: 2
      },
      diamond: {
        strokeWidth: 2
      }
    }
  })

  // 注册自定义节点
  registerCustomNodes(lf)
  applyEditorEditConfig()

  // 监听节点点击事件
  lf.on('node:click', ({ data }) => {
    handleNodeClick(data)
  })

  // 监听边点击事件
  lf.on('edge:click', ({ data }) => {
    handleEdgeClick(data)
  })

  // 监听画布点击事件（取消选中）
  lf.on('blank:click', () => {
    drawerVisible.value = false
    selectedNode.value = null
    selectedEdge.value = null
  })

  // 监听节点/连线结构性变更（approved 版本编辑时用于检测配置是否变化）
  lf.on('node:dnd-add', () => { configDirty.value = true })
  lf.on('node:delete', () => { configDirty.value = true })
  lf.on('edge:add', () => { configDirty.value = true })
  lf.on('edge:delete', () => { configDirty.value = true })

  // 渲染初始数据
  lf.render({
    nodes: [],
    edges: []
  })
}

// 处理节点拖拽开始
const handleNodeDragStart = (node: any) => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  const nodeId = `${node.type}_${Date.now()}`
  lf.dnd.startDrag({
    type: node.type,
    text: node.label,
    properties: {
      nodeId,
      nodeType: node.type,
      nodeName: node.label,
      assigneeUserIds: [],
      properties: {}
    }
  })
}

// 评分维度管理
const addRatingDimension = () => {
  if (!nodeForm.ratingConfig) {
    nodeForm.ratingConfig = { enabled: true, required: false, showInStatistics: true, dimensions: [] }
  }
  nodeForm.ratingConfig.dimensions.push({
    key: `dim_${Date.now()}`,
    name: '',
    description: '',
    minLabel: '很差',
    maxLabel: '非常好'
  })
}

const removeRatingDimension = (idx: number) => {
  if (nodeForm.ratingConfig) {
    nodeForm.ratingConfig.dimensions.splice(idx, 1)
  }
}

// 处理节点点击
const handleNodeClick = (data: any) => {
  selectedNode.value = data
  selectedEdge.value = null
  const savedNextNode = data.properties?.[SELECTED_NEXT_NODE_PROPERTY]
  selectedNextNode.value = typeof savedNextNode === 'string' && nextNodeNames.value.includes(savedNextNode) ? savedNextNode : ''
  drawerTitle.value = nodeDrawerTitle.value
  drawerVisible.value = true
  adjustDrawerSize()

  // 填充表单
  Object.assign(nodeForm, {
    nodeId: data.id,
    nodeType: data.type,
    nodeName: data.text?.value || '',
    positionX: data.x,
    positionY: data.y,
    assigneeType: data.properties?.assigneeType,
    assigneeRoleId: data.properties?.assigneeRoleId,
    assigneeRoleGroupId: data.properties?.assigneeRoleGroupId,
    assigneeOrgId: data.properties?.assigneeOrgId,
    orgScopeType: data.properties?.orgScopeType ?? 'include_children',
    assigneeUserIds: data.properties?.assigneeUserIds || [],
    timeoutHours: data.properties?.timeoutHours,
    timeoutAction: data.properties?.timeoutAction,
    nodeStatusCode: data.properties?.nodeStatusCode ?? data.properties?.properties?.nodeStatusCode,
    allowCancel: data.properties?.allowCancel ?? data.properties?.properties?.allowCancel ?? true,
    projectRequired: data.properties?.projectRequired ?? data.properties?.properties?.projectRequired ?? false,
    requireAttachment: data.properties?.requireAttachment ?? data.properties?.properties?.requireAttachment ?? false,
    ratingConfig: data.properties?.ratingConfig ?? data.properties?.properties?.ratingConfig ?? {
      enabled: false,
      required: false,
      showInStatistics: true,
      dimensions: []
    },
    // 会签配置
    countersignEnabled: data.properties?.countersignEnabled ?? false,
    countersignStrategy: data.properties?.countersignStrategy ?? 'ALL',
    countersignMode: data.properties?.countersignMode ?? 'FIXED',
    countersignApprovers: data.properties?.countersignApprovers || [],
    parallelType: data.properties?.parallelType ?? 'AND',
    parallelBranches: data.properties?.branches || [],
    properties: data.properties || {}
  })

  // 条件节点：收集所有出边构建 conditionBranches
  if (data.type === 'condition' && lf) {
    const graphData = lf.getGraphData() as any
    const nodeNameMap = new Map<string, string>()
    ;(graphData.nodes || []).forEach((n: any) => {
      if (n.id) nodeNameMap.set(n.id, n.text?.value || n.id)
    })
    const outEdges = (graphData.edges || []).filter((e: any) => e.sourceNodeId === data.id)
    nodeForm.conditionBranches = outEdges.map((edge: any) => ({
      edgeId: edge.id,
      targetNodeId: edge.targetNodeId,
      targetNodeName: nodeNameMap.get(edge.targetNodeId) || edge.targetNodeId,
      label: edge.text?.value || '',
      condition: edge.properties?.condition || { logic: 'AND', rules: [] }
    }))
  } else {
    nodeForm.conditionBranches = []
  }
  normalizeCurrentNodeProjectRequired()
}

// 处理边点击
const handleEdgeClick = (data: any) => {
  selectedEdge.value = data
  selectedNode.value = null
  drawerTitle.value = edgeDrawerTitle.value
  drawerVisible.value = true
  adjustDrawerSize()

  // 填充表单
  edgeForm.label = data.text?.value || ''
  edgeForm.conditionExpr = data.properties?.condition?.expr || ''
  // 从边数据还原结构化条件（修复 edgeConditionModel 不持久化 bug）
  const savedCondition = data.properties?.condition
  if (savedCondition?.rules?.length) {
    edgeConditionModel.value = {
      logic: savedCondition.logic === 'OR' ? 'OR' : 'AND',
      rules: savedCondition.rules as ConditionRule[]
    }
  } else {
    edgeConditionModel.value = { logic: 'AND', rules: [{ field: 'type', operator: 'eq', value: '' }] }
  }
}

// 保存节点配置
// 修复 P1：客户端连通性预校验（与服务端 WorkflowConfigServiceImpl.validateConfigStructure 对齐）
// 避免无效工作流被提交，提示用户尽早修正
const validateBeforeSave = (): { valid: boolean; error?: string } => {
  if (!lf) return { valid: false, error: '画布未初始化' }

  const graphData = lf.getGraphData() as any
  const nodes = graphData?.nodes || []
  const edges = graphData?.edges || []

  if (nodes.length === 0) {
    return { valid: false, error: '工作流必须至少包含一个节点' }
  }

  const nodeTypes = new Set<string>()
  const nodeIds = new Set<string>()
  for (const node of nodes) {
    if (node.type) {
      nodeTypes.add(String(node.type).toLowerCase())
    }
    if (node.id) {
      nodeIds.add(String(node.id))
    }
  }
  if (!nodeTypes.has('start')) {
    return { valid: false, error: '工作流必须包含开始节点' }
  }
  if (!nodeTypes.has('end')) {
    return { valid: false, error: '工作流必须包含结束节点' }
  }

  const terminalStates = new Set(['cancelled', 'accepted', 'rejected'])
  for (const edge of edges) {
    const sourceId = edge.sourceNodeId ? String(edge.sourceNodeId) : ''
    const targetId = edge.targetNodeId ? String(edge.targetNodeId) : ''
    if (sourceId && !terminalStates.has(sourceId.toLowerCase()) && !nodeIds.has(sourceId)) {
      return { valid: false, error: `连线引用了不存在的源节点: ${sourceId}` }
    }
    if (targetId && !terminalStates.has(targetId.toLowerCase()) && !nodeIds.has(targetId)) {
      return { valid: false, error: `连线引用了不存在的目标节点: ${targetId}` }
    }
  }
  return { valid: true }
}

const handleSaveNodeConfig = () => {
  if (!lf || !selectedNode.value) return

  normalizeCurrentNodeProjectRequired()
  if (nodeForm.countersignEnabled && nodeForm.countersignMode === 'FIXED') {
    ensureDefaultCountersignApprover()
  }

  const validNextNode = nextNodeNames.value.includes(selectedNextNode.value) ? selectedNextNode.value : ''
  selectedNextNode.value = validNextNode

  const nodeData = {
    id: nodeForm.nodeId,
    type: nodeForm.nodeType,
    x: nodeForm.positionX,
    y: nodeForm.positionY,
    text: nodeForm.nodeName,
    properties: {
      ...nodeForm.properties,
      nodeId: nodeForm.nodeId,
      nodeType: nodeForm.nodeType,
      nodeName: nodeForm.nodeName,
      assigneeType: nodeForm.assigneeType,
      assigneeRoleId: nodeForm.assigneeRoleId,
      assigneeRoleGroupId: nodeForm.assigneeRoleGroupId,
      assigneeOrgId: nodeForm.assigneeOrgId,
      orgScopeType: nodeForm.orgScopeType,
      assigneeUserIds: nodeForm.assigneeUserIds,
      timeoutHours: nodeForm.timeoutHours,
      timeoutAction: nodeForm.timeoutAction,
      nodeStatusCode: nodeForm.nodeStatusCode,
      allowCancel: nodeForm.allowCancel,
      projectRequired: showProjectRequiredCheckbox.value ? nodeForm.projectRequired : false,
      requireAttachment: nodeForm.requireAttachment,
      ratingConfig: nodeForm.nodeType === 'approval' ? nodeForm.ratingConfig : undefined,
      [SELECTED_NEXT_NODE_PROPERTY]: validNextNode,
      // 会签配置
      countersignEnabled: nodeForm.countersignEnabled,
      countersignStrategy: nodeForm.countersignStrategy,
      countersignMode: nodeForm.countersignMode,
      countersignApprovers: nodeForm.countersignApprovers,
      parallelType: nodeForm.parallelType,
      branches: nodeForm.parallelBranches,
    }
  }

  lf.setProperties(nodeForm.nodeId!, nodeData.properties)
  lf.updateText(nodeForm.nodeId!, nodeForm.nodeName || '')

  // 条件节点：将 conditionBranches 中的条件同步写回各边
  if (nodeForm.nodeType === 'condition' && nodeForm.conditionBranches && lf) {
    for (const branch of nodeForm.conditionBranches) {
      const existingProps = lf.getProperties(branch.edgeId) || {}
      lf.setProperties(branch.edgeId, {
        ...existingProps,
        condition: branch.condition
      })
      if (branch.label) {
        lf.updateText(branch.edgeId, branch.label)
      }
    }
  }

  ElMessage.success('节点配置已保存')
  configDirty.value = true
  drawerVisible.value = false
}

// 保存边配置
const handleSaveEdgeConfig = () => {
  if (!lf || !selectedEdge.value) return

  const edgeProperties = {
    label: edgeForm.label,
    condition: {
      logic: edgeConditionModel.value.logic || 'AND',
      rules: edgeConditionModel.value.rules || [],
      expr: edgeForm.conditionExpr
    }
  }

  lf.setProperties(selectedEdge.value.id, edgeProperties)
  if (edgeForm.label) {
    lf.updateText(selectedEdge.value.id, edgeForm.label)
  }

  ElMessage.success('连线配置已保存')
  configDirty.value = true
  drawerVisible.value = false
}

// 删除节点
const handleDeleteNode = async () => {
  if (!lf || !selectedNode.value) return

  try {
    await ElMessageBox.confirm('确定要删除该节点吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.deleteNode(selectedNode.value.id)
    drawerVisible.value = false
    selectedNode.value = null
    ElMessage.success('节点已删除')
  } catch {
    // 用户取消
  }
}

// 删除边
const handleDeleteEdge = async () => {
  if (!lf || !selectedEdge.value) return

  try {
    await ElMessageBox.confirm('确定要删除该连线吗？', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.deleteEdge(selectedEdge.value.id)
    drawerVisible.value = false
    selectedEdge.value = null
    ElMessage.success('连线已删除')
  } catch {
    // 用户取消
  }
}

// 获取节点类型标签
const getNodeTypeLabel = (type?: string) => {
  const node = nodeTypes.find(n => n.type === type)
  return node ? node.label : type
}

// 放大
const handleZoomIn = () => {
  if (!lf) return
  lf.zoom(true)
}

// 缩小
const handleZoomOut = () => {
  if (!lf) return
  lf.zoom(false)
}

// 重置缩放
const handleResetZoom = () => {
  if (!lf) return
  lf.resetZoom()
}

// 格式化排版
const handleFormatLayout = () => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  const graphData = getLayoutGraphData()
  if (!graphData || graphData.nodes.length === 0) {
    ElMessage.warning('画布中暂无可排版的节点')
    return
  }

  const positions = buildFormattedLayout(graphData.nodes, graphData.edges)

  positions.forEach((position, nodeId) => {
    lf!.graphModel.moveNode2Coordinate(nodeId, position.x, position.y, true)
    syncSelectedNodePosition(nodeId, position)
  })

  lf.fitView(60, 80)
  ElMessage.success('已完成格式化排版')
}

// 清空画布
const handleClearCanvas = async () => {
  if (isViewMode.value) {
    ElMessage.warning('查看模式下不能编辑')
    return
  }
  if (!lf) return

  try {
    await ElMessageBox.confirm('确定要清空画布吗？此操作不可恢复。', '确认清空', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    lf.clearData()
    ElMessage.success('画布已清空')
  } catch {
    // 用户取消
  }
}

// 保存草稿
const handleSave = async () => {
  if (!lf) return

  // 修复 P1：保存前客户端连通性校验（与服务端 validateConfigStructure 对齐）
  const clientValidation = validateBeforeSave()
  if (!clientValidation.valid) {
    ElMessage.error(clientValidation.error || '工作流配置不合法')
    return
  }

  // approved 版本修改了节点/连线时的确认提示
  if (currentVersion.value?.activationStatus === 'approved' && configDirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前版本已审核通过，修改节点/连线配置后保存将使版本回退为草稿状态，需重新提交审核。确定继续保存吗？',
        '版本状态变更确认',
        { confirmButtonText: '确定保存', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }
  }

  saving.value = true
  try {
    const graphData = lf.getGraphData() as any
    const projectId = currentProjectId.value
    const desiredVersionMeta = getDesiredVersionMeta()
    if ((currentVersion.value || versionForm.version.trim() || versionForm.name.trim()) && !desiredVersionMeta) {
      return false
    }

    // 转换为后端需要的格式
    const config: WorkflowConfigDTO = {
      versionId: currentVersion.value?.id,
      version: desiredVersionMeta?.version,
      versionName: desiredVersionMeta?.name,
      nodes: graphData.nodes.map((node: any) => ({
        nodeId: node.id!,
        nodeType: node.type as any,
        nodeName: node.text?.value || '',
        positionX: node.x!,
        positionY: node.y!,
        assigneeType: node.properties?.assigneeType,
        assigneeRoleId: node.properties?.assigneeRoleId,
        assigneeRoleGroupId: node.properties?.assigneeRoleGroupId,
        assigneeOrgId: node.properties?.assigneeOrgId,
        assigneeUserIds: node.properties?.assigneeUserIds,
        timeoutHours: node.properties?.timeoutHours,
        timeoutAction: node.properties?.timeoutAction,
        properties: node.properties
      })),
      edges: graphData.edges.map((edge: any) => ({
        edgeId: edge.id!,
        sourceNodeId: edge.sourceNodeId!,
        targetNodeId: edge.targetNodeId!,
        label: edge.text?.value,
        condition: edge.properties?.condition,
        properties: edge.properties
      }))
    }

    const savedVersion = await saveWorkflowConfig(projectId, config)
    if (savedVersion) {
      applyCurrentVersion(savedVersion)
      await loadVersionHistory()
      await syncEditorVersionRoute(savedVersion.id, projectId)
      syncVersionForm(savedVersion)
    }

    ElMessage.success('保存成功')
    return true
  } catch (error) {
    return false
  } finally {
    saving.value = false
  }
}

// 提交审核
const handleSubmit = async () => {
  if (!lf) return

  // 先保存
  const saved = await handleSave()
  if (!saved) return

  submitting.value = true
  try {
    await submitForApproval(currentProjectId.value)

    ElMessage.success('提交审核成功')
    router.push(returnMenuPath.value)
  } catch (error) {
  } finally {
    submitting.value = false
  }
}

// 关闭抽屉
const handleDrawerClose = () => {
  drawerVisible.value = false
  selectedNode.value = null
  selectedEdge.value = null
}

// ========== 抽屉拖拽调整宽度 ==========
function onResizeStart(e: MouseEvent) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = parseInt(drawerSize.value) || 500
  const maxWidth = window.innerWidth * MAX_DRAWER_WIDTH_RATIO

  const onMouseMove = (ev: MouseEvent) => {
    // RTL drawer: width increases when mouse moves left
    const delta = startX - ev.clientX
    const newWidth = Math.min(Math.max(startWidth + delta, MIN_DRAWER_WIDTH), maxWidth)
    drawerSize.value = `${Math.round(newWidth)}px`
  }

  const onMouseUp = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// ========== 根据节点/边类型自适应宽度 ==========
function adjustDrawerSize() {
  if (selectedNode.value) {
    const type = selectedNode.value.properties?.nodeType
    if (type === 'condition') {
      drawerSize.value = '680px'
    } else if (type === 'parallel') {
      drawerSize.value = '580px'
    } else {
      drawerSize.value = '500px'
    }
  } else if (selectedEdge.value) {
    drawerSize.value = '520px'
  }
}

// 离开前确认：处理"工作流发生改变，存在有编辑"场景。
// 返回值约定：
//   true  -> 允许离开
//   false -> 留在当前页
// 弹窗提供三个选项：
//   - 保存草稿并离开（先 handleSave，成功才离开）
//   - 不保存直接离开
//   - 取消（关闭弹窗，留在当前页）
const confirmLeaveIfDirty = async (): Promise<boolean> => {
  // 只读模式或没有未保存修改，直接放行
  if (isViewMode.value || !configDirty.value) return true

  try {
    await ElMessageBox.confirm(
      '当前工作流存在未保存的修改，离开前建议先保存草稿。继续离开将丢失这部分修改，是否保存草稿？',
      '存在未保存的修改',
      {
        confirmButtonText: '保存草稿并离开',
        cancelButtonText: '不保存直接离开',
        showClose: true,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        distinguishCancelAndClose: true,
        type: 'warning',
        buttonSize: 'default'
      }
    )
    // 用户点了"保存草稿并离开"
    const saved = await handleSave()
    return saved === true
  } catch (action: any) {
    // distinguishCancelAndClose: 'cancel' = "不保存直接离开"，'close' = 关闭弹窗
    if (action === 'cancel') return true
    return false
  }
}

// 返回
const goBack = async () => {
  const allowed = await confirmLeaveIfDirty()
  if (!allowed) return
  router.push(returnMenuPath.value)
}

// 路由切换守卫：用户在侧边栏点击其他菜单、或者编辑 URL 触发路由变化时拦截
onBeforeRouteLeave(async () => {
  return await confirmLeaveIfDirty()
})

// 浏览器刷新 / 关闭标签页 / 关闭浏览器兜底
const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
  if (isViewMode.value || !configDirty.value) return
  e.preventDefault()
  // 现代浏览器忽略自定义文案，统一显示原生提示
  e.returnValue = ''
}

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnloadHandler)
})

const loadVersionHistory = async () => {
  try {
    versionHistory.value = await getVersionHistory(currentProjectId.value) || []
    applySuggestedVersionMeta()
  } catch {
    versionHistory.value = []
  }
}

// 加载工作流配置
const loadWorkflowConfig = async () => {
  const versionId = route.query.versionId
  const mode = route.query.mode

  isViewMode.value = mode === 'view'
  isEditMode.value = mode === 'edit'
  applyEditorEditConfig()

  if (versionId) {
    try {
      const version = await getVersionConfig(Number(versionId))
      if (version) {
        applyCurrentVersion(version)
        syncVersionForm(version)

        // 渲染配置
        if (lf && version.config) {
          const graphData = {
            nodes: version.config.nodes.map(node => ({
              id: node.nodeId,
              type: node.nodeType,
              x: node.positionX,
              y: node.positionY,
              text: node.nodeName,
              properties: {
                ...(node.properties || {}),
                assigneeType: node.assigneeType,
                assigneeRoleId: node.assigneeRoleId,
                assigneeUserIds: node.assigneeUserIds,
                timeoutHours: node.timeoutHours,
                timeoutAction: node.timeoutAction,
                nodeStatusCode: node.properties?.nodeStatusCode ?? (node.properties as any)?.properties?.nodeStatusCode,
                requireAttachment: node.properties?.requireAttachment ?? (node.properties as any)?.properties?.requireAttachment ?? false
              }
            })),
            edges: version.config.edges.map(edge => ({
              id: edge.edgeId,
              type: 'polyline',
              sourceNodeId: edge.sourceNodeId,
              targetNodeId: edge.targetNodeId,
              text: edge.label,
              properties: edge
            }))
          }

          lf.render(graphData)
        }
      }
    } catch (error) {
      ElMessage.error('加载配置失败')
    }
  } else {
    applySuggestedVersionMeta()
  }
}

const loadNodeStatuses = async () => {
  try {
    const result = await nodeStatusApi.list() as any
    nodeStatusOptions.value = Array.isArray(result) ? result : (result?.data || [])
  } catch (error) {
    nodeStatusOptions.value = []
  }
}

onMounted(() => {
  initLogicFlow()
  loadNodeStatuses()
  loadVersionHistory()
  loadWorkflowConfig()
  loadRoleAndUserList()
})

async function loadRoleAndUserList() {
  assigneeOptionsLoading.value = true
  try {
    const [rolesRes, roleGroupsRes, usersRes, orgTreeRes]: any[] = await Promise.all([
      roleApi.getRoleList(),
      roleApi.getRoleGroups(),
      userApi.getUserList({ pageNum: 1, pageSize: 999 }),
      userApi.getOrgTree()
    ])
    roleList.value = (rolesRes?.data ?? rolesRes ?? [])
    roleGroupList.value = (roleGroupsRes?.data ?? roleGroupsRes ?? [])
    allUserList.value = (usersRes?.list ?? [])
    orgTreeData.value = (orgTreeRes?.data ?? orgTreeRes ?? [])
  } catch {
    // ignore
  } finally {
    assigneeOptionsLoading.value = false
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler)
  if (lf) {
    lf.destroy()
    lf = null
  }
})
</script>

<style scoped lang="scss">
.workflow-editor-page {
  height: 100vh;
  padding: 0;
  background: #f5f7fa;

  .editor-card {
    height: 100%;
    margin: 0;

    :deep(.el-card__body) {
      height: calc(100% - 60px);
      padding: 0;
    }
  }

  .editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;

      .title-info {
        h2 {
          margin: 0;
          font-size: 18px;
          font-weight: 600;
        }

        .scope-tag {
          margin-top: 6px;
          font-size: 13px;
          color: var(--color-muted-text);
        }

        .version-tag {
          font-size: 14px;
          color: var(--color-muted-text);
          margin-left: 8px;
        }

        .version-editor {
          display: flex;
          align-items: flex-start;
          flex-wrap: wrap;
          gap: 12px;
          margin-top: 8px;
        }

        .version-input-group {
          display: flex;
          flex-direction: column;
          gap: 4px;
        }

        .version-number-input {
          display: flex;
          align-items: center;
          gap: 6px;

          .version-prefix {
            font-size: 14px;
            color: var(--color-text-secondary);
            font-weight: 600;
          }

          :deep(.el-input) {
            width: 140px;
          }
        }

        .version-meta-hint {
          font-size: 12px;
          line-height: 1.4;

          &.info {
            color: var(--color-muted-text);
          }

          &.success {
            color: var(--color-success);
          }

          &.warning {
            color: var(--color-warning);
          }

          &.error {
            color: var(--color-danger);
          }
        }

        .version-name-input {
          width: 320px;
        }
      }
    }

    .header-right {
      display: flex;
      gap: 12px;
    }
  }

  .approved-edit-warning {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    margin: 0;
    font-size: 13px;
    line-height: 1.5;
    color: #b88230;
    background: #fff7e6;
    border-top: 1px solid #f3d19e;

    .el-icon {
      flex-shrink: 0;
      font-size: 16px;
    }
  }

  .editor-container {
    display: flex;
    height: 100%;
    background: #fff;

    .toolbar-left {
      width: 200px;
      border-right: 1px solid #e4e7ed;
      padding: 16px;
      overflow-y: auto;

      .toolbar-title {
        font-size: 14px;
        font-weight: 600;
        margin-bottom: 12px;
        color: var(--color-text-primary);
      }

      .node-palette {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 24px;

        .palette-node {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 10px;
          border: 1px solid #dcdfe6;
          border-radius: 4px;
          cursor: move;
          transition: all 0.3s;

          &:hover {
            border-color: var(--color-accent);
            background: var(--color-info-light);
          }

          &.is-readonly {
            cursor: not-allowed;
            opacity: 0.65;
          }

          &.is-readonly:hover {
            border-color: #dcdfe6;
            background: transparent;
          }

          .node-icon {
            font-size: 18px;
          }

          .node-label {
            font-size: 14px;
          }

          &.start {
            border-color: var(--color-success);
            .node-icon { color: var(--color-success); }
          }

          &.approval {
            border-color: var(--color-accent);
            .node-icon { color: var(--color-accent); }
          }

          &.cc {
            border-color: var(--color-warning);
            .node-icon { color: var(--color-warning); }
          }

          &.condition {
            border-color: var(--color-danger);
            .node-icon { color: var(--color-danger); }
          }

          &.end {
            border-color: var(--color-muted-text);
            .node-icon { color: var(--color-muted-text); }
          }
        }
      }

      .toolbar-section {
        margin-top: 24px;
      }

      .toolbar-actions {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }

      .toolbar-action-btn {
        width: 100%;
        margin-left: 0;
        justify-content: center;

        :deep(span) {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          width: 100%;
        }
      }

      .help-section {
        padding: 12px;
        border-radius: 6px;
        background: #f5f7fa;

        ol {
          margin: 0;
          padding-left: 18px;
          color: var(--color-text-secondary);
          font-size: 12px;
          line-height: 1.7;
        }
      }
    }

    .canvas-container {
      flex: 1;
      position: relative;
      background: #fafafa;

      .logicflow-container {
        width: 100%;
        height: 100%;
      }
    }
  }

  .node-config-panel,
  .edge-config-panel {
    padding: 16px;
  }

  .node-rule-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: 24px;
    row-gap: 8px;
    width: 100%;

    :deep(.el-checkbox) {
      align-items: center;
      height: 24px;
      margin-right: 0;
      margin-left: 0;
    }

    :deep(.el-checkbox__label) {
      line-height: 24px;
    }
  }

  .node-config-footer {
    position: sticky;
    bottom: 0;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    padding: 10px 16px;
    margin: 16px -16px -16px;
    background: var(--el-bg-color);
    border-top: 1px solid var(--el-border-color-lighter);

    :deep(.el-button) {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }
}

/* ===== 抽屉拖拽调整手柄 ===== */
:deep(.config-drawer .el-drawer__body) {
  position: relative;
  overflow-x: hidden;
}

.drawer-resize-handle {
  position: absolute;
  top: 0;
  left: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;

  &:hover,
  &.active {
    background: rgba(64, 158, 255, 0.08);
  }

  .resize-bar {
    width: 2px;
    height: 32px;
    border-radius: 1px;
    background: var(--el-border-color, #dcdfe6);
    transition: background 0.2s;
  }

  &:hover .resize-bar,
  &.active .resize-bar {
    background: var(--el-color-primary, #409eff);
    height: 48px;
  }
}
</style>
