<template>
  <div class="workflow-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <div class="title">工作流配置</div>
            <div class="subtitle">
              需求状态流转仅由流程版本控制。保存版本只会生成配置快照，启用版本后才会发布到运行态。
            </div>
          </div>
          <div class="header-actions">
            <el-tag v-if="activeVersion" type="success" size="large">
              当前生效：V{{ activeVersion.version }} / {{ activeVersion.name }}
            </el-tag>
            <el-button @click="reloadData">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="page-alert"
        title="节点操作权限支持按角色和按人配置，配置入口仅对超级管理员或具有 workflow:config 权限的账号开放。"
      />

      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="节点配置" name="nodes">
          <div class="tab-content">
            <div class="toolbar">
              <div class="toolbar-left">
                <el-button type="primary" @click="openNodeDialog()">新增节点</el-button>
                <span class="text-muted">
                  节点代表需求状态。节点级权限控制“谁可以在该节点执行后续流转操作”。
                </span>
              </div>
              <el-button
                v-if="versions.length > 0"
                @click="loadActiveVersionIntoDraft"
              >
                载入当前版本到草稿
              </el-button>
            </div>

            <el-empty v-if="draftNodes.length === 0" description="当前草稿还没有节点，请先新增节点" />

            <el-table v-else :data="sortedNodes" border>
              <el-table-column prop="name" label="节点名称" min-width="180" />
              <el-table-column prop="color" label="颜色" width="130">
                <template #default="{ row }">
                  <span class="color-preview" :style="{ backgroundColor: row.color || '#409EFF' }" />
                  <span class="ml-2">{{ row.color || '#409EFF' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="isFinal" label="终态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.isFinal ? 'success' : 'info'">
                    {{ row.isFinal ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="80" />
              <el-table-column label="允许角色" min-width="220">
                <template #default="{ row }">
                  <span>{{ formatRoleSummary(row.allowedRoles) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="允许用户" min-width="220">
                <template #default="{ row }">
                  <span>{{ formatUserSummary(row.allowedUsers) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openNodeDialog(row)">编辑</el-button>
                  <el-button link type="danger" @click="deleteNode(row.nodeId)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="流转配置" name="transitions">
          <div class="tab-content">
            <div class="toolbar transitions-toolbar">
              <div class="toolbar-left">
                <el-button @click="resetSelection">取消选中</el-button>
                <el-button type="danger" :disabled="draftEdges.length === 0" @click="clearEdges">
                  清空流转
                </el-button>
                <span class="text-muted">
                  点击一个节点作为起点，再点击另一个节点即可创建流转；点击已有连线可编辑规则。
                </span>
              </div>
              <div class="toolbar-right">
                <el-input
                  v-model="newVersionName"
                  placeholder="输入版本名称，例如：评审流程 V2"
                  style="width: 260px"
                />
                <el-button
                  type="primary"
                  :loading="savingVersion"
                  :disabled="draftNodes.length === 0"
                  @click="saveAsVersion"
                >
                  保存为新版本
                </el-button>
              </div>
            </div>

            <div ref="graphRef" class="workflow-graph" />

            <div class="edge-table">
              <div class="section-title">流转规则明细</div>
              <el-empty v-if="draftEdges.length === 0" description="当前草稿还没有流转规则" />
              <el-table v-else :data="draftEdges" border>
                <el-table-column label="起始节点" min-width="140">
                  <template #default="{ row }">
                    {{ getNodeName(row.source) }}
                  </template>
                </el-table-column>
                <el-table-column label="目标节点" min-width="140">
                  <template #default="{ row }">
                    {{ getNodeName(row.target) }}
                  </template>
                </el-table-column>
                <el-table-column prop="label" label="按钮文案" min-width="160" />
                <el-table-column label="允许角色" min-width="200">
                  <template #default="{ row }">
                    {{ formatRoleSummary(row.allowedRoles) }}
                  </template>
                </el-table-column>
                <el-table-column label="必填字段" min-width="200">
                  <template #default="{ row }">
                    {{ formatFieldSummary(row.requiredFields) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="160">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openEdgeDialog(row)">编辑</el-button>
                    <el-button link type="danger" @click="deleteEdge(row.source, row.target)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="流程版本" name="versions">
          <div class="tab-content">
            <el-empty v-if="versions.length === 0" description="当前项目还没有工作流版本，请先在前两个页签配置后保存版本" />
            <el-table v-else :data="versions" border>
              <el-table-column prop="version" label="版本号" width="100" />
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="row.isActive ? 'success' : 'info'">
                    {{ row.isActive ? '当前生效' : '历史版本' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180" />
              <el-table-column label="操作" width="200">
                <template #default="{ row }">
                  <el-button link type="primary" @click="loadVersionToDraft(row)">载入草稿</el-button>
                  <el-button
                    v-if="!row.isActive"
                    link
                    type="success"
                    @click="activateVersion(row)"
                  >
                    启用
                  </el-button>
                  <span v-else class="text-muted">已生效</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog
      v-model="nodeDialogVisible"
      :title="editingNodeId ? '编辑节点' : '新增节点'"
      width="640px"
    >
      <el-form ref="nodeFormRef" :model="nodeForm" :rules="nodeRules" label-width="100px">
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="nodeForm.name" maxlength="30" placeholder="例如：待评审、开发中、已验收" />
        </el-form-item>
        <el-form-item label="节点颜色">
          <el-color-picker v-model="nodeForm.color" />
        </el-form-item>
        <el-form-item label="是否终态">
          <el-switch v-model="nodeForm.isFinal" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="nodeForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="允许角色">
          <el-select
            v-model="nodeForm.allowedRoles"
            multiple
            filterable
            clearable
            placeholder="不选则不按角色限制"
          >
            <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许用户">
          <el-select
            v-model="nodeForm.allowedUsers"
            multiple
            filterable
            clearable
            placeholder="不选则不按具体用户限制"
          >
            <el-option v-for="user in userOptions" :key="user.id" :label="user.label" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示">
          <div class="helper-text">
            同时配置角色和用户时，满足任一条件即可在该节点执行流转操作。内置动态角色支持：创建人、负责人、处理人。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNode">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="edgeDialogVisible"
      title="流转规则"
      width="640px"
    >
      <el-form :model="edgeForm" label-width="100px">
        <el-form-item label="起始节点">
          <el-input :model-value="getNodeName(edgeForm.source)" disabled />
        </el-form-item>
        <el-form-item label="目标节点">
          <el-input :model-value="getNodeName(edgeForm.target)" disabled />
        </el-form-item>
        <el-form-item label="按钮文案">
          <el-input v-model="edgeForm.label" maxlength="30" placeholder="例如：提交评审、打回修改、完成开发" />
        </el-form-item>
        <el-form-item label="允许角色">
          <el-select
            v-model="edgeForm.allowedRoles"
            multiple
            filterable
            clearable
            placeholder="不选则继承节点权限，不额外增加角色限制"
          >
            <el-option v-for="role in roleOptions" :key="role" :label="role" :value="role" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填字段">
          <el-select
            v-model="edgeForm.requiredFields"
            multiple
            filterable
            clearable
            placeholder="执行该流转前必须补齐的字段"
          >
            <el-option v-for="field in fieldOptions" :key="field" :label="field" :value="field" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件表达式">
          <el-input
            v-model="edgeForm.conditions"
            type="textarea"
            :rows="3"
            placeholder='选填，使用 JSON 表达条件，例如：{"priority":"P0"}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="editingEdgeKey" type="danger" plain @click="deleteCurrentEditingEdge">删除流转</el-button>
        <el-button @click="edgeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdge">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import * as echarts from 'echarts'
import {
  activateWorkflowVersion,
  createWorkflowVersion,
  getWorkflowStates,
  getWorkflowTransitions,
  getWorkflowVersions,
} from '@/api/modules/workflow'
import * as userApi from '@/api/modules/user'
import type { Position, User } from '@/types/user'
import type {
  WorkflowDefinition,
  WorkflowEdgeDefinition,
  WorkflowNodeDefinition,
  WorkflowState,
  WorkflowTransition,
  WorkflowVersion,
} from '@/types/workflow'

type NodePosition = {
  x: number
  y: number
}

const BUILTIN_ROLE_OPTIONS = ['创建人', '负责人', '处理人']
const FIELD_OPTIONS = [
  'title',
  'description',
  'type',
  'priority',
  'assigneeId',
  'moduleId',
  'iterationId',
  'estimatedHours',
  'dueDate',
]

const route = useRoute()
const projectId = computed(() => {
  const id = Number(route.query.projectId)
  return Number.isFinite(id) && id > 0 ? id : 1
})

const activeTab = ref('nodes')
const savingVersion = ref(false)
const graphRef = ref<HTMLDivElement | null>(null)
let graphChart: echarts.ECharts | null = null

const draftNodes = ref<WorkflowNodeDefinition[]>([])
const draftEdges = ref<WorkflowEdgeDefinition[]>([])
const versions = ref<WorkflowVersion[]>([])
const newVersionName = ref('')
const selectedSourceNodeId = ref<string | null>(null)
const nodePositions = ref<Record<string, NodePosition>>({})

const roleOptions = ref<string[]>([...BUILTIN_ROLE_OPTIONS])
const userOptions = ref<{ id: number; label: string }[]>([])

const nodeDialogVisible = ref(false)
const nodeFormRef = ref<FormInstance>()
const editingNodeId = ref<string | null>(null)
const nodeForm = ref<WorkflowNodeDefinition>(createEmptyNode())

const edgeDialogVisible = ref(false)
const editingEdgeKey = ref<string>('')
const edgeForm = ref<WorkflowEdgeDefinition>(createEmptyEdge())

const nodeRules = {
  name: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
}

const activeVersion = computed(() => versions.value.find((item) => !!item.isActive) || null)
const sortedNodes = computed(() =>
  [...draftNodes.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)),
)
const fieldOptions = FIELD_OPTIONS

function createEmptyNode(): WorkflowNodeDefinition {
  return {
    nodeId: '',
    name: '',
    type: 'state',
    color: '#409EFF',
    isFinal: false,
    sortOrder: draftNodes.value.length + 1,
    allowedRoles: [],
    allowedUsers: [],
    editableFields: [],
    requiredFields: [],
    availableActions: [],
  }
}

function createEmptyEdge(): WorkflowEdgeDefinition {
  return {
    source: '',
    target: '',
    label: '',
    allowedRoles: [],
    requiredFields: [],
    conditions: '',
  }
}

function normalizeStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean)
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) {
      return []
    }
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed)
        return Array.isArray(parsed) ? parsed.map((item) => String(item).trim()).filter(Boolean) : []
      } catch {
      }
    }
    return trimmed.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return []
}

function normalizeNumberArray(value: unknown): number[] {
  if (Array.isArray(value)) {
    return value
      .map((item) => Number(item))
      .filter((item) => Number.isFinite(item))
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) {
      return []
    }
    if (trimmed.startsWith('[')) {
      try {
        const parsed = JSON.parse(trimmed)
        return Array.isArray(parsed)
          ? parsed.map((item) => Number(item)).filter((item) => Number.isFinite(item))
          : []
      } catch {
      }
    }
    return trimmed
      .split(',')
      .map((item) => Number(item.trim()))
      .filter((item) => Number.isFinite(item))
  }
  return []
}

function getNodeName(nodeId: string) {
  return draftNodes.value.find((node) => node.nodeId === nodeId)?.name || nodeId
}

function getUserLabel(userId: number) {
  return userOptions.value.find((user) => user.id === userId)?.label || `用户${userId}`
}

function formatRoleSummary(roles?: string[] | null) {
  const list = normalizeStringArray(roles)
  return list.length > 0 ? list.join('、') : '不限'
}

function formatUserSummary(userIds?: number[] | null) {
  const ids = normalizeNumberArray(userIds)
  return ids.length > 0 ? ids.map((id) => getUserLabel(id)).join('、') : '不限'
}

function formatFieldSummary(fields?: string[] | null) {
  const list = normalizeStringArray(fields)
  return list.length > 0 ? list.join('、') : '无'
}

function hasNodePermission(node: WorkflowNodeDefinition) {
  return normalizeStringArray(node.allowedRoles).length > 0 || normalizeNumberArray(node.allowedUsers).length > 0
}

function generateNodeId() {
  return `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function findEdgeIndex(source: string, target: string) {
  return draftEdges.value.findIndex((edge) => edge.source === source && edge.target === target)
}

function getEdgeKey(source: string, target: string) {
  return `${source}->${target}`
}

function normalizeDefinition(definition: WorkflowVersion['definition']): WorkflowDefinition | null {
  if (!definition) {
    return null
  }

  let raw: any = definition
  if (typeof definition === 'string') {
    try {
      raw = JSON.parse(definition)
    } catch {
      return null
    }
  }

  const nodes = Array.isArray(raw?.nodes) ? raw.nodes : []
  const edges = Array.isArray(raw?.edges) ? raw.edges : []

  return {
    id: raw?.id ?? null,
    name: typeof raw?.name === 'string' ? raw.name : '',
    nodes: nodes
      .map((node: any, index: number) => ({
        nodeId: String(node?.nodeId || `node_${index + 1}`),
        name: String(node?.name || '').trim(),
        type: 'state',
        color: node?.color || '#409EFF',
        isFinal: !!node?.isFinal,
        sortOrder: Number.isFinite(Number(node?.sortOrder)) ? Number(node.sortOrder) : index + 1,
        allowedRoles: normalizeStringArray(node?.allowedRoles),
        allowedUsers: normalizeNumberArray(node?.allowedUsers),
        editableFields: normalizeStringArray(node?.editableFields),
        requiredFields: normalizeStringArray(node?.requiredFields),
        availableActions: normalizeStringArray(node?.availableActions),
      }))
      .filter((node: WorkflowNodeDefinition) => !!node.name),
    edges: edges
      .map((edge: any) => ({
        source: String(edge?.source || '').trim(),
        target: String(edge?.target || '').trim(),
        label: typeof edge?.label === 'string' ? edge.label : '',
        allowedRoles: normalizeStringArray(edge?.allowedRoles),
        requiredFields: normalizeStringArray(edge?.requiredFields),
        conditions: typeof edge?.conditions === 'string'
          ? edge.conditions
          : edge?.conditions
            ? JSON.stringify(edge.conditions)
            : '',
      }))
      .filter((edge: WorkflowEdgeDefinition) => !!edge.source && !!edge.target),
  }
}

function buildDefinition(versionName: string): WorkflowDefinition {
  return {
    id: null,
    name: versionName,
    nodes: sortedNodes.value.map((node) => ({
      nodeId: node.nodeId,
      name: node.name.trim(),
      type: 'state',
      color: node.color || '#409EFF',
      isFinal: !!node.isFinal,
      sortOrder: node.sortOrder || 0,
      allowedRoles: normalizeStringArray(node.allowedRoles),
      allowedUsers: normalizeNumberArray(node.allowedUsers),
      editableFields: [],
      requiredFields: [],
      availableActions: [],
    })),
    edges: draftEdges.value.map((edge) => ({
      source: edge.source,
      target: edge.target,
      label: edge.label?.trim() || '',
      allowedRoles: normalizeStringArray(edge.allowedRoles),
      requiredFields: normalizeStringArray(edge.requiredFields),
      conditions: typeof edge.conditions === 'string' ? edge.conditions.trim() : '',
    })),
  }
}

function validateDraftDefinition() {
  const errors: string[] = []
  if (draftNodes.value.length === 0) {
    errors.push('至少需要配置一个节点')
    return errors
  }

  const nodeIds = new Set<string>()
  const nodeNames = new Set<string>()
  const outgoing = new Map<string, Set<string>>()
  const incoming = new Map<string, Set<string>>()

  for (const node of draftNodes.value) {
    if (!node.nodeId) {
      errors.push('存在未生成 nodeId 的节点')
      continue
    }
    if (nodeIds.has(node.nodeId)) {
      errors.push(`节点ID重复：${node.nodeId}`)
    }
    nodeIds.add(node.nodeId)

    const name = node.name.trim()
    if (!name) {
      errors.push('节点名称不能为空')
    } else if (nodeNames.has(name)) {
      errors.push(`节点名称重复：${name}`)
    }
    nodeNames.add(name)
    outgoing.set(node.nodeId, new Set())
    incoming.set(node.nodeId, new Set())
  }

  for (const edge of draftEdges.value) {
    if (!nodeIds.has(edge.source)) {
      errors.push(`流转起始节点不存在：${edge.source}`)
      continue
    }
    if (!nodeIds.has(edge.target)) {
      errors.push(`流转目标节点不存在：${edge.target}`)
      continue
    }
    outgoing.get(edge.source)?.add(edge.target)
    incoming.get(edge.target)?.add(edge.source)
  }

  const startNodes = [...nodeIds].filter((nodeId) => (incoming.get(nodeId)?.size || 0) === 0)
  if (startNodes.length === 0) {
    errors.push('至少需要一个起始节点')
  }

  const finalNodes = draftNodes.value
    .filter((node) => !!node.isFinal)
    .map((node) => node.nodeId)
  if (finalNodes.length === 0) {
    errors.push('至少需要一个终态节点')
  }

  if (startNodes.length > 0) {
    const reachable = traverseGraph(startNodes, outgoing)
    for (const nodeId of nodeIds) {
      if (!reachable.has(nodeId)) {
        errors.push(`节点无法从起始节点到达：${getNodeName(nodeId)}`)
      }
    }
  }

  if (finalNodes.length > 0) {
    const reverseGraph = new Map<string, Set<string>>()
    for (const nodeId of nodeIds) {
      reverseGraph.set(nodeId, new Set())
    }
    for (const [source, targets] of outgoing.entries()) {
      for (const target of targets) {
        reverseGraph.get(target)?.add(source)
      }
    }
    const canReachFinal = traverseGraph(finalNodes, reverseGraph)
    for (const nodeId of nodeIds) {
      if (!canReachFinal.has(nodeId)) {
        errors.push(`节点无法流转到任一终态：${getNodeName(nodeId)}`)
      }
    }
  }

  return errors
}

function traverseGraph(startNodes: string[], graph: Map<string, Set<string>>) {
  const visited = new Set<string>()
  const queue = [...startNodes]
  while (queue.length > 0) {
    const current = queue.shift()
    if (!current || visited.has(current)) {
      continue
    }
    visited.add(current)
    for (const next of graph.get(current) || []) {
      if (!visited.has(next)) {
        queue.push(next)
      }
    }
  }
  return visited
}

function applyDraftDefinition(definition: WorkflowDefinition | null) {
  if (!definition) {
    draftNodes.value = []
    draftEdges.value = []
    selectedSourceNodeId.value = null
    nodePositions.value = {}
    return
  }

  draftNodes.value = definition.nodes.map((node) => ({
    ...createEmptyNode(),
    ...node,
    nodeId: node.nodeId,
    name: node.name,
    color: node.color || '#409EFF',
    isFinal: !!node.isFinal,
    sortOrder: node.sortOrder || 0,
    allowedRoles: normalizeStringArray(node.allowedRoles),
    allowedUsers: normalizeNumberArray(node.allowedUsers),
  }))
  draftEdges.value = definition.edges.map((edge) => ({
    ...createEmptyEdge(),
    ...edge,
    source: edge.source,
    target: edge.target,
    label: edge.label || '',
    allowedRoles: normalizeStringArray(edge.allowedRoles),
    requiredFields: normalizeStringArray(edge.requiredFields),
    conditions: typeof edge.conditions === 'string'
      ? edge.conditions
      : edge.conditions
        ? JSON.stringify(edge.conditions)
        : '',
  }))
  selectedSourceNodeId.value = null
  nodePositions.value = {}
}

function buildDraftFromRuntime(states: WorkflowState[], transitions: WorkflowTransition[]) {
  const nodes: WorkflowNodeDefinition[] = states.map((state, index) => ({
    nodeId: `runtime_${state.id}`,
    name: state.name,
    type: 'state',
    color: state.color || '#409EFF',
    isFinal: !!state.isFinal,
    sortOrder: Number.isFinite(Number(state.sortOrder)) ? Number(state.sortOrder) : index + 1,
    allowedRoles: [],
    allowedUsers: [],
    editableFields: [],
    requiredFields: [],
    availableActions: [],
  }))

  const nodeIdMap = new Map<number, string>()
  states.forEach((state) => {
    nodeIdMap.set(state.id, `runtime_${state.id}`)
  })

  const edges: WorkflowEdgeDefinition[] = transitions
    .map((transition) => ({
      source: nodeIdMap.get(transition.fromStateId) || '',
      target: nodeIdMap.get(transition.toStateId) || '',
      label: transition.label || '',
      allowedRoles: normalizeStringArray(transition.allowedRoles),
      requiredFields: normalizeStringArray(transition.requiredFields),
      conditions: typeof transition.conditions === 'string'
        ? transition.conditions
        : transition.conditions
          ? JSON.stringify(transition.conditions)
          : '',
    }))
    .filter((edge) => !!edge.source && !!edge.target)

  applyDraftDefinition({
    id: null,
    name: '运行态流程',
    nodes,
    edges,
  })
}

async function loadVersions() {
  const list = (await getWorkflowVersions(projectId.value)) as unknown as any[]
  versions.value = (list || []).map((item: any) => ({
    ...item,
    isActive: !!item.isActive,
  })) as WorkflowVersion[]
}

async function loadDraftSource() {
  const active = versions.value.find((item) => !!item.isActive)
  if (active) {
    applyDraftDefinition(normalizeDefinition(active.definition))
    return
  }

  if (versions.value.length > 0) {
    applyDraftDefinition(normalizeDefinition(versions.value[0].definition))
    return
  }

  const [states, transitions] = await Promise.all([
    getWorkflowStates(projectId.value) as unknown as Promise<WorkflowState[]>,
    getWorkflowTransitions(projectId.value) as unknown as Promise<WorkflowTransition[]>,
  ])
  buildDraftFromRuntime(states || [], transitions || [])
}

async function reloadData() {
  try {
    await loadVersions()
    await loadDraftSource()
    if (activeTab.value === 'transitions') {
      await nextTick()
      renderGraph()
    }
  } catch {
    ElMessage.error('工作流数据加载失败')
  }
}

function loadVersionToDraft(version: WorkflowVersion, options?: { silent?: boolean }) {
  applyDraftDefinition(normalizeDefinition(version.definition))
  newVersionName.value = `${version.name}-副本`
  if (!options?.silent) {
    ElMessage.success(`已载入版本 V${version.version} 到草稿`)
  }
}

function loadActiveVersionIntoDraft() {
  if (!activeVersion.value) {
    ElMessage.warning('当前没有已生效版本')
    return
  }
  loadVersionToDraft(activeVersion.value)
}

async function loadRoleAndUserOptions() {
  try {
    const [positions, users] = await Promise.all([
      userApi.getPositionList() as any,
      userApi.getUserList({ pageNum: 1, pageSize: 1000 }) as any,
    ])

    const positionList: Position[] = Array.isArray(positions) ? positions : positions?.data || []
    const roleNames = positionList
      .map((position) => position.name)
      .filter((name) => !!name)
    roleOptions.value = Array.from(new Set([...BUILTIN_ROLE_OPTIONS, ...roleNames]))

    const userList: User[] = users?.list || users?.data?.list || []
    userOptions.value = userList.map((user) => ({
      id: user.id,
      label: user.realName || user.username,
    }))
  } catch {
    roleOptions.value = [...BUILTIN_ROLE_OPTIONS, '产品经理', '开发', '测试']
    userOptions.value = []
  }
}

function openNodeDialog(node?: WorkflowNodeDefinition) {
  editingNodeId.value = node?.nodeId || null
  if (node) {
    nodeForm.value = {
      ...createEmptyNode(),
      ...node,
      allowedRoles: normalizeStringArray(node.allowedRoles),
      allowedUsers: normalizeNumberArray(node.allowedUsers),
    }
  } else {
    nodeForm.value = createEmptyNode()
  }
  nodeDialogVisible.value = true
}

async function submitNode() {
  if (!nodeFormRef.value) {
    return
  }

  await nodeFormRef.value.validate()

  const name = nodeForm.value.name.trim()
  const duplicate = draftNodes.value.find(
    (node) => node.name.trim() === name && node.nodeId !== editingNodeId.value,
  )
  if (duplicate) {
    ElMessage.warning('节点名称不能重复')
    return
  }

  const payload: WorkflowNodeDefinition = {
    nodeId: editingNodeId.value || generateNodeId(),
    name,
    type: 'state',
    color: nodeForm.value.color || '#409EFF',
    isFinal: !!nodeForm.value.isFinal,
    sortOrder: nodeForm.value.sortOrder || 0,
    allowedRoles: normalizeStringArray(nodeForm.value.allowedRoles),
    allowedUsers: normalizeNumberArray(nodeForm.value.allowedUsers),
    editableFields: [],
    requiredFields: [],
    availableActions: [],
  }

  if (editingNodeId.value) {
    draftNodes.value = draftNodes.value.map((node) =>
      node.nodeId === editingNodeId.value ? payload : node,
    )
  } else {
    draftNodes.value = [...draftNodes.value, payload]
  }

  nodeDialogVisible.value = false
  if (activeTab.value === 'transitions') {
    await nextTick()
    renderGraph()
  }
}

async function deleteNode(nodeId: string) {
  const nodeName = getNodeName(nodeId)
  try {
    await ElMessageBox.confirm(`确定删除节点“${nodeName}”吗？相关流转也会一起删除。`, '确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  draftNodes.value = draftNodes.value.filter((node) => node.nodeId !== nodeId)
  draftEdges.value = draftEdges.value.filter((edge) => edge.source !== nodeId && edge.target !== nodeId)
  selectedSourceNodeId.value = selectedSourceNodeId.value === nodeId ? null : selectedSourceNodeId.value
  delete nodePositions.value[nodeId]
  nodePositions.value = { ...nodePositions.value }
  if (activeTab.value === 'transitions') {
    await nextTick()
    renderGraph()
  }
}

function openEdgeDialog(edge: WorkflowEdgeDefinition) {
  editingEdgeKey.value = getEdgeKey(edge.source, edge.target)
  edgeForm.value = {
    ...createEmptyEdge(),
    ...edge,
    allowedRoles: normalizeStringArray(edge.allowedRoles),
    requiredFields: normalizeStringArray(edge.requiredFields),
    conditions: typeof edge.conditions === 'string'
      ? edge.conditions
      : edge.conditions
        ? JSON.stringify(edge.conditions)
        : '',
  }
  edgeDialogVisible.value = true
}

function openCreateEdgeDialog(source: string, target: string) {
  openEdgeDialog({
    source,
    target,
    label: '',
    allowedRoles: [],
    requiredFields: [],
    conditions: '',
  })
}

async function submitEdge() {
  if (!edgeForm.value.source || !edgeForm.value.target) {
    ElMessage.warning('流转节点配置不完整')
    return
  }
  if (edgeForm.value.source === edgeForm.value.target) {
    ElMessage.warning('不能创建指向自身的流转')
    return
  }

  const payload: WorkflowEdgeDefinition = {
    source: edgeForm.value.source,
    target: edgeForm.value.target,
    label: edgeForm.value.label?.trim() || '',
    allowedRoles: normalizeStringArray(edgeForm.value.allowedRoles),
    requiredFields: normalizeStringArray(edgeForm.value.requiredFields),
    conditions: typeof edgeForm.value.conditions === 'string' ? edgeForm.value.conditions.trim() : '',
  }

  const edgeIndex = findEdgeIndex(payload.source, payload.target)
  if (edgeIndex >= 0) {
    draftEdges.value = draftEdges.value.map((edge, index) => (index === edgeIndex ? payload : edge))
  } else {
    draftEdges.value = [...draftEdges.value, payload]
  }

  edgeDialogVisible.value = false
  await nextTick()
  renderGraph()
}

async function deleteEdge(source: string, target: string) {
  const sourceName = getNodeName(source)
  const targetName = getNodeName(target)
  try {
    await ElMessageBox.confirm(`确定删除流转“${sourceName} -> ${targetName}”吗？`, '确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  draftEdges.value = draftEdges.value.filter((edge) => !(edge.source === source && edge.target === target))
  await nextTick()
  renderGraph()
}

async function deleteCurrentEditingEdge() {
  if (!edgeForm.value.source || !edgeForm.value.target) {
    return
  }
  edgeDialogVisible.value = false
  await deleteEdge(edgeForm.value.source, edgeForm.value.target)
}

function resetSelection() {
  selectedSourceNodeId.value = null
  renderGraph()
}

async function clearEdges() {
  try {
    await ElMessageBox.confirm('确定清空当前草稿中的所有流转规则吗？', '确认', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  draftEdges.value = []
  selectedSourceNodeId.value = null
  await nextTick()
  renderGraph()
}

async function saveAsVersion() {
  const versionName = newVersionName.value.trim() || `流程版本-${new Date().toISOString().slice(0, 19).replace('T', ' ')}`
  const errors = validateDraftDefinition()
  if (errors.length > 0) {
    ElMessage.error(errors[0])
    return
  }

  savingVersion.value = true
  try {
    const definition = JSON.stringify(buildDefinition(versionName))
    await createWorkflowVersion(projectId.value, { name: versionName, definition })
    ElMessage.success('流程版本已保存')
    newVersionName.value = ''
    await loadVersions()
  } catch {
    ElMessage.error('保存流程版本失败')
  } finally {
    savingVersion.value = false
  }
}

async function activateVersion(version: WorkflowVersion) {
  try {
    await ElMessageBox.confirm(
      `启用版本 V${version.version} 后，会用该版本重建当前项目的运行态节点和流转规则。是否继续？`,
      '启用确认',
      {
        type: 'warning',
        confirmButtonText: '启用',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  try {
    await activateWorkflowVersion(version.id, projectId.value)
    ElMessage.success('流程版本已启用')
    await reloadData()
    loadVersionToDraft(version, { silent: true })
  } catch {
    ElMessage.error('启用流程版本失败')
  }
}

function captureNodePositions() {
  if (!graphChart) {
    return
  }

  try {
    const seriesModel = (graphChart as any).getModel?.().getSeriesByIndex?.(0)
    const data = seriesModel?.getData?.()
    if (!data?.count) {
      return
    }

    const nextPositions: Record<string, NodePosition> = { ...nodePositions.value }
    for (let index = 0; index < data.count(); index += 1) {
      const id = data.get?.('id', index) || data.getId?.(index)
      const layout = data.getItemLayout?.(index)
      if (!id || !layout) {
        continue
      }
      if (!Number.isFinite(layout.x) || !Number.isFinite(layout.y)) {
        continue
      }
      nextPositions[String(id)] = { x: layout.x, y: layout.y }
    }
    nodePositions.value = nextPositions
  } catch {
  }
}

function renderGraph() {
  if (!graphRef.value || activeTab.value !== 'transitions') {
    return
  }

  if (!graphChart) {
    graphChart = echarts.init(graphRef.value)
    graphChart.on('click', (params: any) => {
      if (params?.dataType === 'node') {
        const nodeId = String(params.data?.id || '')
        if (!nodeId) {
          return
        }
        if (!selectedSourceNodeId.value) {
          selectedSourceNodeId.value = nodeId
          renderGraph()
          return
        }
        if (selectedSourceNodeId.value === nodeId) {
          selectedSourceNodeId.value = null
          renderGraph()
          return
        }

        const existingIndex = findEdgeIndex(selectedSourceNodeId.value, nodeId)
        if (existingIndex >= 0) {
          openEdgeDialog(draftEdges.value[existingIndex])
        } else {
          openCreateEdgeDialog(selectedSourceNodeId.value, nodeId)
        }
        selectedSourceNodeId.value = null
        renderGraph()
        return
      }

      if (params?.dataType === 'edge') {
        const source = String(params.data?.source || '')
        const target = String(params.data?.target || '')
        const edge = draftEdges.value.find((item) => item.source === source && item.target === target)
        if (edge) {
          openEdgeDialog(edge)
        }
      }
    })

    graphChart.on('dblclick', (params: any) => {
      if (params?.dataType !== 'node') {
        return
      }
      const nodeId = String(params.data?.id || '')
      const node = draftNodes.value.find((item) => item.nodeId === nodeId)
      if (node) {
        openNodeDialog(node)
      }
    })
  }

  captureNodePositions()

  const width = graphRef.value.clientWidth || 960
  const height = graphRef.value.clientHeight || 520
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.max(140, Math.min(width, height) / 2 - 90)
  const nodes = sortedNodes.value.map((node, index) => {
    const angle = (2 * Math.PI * index) / Math.max(sortedNodes.value.length, 1)
    const saved = nodePositions.value[node.nodeId]
    const x = saved?.x ?? centerX + radius * Math.cos(angle)
    const y = saved?.y ?? centerY + radius * Math.sin(angle)
    const selected = selectedSourceNodeId.value === node.nodeId
    return {
      id: node.nodeId,
      name: hasNodePermission(node) ? `${node.name}（受限）` : node.name,
      x,
      y,
      draggable: true,
      symbolSize: node.isFinal ? 72 : 62,
      itemStyle: {
        color: node.color || '#409EFF',
        borderColor: selected ? '#303133' : '#ffffff',
        borderWidth: selected ? 3 : 1,
      },
      label: {
        show: true,
        color: '#303133',
      },
    }
  })

  const edges = draftEdges.value.map((edge) => ({
    source: edge.source,
    target: edge.target,
    label: edge.label ? { show: true, formatter: edge.label, color: '#606266' } : undefined,
    lineStyle: {
      width: 2,
      color: normalizeStringArray(edge.allowedRoles).length > 0 ? '#E6A23C' : '#909399',
    },
  }))

  graphChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        if (params?.dataType === 'edge') {
          const edge = draftEdges.value.find(
            (item) => item.source === params.data?.source && item.target === params.data?.target,
          )
          const label = edge?.label ? ` / ${edge.label}` : ''
          return `${getNodeName(params.data?.source)} -> ${getNodeName(params.data?.target)}${label}`
        }
        return params?.data?.name || ''
      },
    },
    animation: false,
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        data: nodes,
        links: edges,
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: 10,
        emphasis: {
          focus: 'adjacency',
        },
      },
    ],
  })
}

watch(
  projectId,
  async () => {
    draftNodes.value = []
    draftEdges.value = []
    versions.value = []
    newVersionName.value = ''
    selectedSourceNodeId.value = null
    nodePositions.value = {}
    await reloadData()
  },
  { immediate: true },
)

watch(
  [draftNodes, draftEdges, activeTab, selectedSourceNodeId],
  async () => {
    if (activeTab.value !== 'transitions') {
      return
    }
    await nextTick()
    renderGraph()
  },
  { deep: true },
)

onMounted(() => {
  loadRoleAndUserOptions()
})

onBeforeUnmount(() => {
  graphChart?.dispose()
  graphChart = null
})
</script>

<style scoped lang="scss">
.workflow-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
  }

  .title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }

  .subtitle {
    margin-top: 6px;
    font-size: 13px;
    color: #606266;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .page-alert {
    margin-bottom: 16px;
  }

  .tab-content {
    padding: 16px 0;
  }

  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;
  }

  .toolbar-left,
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .transitions-toolbar {
    align-items: flex-start;
  }

  .workflow-graph {
    width: 100%;
    height: 520px;
    border: 1px solid #ebeef5;
    border-radius: 8px;
    background: linear-gradient(180deg, #fcfcfd 0%, #f7f8fa 100%);
  }

  .edge-table {
    margin-top: 20px;
  }

  .section-title {
    margin-bottom: 12px;
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }

  .color-preview {
    display: inline-block;
    width: 18px;
    height: 18px;
    border-radius: 4px;
    vertical-align: middle;
    border: 1px solid #dcdfe6;
  }

  .helper-text,
  .text-muted {
    font-size: 12px;
    color: #909399;
    line-height: 1.6;
  }

  .ml-2 {
    margin-left: 8px;
  }
}
</style>
