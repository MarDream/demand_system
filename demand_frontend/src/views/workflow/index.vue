<template>
  <div class="workflow-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">工作流配置</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 状态管理 -->
        <el-tab-pane label="状态管理" name="states">
          <div class="tab-content">
            <div class="toolbar">
              <el-button type="primary" @click="openStateDialog()">新增状态</el-button>
            </div>
            <el-table :data="states" border style="width: 100%">
              <el-table-column prop="name" label="状态名称" />
              <el-table-column prop="color" label="颜色" width="120">
                <template #default="{ row }">
                  <span class="color-preview" :style="{ backgroundColor: row.color }" />
                  <span class="ml-2">{{ row.color }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="isFinal" label="是否终态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.isFinal ? 'success' : 'info'">
                    {{ row.isFinal ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="80" />
              <el-table-column label="操作" width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openStateDialog(row)">编辑</el-button>
                  <el-button link type="primary" @click="openNodePermissionDialog(row.id)">权限</el-button>
                  <el-button link type="danger" @click="deleteState(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 流转规则 -->
        <el-tab-pane label="流转规则" name="transitions">
          <div class="tab-content">
            <div class="toolbar transitions-toolbar">
              <div class="left">
                <el-button @click="resetSelection">取消选中</el-button>
                <el-button type="danger" @click="clearTransitions" :disabled="transitions.length === 0">清空连接</el-button>
                <span class="text-muted">
                  先点击起始状态，再点击目标状态建立连接；点击连线可删除
                  <span v-if="selectedFromStateId">（当前起始：{{ getStateName(selectedFromStateId) }}）</span>
                  <span class="ml-2">双击节点可设置节点权限</span>
                </span>
              </div>
              <div class="right">
                <el-input v-model="newVersionName" placeholder="版本名称" style="width: 220px" />
                <el-button type="primary" @click="saveAsVersion" :disabled="states.length === 0">保存为新版本</el-button>
              </div>
            </div>

            <div ref="graphRef" class="workflow-graph" />
          </div>
        </el-tab-pane>

        <!-- 流程版本 -->
        <el-tab-pane label="流程版本" name="versions">
          <div class="tab-content">
            <el-table :data="versions" border style="width: 100%">
              <el-table-column prop="version" label="版本号" width="100" />
              <el-table-column prop="name" label="名称" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.isActive ? 'success' : 'info'">
                    {{ row.isActive ? '当前' : '历史' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180" />
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button v-if="!row.isActive" link type="primary" @click="activateVersion(row)">
                    启用
                  </el-button>
                  <span v-else class="text-muted">当前版本</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 状态编辑对话框 -->
    <el-dialog
      v-model="stateDialogVisible"
      :title="isEditing ? '编辑状态' : '新增状态'"
      width="500px"
    >
      <el-form :model="stateForm" :rules="stateRules" ref="stateFormRef" label-width="80px">
        <el-form-item label="状态名称" prop="name">
          <el-input v-model="stateForm.name" placeholder="请输入状态名称" />
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="stateForm.color" />
        </el-form-item>
        <el-form-item label="终态">
          <el-switch v-model="stateForm.isFinal" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="stateForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitState">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="nodePermissionDialogVisible"
      title="节点权限设置"
      width="560px"
    >
      <el-form :model="nodePermissionForm" label-width="100px">
        <el-form-item label="节点">
          <el-input :model-value="nodePermissionStateName" disabled />
        </el-form-item>
        <el-form-item label="允许角色">
          <el-select v-model="nodePermissionForm.allowedRoles" multiple filterable clearable placeholder="不选则不限制角色">
            <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许用户">
          <el-select v-model="nodePermissionForm.allowedUsers" multiple filterable clearable placeholder="不选则不限制用户">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodePermissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNodePermission">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import * as echarts from 'echarts'
import {
  getWorkflowStates,
  getWorkflowTransitions,
  getWorkflowVersions,
  activateWorkflowVersion,
  createWorkflowVersion,
  createWorkflowState,
  updateWorkflowState,
  deleteWorkflowState,
  createWorkflowTransition,
  deleteWorkflowTransition,
} from '@/api/modules/workflow'
import type { WorkflowState, WorkflowTransition, WorkflowVersion } from '@/types/workflow'
import * as userApi from '@/api/modules/user'
import type { User, Position } from '@/types/user'

const route = useRoute()
const projectId = computed(() => {
  const id = Number(route.query.projectId)
  return Number.isFinite(id) && id > 0 ? id : 1
})
const activeTab = ref('states')

// 状态管理
const states = ref<WorkflowState[]>([])
const stateDialogVisible = ref(false)
const isEditing = ref(false)
const stateFormRef = ref<FormInstance>()
const stateForm = ref<Partial<WorkflowState>>({
  name: '',
  color: '#409EFF',
  isFinal: false,
  sortOrder: 0,
})

const stateRules = {
  name: [{ required: true, message: '请输入状态名称', trigger: 'blur' }],
}

// 流转规则
const transitions = ref<WorkflowTransition[]>([])
const graphRef = ref<HTMLDivElement | null>(null)
let graphChart: echarts.ECharts | null = null
const selectedFromStateId = ref<number | null>(null)
const newVersionName = ref<string>('')
const nodePositions = ref<Record<string, { x: number; y: number }>>({})

type NodePermissionForm = {
  allowedRoles: string[]
  allowedUsers: number[]
}

const nodePermissionByStateId = ref<Record<string, NodePermissionForm>>({})
const nodePermissionDialogVisible = ref(false)
const nodePermissionStateId = ref<number | null>(null)
const nodePermissionForm = ref<NodePermissionForm>({
  allowedRoles: [],
  allowedUsers: [],
})

const roleOptions = ref<string[]>([])
const userOptions = ref<{ id: number; label: string }[]>([])

// 流程版本
const versions = ref<WorkflowVersion[]>([])

// 工具方法
const getStateName = (stateId: number) => {
  const state = states.value.find((s) => s.id === stateId)
  return state ? state.name : `状态${stateId}`
}

const nodePermissionStateName = computed(() => {
  if (nodePermissionStateId.value == null) return ''
  return getStateName(nodePermissionStateId.value)
})

const normalizeStringArray = (val: unknown): string[] => {
  if (Array.isArray(val)) return val.map((s) => String(s)).filter((s) => s.trim().length > 0)
  if (typeof val === 'string') {
    const trimmed = val.trim()
    if (!trimmed) return []
    return trimmed.split(',').map((s) => s.trim()).filter(Boolean)
  }
  return []
}

const normalizeNumberArray = (val: unknown): number[] => {
  if (Array.isArray(val)) return val.map((n) => Number(n)).filter((n) => Number.isFinite(n))
  if (typeof val === 'string') {
    const trimmed = val.trim()
    if (!trimmed) return []
    return trimmed.split(',').map((s) => Number(s.trim())).filter((n) => Number.isFinite(n))
  }
  return []
}

const hasNodePermission = (stateId: number) => {
  const v = nodePermissionByStateId.value[String(stateId)]
  return !!(v && (v.allowedRoles?.length || v.allowedUsers?.length))
}

const applyNodePermissionsFromActiveVersion = () => {
  const active = versions.value.find((v: any) => !!v.isActive)
  if (!active) return
  const defRaw = (active as any).definition
  let def: any = null
  try {
    def = typeof defRaw === 'string' ? JSON.parse(defRaw) : defRaw
  } catch {
    def = null
  }
  const nodes = def?.nodes
  if (!Array.isArray(nodes)) return

  const next: Record<string, NodePermissionForm> = { ...nodePermissionByStateId.value }
  for (const n of nodes) {
    const nodeId = n?.nodeId
    if (nodeId == null) continue
    const key = String(nodeId).trim()
    if (!key) continue
    const allowedRoles = normalizeStringArray(n?.allowedRoles)
    const allowedUsers = normalizeNumberArray(n?.allowedUsers)
    if (!allowedRoles.length && !allowedUsers.length) {
      delete next[key]
      continue
    }
    next[key] = { allowedRoles, allowedUsers }
  }
  nodePermissionByStateId.value = next
}

const renderGraph = () => {
  if (!graphRef.value) return
  if (!graphChart) {
    graphChart = echarts.init(graphRef.value)
    graphChart.on('click', (params: any) => {
      if (params?.dataType === 'node') {
        const stateId = Number(params.data?.id)
        if (!Number.isFinite(stateId)) return
        if (selectedFromStateId.value == null) {
          selectedFromStateId.value = stateId
          renderGraph()
          return
        }
        if (selectedFromStateId.value === stateId) return
        addTransition(selectedFromStateId.value, stateId)
        selectedFromStateId.value = null
        renderGraph()
        return
      }
      if (params?.dataType === 'edge') {
        const source = Number(params.data?.source)
        const target = Number(params.data?.target)
        if (!Number.isFinite(source) || !Number.isFinite(target)) return
        ElMessageBox.confirm(`删除流转：${getStateName(source)} -> ${getStateName(target)}？`, '确认', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        })
          .then(() => void deleteTransition(source, target))
          .catch(() => {})
      }
    })
    graphChart.on('dblclick', (params: any) => {
      if (params?.dataType !== 'node') return
      const stateId = Number(params.data?.id)
      if (!Number.isFinite(stateId)) return
      openNodePermissionDialog(stateId)
    })
  }

  try {
    const seriesModel = (graphChart as any).getModel?.().getSeriesByIndex?.(0)
    const data = seriesModel?.getData?.()
    if (data?.count) {
      const nextPositions: Record<string, { x: number; y: number }> = { ...nodePositions.value }
      for (let i = 0; i < data.count(); i += 1) {
        const id = data.get?.('id', i) || data.getId?.(i)
        const layout = data.getItemLayout?.(i)
        if (!id || !layout) continue
        if (!Number.isFinite(layout.x) || !Number.isFinite(layout.y)) continue
        nextPositions[String(id)] = { x: layout.x, y: layout.y }
      }
      nodePositions.value = nextPositions
    }
  } catch {
  }

  const width = graphRef.value.clientWidth || 800
  const height = graphRef.value.clientHeight || 480
  const sortedStates = [...states.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width, height) / 2 - 80

  const nodes = sortedStates.map((s, idx) => {
    const angle = (2 * Math.PI * idx) / Math.max(sortedStates.length, 1)
    const baseX = centerX + radius * Math.cos(angle)
    const baseY = centerY + radius * Math.sin(angle)
    const saved = nodePositions.value[String(s.id)]
    const x = saved?.x ?? baseX
    const y = saved?.y ?? baseY
    const isSelected = selectedFromStateId.value === s.id
    return {
      id: String(s.id),
      name: hasNodePermission(s.id) ? `${s.name}（受限）` : s.name,
      x,
      y,
      draggable: true,
      itemStyle: { color: s.color || '#409EFF', borderColor: isSelected ? '#303133' : '#fff', borderWidth: isSelected ? 3 : 1 },
      label: { show: true, color: '#303133' },
      symbolSize: 58,
    }
  })

  const edges = transitions.value.map((t) => ({
    source: String(t.fromStateId),
    target: String(t.toStateId),
    lineStyle: { width: 2, color: '#909399' },
  }))

  graphChart.setOption({
    tooltip: { formatter: (p: any) => (p?.dataType === 'edge' ? `${getStateName(Number(p.data.source))} -> ${getStateName(Number(p.data.target))}` : p?.data?.name) },
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
        emphasis: { focus: 'adjacency' },
      },
    ],
  })
}

const addTransition = (fromStateId: number, toStateId: number) => {
  const exists = transitions.value.some((t) => t.fromStateId === fromStateId && t.toStateId === toStateId)
  if (exists) return
  void createTransition(fromStateId, toStateId)
}

const createTransition = async (fromStateId: number, toStateId: number) => {
  try {
    const created = (await createWorkflowTransition(projectId.value, {
      fromStateId,
      toStateId,
      allowedRoles: '',
      requiredFields: '',
      conditions: null,
    })) as unknown as WorkflowTransition
    transitions.value = [...transitions.value, { ...created, allowedRoles: [], requiredFields: [] } as any]
    renderGraph()
  } catch {
    transitions.value = [
      ...transitions.value,
      {
        id: 0,
        projectId: projectId.value,
        fromStateId,
        toStateId,
        allowedRoles: [],
        requiredFields: [],
      } as WorkflowTransition,
    ]
    renderGraph()
  }
}

const deleteTransition = async (fromStateId: number, toStateId: number) => {
  const target = transitions.value.find((t) => t.fromStateId === fromStateId && t.toStateId === toStateId)
  transitions.value = transitions.value.filter((t) => !(t.fromStateId === fromStateId && t.toStateId === toStateId))
  renderGraph()
  if (!target?.id) return
  try {
    await deleteWorkflowTransition(target.id)
  } catch {
  }
}

// 加载数据
const loadStates = async () => {
  try {
    const list = (await getWorkflowStates(projectId.value)) as unknown as any[]
    states.value = (list || []).map((s: any) => ({
      ...s,
      isFinal: !!s.isFinal,
    })) as WorkflowState[]
  } catch {
    ElMessage.warning('状态数据加载失败，使用模拟数据')
    states.value = [
      { id: 1, projectId: 1, name: '待处理', color: '#909399', isFinal: false, sortOrder: 1 },
      { id: 2, projectId: 1, name: '进行中', color: '#409EFF', isFinal: false, sortOrder: 2 },
      { id: 3, projectId: 1, name: '已完成', color: '#67C23A', isFinal: true, sortOrder: 3 },
      { id: 4, projectId: 1, name: '已关闭', color: '#F56C6C', isFinal: true, sortOrder: 4 },
    ]
  }
}

const loadTransitions = async () => {
  try {
    const list = (await getWorkflowTransitions(projectId.value)) as unknown as WorkflowTransition[]
    transitions.value = (list || []).map((t: any) => ({
      ...t,
      allowedRoles: normalizeStringArray(t.allowedRoles),
      requiredFields: normalizeStringArray(t.requiredFields),
    }))
  } catch {
    transitions.value = [
      { id: 1, projectId: 1, fromStateId: 1, toStateId: 2, allowedRoles: ['产品经理'], requiredFields: ['title'] },
      { id: 2, projectId: 1, fromStateId: 2, toStateId: 3, allowedRoles: ['开发'], requiredFields: ['description'] },
      { id: 3, projectId: 1, fromStateId: 2, toStateId: 4, allowedRoles: ['产品经理'], requiredFields: [] },
    ]
  }
}

const loadVersions = async () => {
  try {
    const list = (await getWorkflowVersions(projectId.value)) as unknown as any[]
    versions.value = (list || []).map((v: any) => ({
      ...v,
      isActive: !!v.isActive,
    })) as WorkflowVersion[]
    applyNodePermissionsFromActiveVersion()
  } catch {
    versions.value = [
      { id: 1, projectId: 1, version: 1, name: '初始版本', definition: {}, isActive: false, creatorId: 1, createdAt: '2024-01-15 10:00:00' },
      { id: 2, projectId: 1, version: 2, name: '当前版本', definition: {}, isActive: true, creatorId: 1, createdAt: '2024-03-20 14:30:00' },
    ]
    applyNodePermissionsFromActiveVersion()
  }
}

const loadRoleAndUserOptions = async () => {
  try {
    const [positions, users] = await Promise.all([
      userApi.getPositionList() as any,
      userApi.getUserList({ pageNum: 1, pageSize: 1000 }) as any,
    ])

    const posList: Position[] = Array.isArray(positions) ? positions : positions?.data || positions || []
    roleOptions.value = (posList || []).map((p) => p.name).filter((v) => !!v)

    const userList: User[] = users?.list || users?.data?.list || []
    userOptions.value = (userList || []).map((u) => ({ id: u.id, label: u.realName || u.username }))
  } catch {
    roleOptions.value = ['产品经理', '开发', '测试']
    userOptions.value = []
  }
}

// 状态操作
const openStateDialog = (row?: WorkflowState) => {
  isEditing.value = !!row
  if (row) {
    stateForm.value = { ...row }
  } else {
    stateForm.value = { name: '', color: '#409EFF', isFinal: false, sortOrder: states.value.length + 1 }
  }
  stateDialogVisible.value = true
}

const submitState = async () => {
  if (!stateFormRef.value) return
  await stateFormRef.value.validate()
  try {
    if (isEditing.value && stateForm.value.id) {
      await updateWorkflowState(stateForm.value.id, {
        name: stateForm.value.name,
        color: stateForm.value.color,
        isFinal: stateForm.value.isFinal ? 1 : 0,
        sortOrder: stateForm.value.sortOrder,
      })
      ElMessage.success('状态更新成功')
    } else {
      await createWorkflowState(projectId.value, {
        name: stateForm.value.name,
        color: stateForm.value.color,
        isFinal: stateForm.value.isFinal ? 1 : 0,
        sortOrder: stateForm.value.sortOrder,
      })
      ElMessage.success('状态创建成功')
    }
    stateDialogVisible.value = false
    await loadStates()
    await loadTransitions()
    if (activeTab.value === 'transitions') {
      await nextTick()
      renderGraph()
    }
  } catch {
    ElMessage.error('保存失败')
  }
}

const deleteState = async (row: WorkflowState) => {
  try {
    await ElMessageBox.confirm(`确定删除状态“${row.name}”吗？该状态相关的流转也会被删除。`, '确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteWorkflowState(row.id)
    ElMessage.success('删除成功')
    delete nodePermissionByStateId.value[String(row.id)]
    nodePermissionByStateId.value = { ...nodePermissionByStateId.value }
    await loadStates()
    await loadTransitions()
    if (activeTab.value === 'transitions') {
      await nextTick()
      renderGraph()
    }
  } catch {
    ElMessage.error('删除失败')
  }
}

const openNodePermissionDialog = (stateId: number) => {
  nodePermissionStateId.value = stateId
  const existing = nodePermissionByStateId.value[String(stateId)]
  nodePermissionForm.value = {
    allowedRoles: existing?.allowedRoles ? [...existing.allowedRoles] : [],
    allowedUsers: existing?.allowedUsers ? [...existing.allowedUsers] : [],
  }
  nodePermissionDialogVisible.value = true
}

const saveNodePermission = () => {
  if (nodePermissionStateId.value == null) return
  nodePermissionByStateId.value[String(nodePermissionStateId.value)] = {
    allowedRoles: [...(nodePermissionForm.value.allowedRoles || [])],
    allowedUsers: [...(nodePermissionForm.value.allowedUsers || [])],
  }
  nodePermissionByStateId.value = { ...nodePermissionByStateId.value }
  nodePermissionDialogVisible.value = false
  if (activeTab.value === 'transitions') {
    renderGraph()
  }
}

// 流转规则操作
const editTransition = (row: WorkflowTransition) => {
  ElMessage.info(`编辑流转规则: ${getStateName(row.fromStateId)} -> ${getStateName(row.toStateId)}`)
}

const resetSelection = () => {
  selectedFromStateId.value = null
  renderGraph()
}

const clearTransitions = async () => {
  try {
    await ElMessageBox.confirm('确定清空所有连接吗？', '确认', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  transitions.value = []
  selectedFromStateId.value = null
  renderGraph()
}

const saveAsVersion = async () => {
  const name = newVersionName.value.trim() || `版本${Date.now()}`
  const nodes = states.value.map((s) => {
    const perm = nodePermissionByStateId.value[String(s.id)]
    return {
      nodeId: String(s.id),
      name: s.name,
      type: 'state',
      allowedRoles: perm?.allowedRoles || [],
      allowedUsers: perm?.allowedUsers || [],
      editableFields: [],
      requiredFields: [],
      availableActions: [],
    }
  })
  const edges = transitions.value.map((t) => ({
    source: String(t.fromStateId),
    target: String(t.toStateId),
    label: '',
  }))
  const definition = JSON.stringify({ id: null, name, nodes, edges })

  try {
    await createWorkflowVersion(projectId.value, { name, definition })
    ElMessage.success('已保存为新版本')
    newVersionName.value = ''
    await loadVersions()
  } catch {
    ElMessage.success('已保存为新版本（模拟）')
    newVersionName.value = ''
    await loadVersions()
  }
}

// 版本操作
const activateVersion = async (row: WorkflowVersion) => {
  try {
    await activateWorkflowVersion(row.id, projectId.value)
    ElMessage.success('版本已启用')
    await loadVersions()
  } catch {
    ElMessage.success('版本已启用（模拟）')
    versions.value.forEach((v) => {
      v.isActive = v.id === row.id
    })
    applyNodePermissionsFromActiveVersion()
  }
}

watch(
  projectId,
  async () => {
    selectedFromStateId.value = null
    nodePositions.value = {}
    nodePermissionByStateId.value = {}
    await loadStates()
    await loadTransitions()
    await loadVersions()
    if (activeTab.value === 'transitions') {
      await nextTick()
      renderGraph()
    }
  },
  { immediate: true },
)

onMounted(() => {
  loadRoleAndUserOptions()
})

watch(
  [states, transitions, activeTab, selectedFromStateId],
  async () => {
    if (activeTab.value !== 'transitions') return
    await nextTick()
    renderGraph()
  },
  { deep: true },
)

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

    .title {
      font-size: 18px;
      font-weight: 600;
    }
  }

  .tab-content {
    padding: 16px 0;
  }

  .toolbar {
    margin-bottom: 16px;
  }

  .transitions-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .workflow-graph {
    width: 100%;
    height: 520px;
    border: 1px solid #ebeef5;
    border-radius: 6px;
  }

  .color-preview {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 4px;
    vertical-align: middle;
    border: 1px solid #ddd;
  }

  .ml-2 {
    margin-left: 8px;
  }

  .mr-1 {
    margin-right: 4px;
  }

  .text-muted {
    color: #909399;
    font-size: 12px;
  }
}
</style>
