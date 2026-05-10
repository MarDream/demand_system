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
              <h2>{{ isViewMode ? '查看工作流' : isEditMode ? '编辑工作流' : '新建工作流' }}</h2>
              <span v-if="currentVersion" class="version-tag">
                V{{ currentVersion.version }} - {{ currentVersion.name }}
              </span>
            </div>
          </div>
          <div class="header-right">
            <el-button v-if="!isViewMode" @click="handleSave" :loading="saving">
              <el-icon><DocumentCopy /></el-icon>
              保存草稿
            </el-button>
            <el-button v-if="!isViewMode" type="primary" @click="handleSubmit" :loading="submitting">
              <el-icon><Check /></el-icon>
              提交审核
            </el-button>
          </div>
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
              :class="node.type"
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
            <el-button @click="handleZoomIn" style="width: 100%">
              <el-icon><ZoomIn /></el-icon>
              放大
            </el-button>
            <el-button @click="handleZoomOut" style="width: 100%; margin-top: 8px">
              <el-icon><ZoomOut /></el-icon>
              缩小
            </el-button>
            <el-button @click="handleResetZoom" style="width: 100%; margin-top: 8px">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
            <el-button @click="handleClearCanvas" type="danger" style="width: 100%; margin-top: 8px">
              <el-icon><Delete /></el-icon>
              清空画布
            </el-button>
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
          size="400px"
          :before-close="handleDrawerClose"
        >
          <div v-if="selectedNode" class="node-config-panel">
            <el-form :model="nodeForm" label-width="100px" label-position="top">
              <el-form-item label="节点名称">
                <el-input v-model="nodeForm.nodeName" placeholder="请输入节点名称" />
              </el-form-item>

              <el-form-item label="节点类型">
                <el-tag>{{ getNodeTypeLabel(nodeForm.nodeType) }}</el-tag>
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

              <!-- 审批节点和抄送节点的配置 -->
              <template v-if="nodeForm.nodeType === 'approval' || nodeForm.nodeType === 'cc'">
                <el-form-item label="处理人类型">
                  <el-select v-model="nodeForm.assigneeType" placeholder="请选择处理人类型">
                    <el-option label="指定用户" value="SPECIFIED_USER" />
                    <el-option label="指定角色" value="SPECIFIED_ROLE" />
                    <el-option label="指定岗位" value="SPECIFIED_POSITION" />
                    <el-option label="发起人" value="INITIATOR" />
                    <el-option label="上级领导" value="SUPERIOR" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_ROLE'" label="指定角色">
                  <el-select v-model="nodeForm.assigneeRoleId" placeholder="请选择角色">
                    <el-option label="管理员" :value="1" />
                    <el-option label="产品经理" :value="2" />
                    <el-option label="开发" :value="3" />
                    <el-option label="测试" :value="4" />
                  </el-select>
                </el-form-item>

                <el-form-item v-if="nodeForm.assigneeType === 'SPECIFIED_USER'" label="指定用户">
                  <el-select v-model="nodeForm.assigneeUserIds" multiple placeholder="请选择用户">
                    <el-option label="用户1" :value="1" />
                    <el-option label="用户2" :value="2" />
                    <el-option label="用户3" :value="3" />
                  </el-select>
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
              </template>

              <!-- 条件节点配置 -->
              <template v-if="nodeForm.nodeType === 'condition'">
                <el-form-item label="条件说明">
                  <el-input
                    v-model="nodeForm.properties!.conditionDesc"
                    type="textarea"
                    :rows="3"
                    placeholder="请描述分支条件"
                  />
                </el-form-item>
              </template>

              <el-form-item>
                <el-button type="primary" @click="handleSaveNodeConfig">保存配置</el-button>
                <el-button @click="handleDeleteNode" type="danger">删除节点</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div v-else-if="selectedEdge" class="edge-config-panel">
            <el-form :model="edgeForm" label-width="100px" label-position="top">
              <el-form-item label="连线标签">
                <el-input v-model="edgeForm.label" placeholder="请输入连线标签（可选）" />
              </el-form-item>

              <el-form-item label="条件表达式">
                <el-input
                  v-model="edgeForm.conditionExpr"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：priority == 'HIGH'"
                />
              </el-form-item>

              <el-form-item>
                <el-button type="primary" @click="handleSaveEdgeConfig">保存配置</el-button>
                <el-button @click="handleDeleteEdge" type="danger">删除连线</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-drawer>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  DocumentCopy,
  Check,
  ZoomIn,
  ZoomOut,
  Refresh,
  Delete
} from '@element-plus/icons-vue'
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import { registerCustomNodes } from './logicflow-config'
import {
  getWorkflowConfig,
  saveWorkflowConfig,
  submitForApproval,
  getVersionConfig
} from '@/api/modules/workflow-visual'
import { nodeStatusApi, type NodeStatus } from '@/api/modules/workflow-engine'
import type {
  WorkflowVersionDTO,
  WorkflowNodeDTO,
  WorkflowEdgeDTO,
  WorkflowConfigDTO
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

const drawerVisible = ref(false)
const drawerTitle = ref('')
const selectedNode = ref<any>(null)
const selectedEdge = ref<any>(null)
const nodeStatusOptions = ref<NodeStatus[]>([])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始', icon: '▶' },
  { type: 'approval', label: '审批', icon: '✓' },
  { type: 'cc', label: '抄送', icon: '📧' },
  { type: 'condition', label: '条件', icon: '◆' },
  { type: 'end', label: '结束', icon: '■' }
]

// 节点表单
const nodeForm = reactive<Partial<WorkflowNodeDTO> & { nodeStatusCode?: string }>({
  nodeId: '',
  nodeType: 'approval',
  nodeName: '',
  positionX: 0,
  positionY: 0,
  assigneeType: undefined,
  assigneeRoleId: undefined,
  assigneeUserIds: [],
  timeoutHours: undefined,
  timeoutAction: undefined,
  nodeStatusCode: undefined,
  properties: {}
})

// 边表单
const edgeForm = reactive({
  label: '',
  conditionExpr: ''
})

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

// 处理节点点击
const handleNodeClick = (data: any) => {
  selectedNode.value = data
  selectedEdge.value = null
  drawerTitle.value = '节点配置'
  drawerVisible.value = true

  // 填充表单
  Object.assign(nodeForm, {
    nodeId: data.id,
    nodeType: data.type,
    nodeName: data.text?.value || '',
    positionX: data.x,
    positionY: data.y,
    assigneeType: data.properties?.assigneeType,
    assigneeRoleId: data.properties?.assigneeRoleId,
    assigneeUserIds: data.properties?.assigneeUserIds || [],
    timeoutHours: data.properties?.timeoutHours,
    timeoutAction: data.properties?.timeoutAction,
    nodeStatusCode: data.properties?.nodeStatusCode,
    properties: data.properties || {}
  })
}

// 处理边点击
const handleEdgeClick = (data: any) => {
  selectedEdge.value = data
  selectedNode.value = null
  drawerTitle.value = '连线配置'
  drawerVisible.value = true

  // 填充表单
  edgeForm.label = data.text?.value || ''
  edgeForm.conditionExpr = data.properties?.condition?.expr || ''
}

// 保存节点配置
const handleSaveNodeConfig = () => {
  if (!lf || !selectedNode.value) return

  const nodeData = {
    id: nodeForm.nodeId,
    type: nodeForm.nodeType,
    x: nodeForm.positionX,
    y: nodeForm.positionY,
    text: nodeForm.nodeName,
    properties: {
      nodeId: nodeForm.nodeId,
      nodeType: nodeForm.nodeType,
      nodeName: nodeForm.nodeName,
      assigneeType: nodeForm.assigneeType,
      assigneeRoleId: nodeForm.assigneeRoleId,
      assigneeUserIds: nodeForm.assigneeUserIds,
      timeoutHours: nodeForm.timeoutHours,
      timeoutAction: nodeForm.timeoutAction,
      nodeStatusCode: nodeForm.nodeStatusCode,
      ...nodeForm.properties
    }
  }

  lf.setProperties(nodeForm.nodeId!, nodeData.properties)
  lf.updateText(nodeForm.nodeId!, nodeForm.nodeName || '')

  ElMessage.success('节点配置已保存')
  drawerVisible.value = false
}

// 保存边配置
const handleSaveEdgeConfig = () => {
  if (!lf || !selectedEdge.value) return

  const edgeProperties = {
    label: edgeForm.label,
    condition: {
      expr: edgeForm.conditionExpr
    }
  }

  lf.setProperties(selectedEdge.value.id, edgeProperties)
  if (edgeForm.label) {
    lf.updateText(selectedEdge.value.id, edgeForm.label)
  }

  ElMessage.success('连线配置已保存')
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

// 清空画布
const handleClearCanvas = async () => {
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

  saving.value = true
  try {
    const graphData = lf.getGraphData() as any

    // 转换为后端需要的格式
    const config: WorkflowConfigDTO = {
      nodes: graphData.nodes.map((node: any) => ({
        nodeId: node.id!,
        nodeType: node.type as any,
        nodeName: node.text?.value || '',
        positionX: node.x!,
        positionY: node.y!,
        assigneeType: node.properties?.assigneeType,
        assigneeRoleId: node.properties?.assigneeRoleId,
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

    const projectId = Number(route.query.projectId || route.params.projectId || 1)
    await saveWorkflowConfig(projectId, config)

    ElMessage.success('保存成功')
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 提交审核
const handleSubmit = async () => {
  if (!lf) return

  // 先保存
  await handleSave()

  submitting.value = true
  try {
    const projectId = Number(route.query.projectId || route.params.projectId || 1)
    await submitForApproval(projectId)

    ElMessage.success('提交审核成功')
    router.push('/system/workflow-config')
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败')
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

// 返回
const goBack = () => {
  router.push('/system/workflow-config')
}

// 加载工作流配置
const loadWorkflowConfig = async () => {
  const versionId = route.query.versionId
  const mode = route.query.mode

  isViewMode.value = mode === 'view'
  isEditMode.value = mode === 'edit'

  if (versionId) {
    try {
      const version = await getVersionConfig(Number(versionId))
      if (version) {
        currentVersion.value = version

        // 渲染配置
        if (lf && version.config) {
          const graphData = {
            nodes: version.config.nodes.map(node => ({
              id: node.nodeId,
              type: node.nodeType,
              x: node.positionX,
              y: node.positionY,
              text: node.nodeName,
              properties: node
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
      console.error('加载配置失败:', error)
      ElMessage.error('加载配置失败')
    }
  }
}

const loadNodeStatuses = async () => {
  try {
    const result = await nodeStatusApi.list() as any
    nodeStatusOptions.value = Array.isArray(result) ? result : (result?.data || [])
  } catch (error) {
    console.error('加载节点状态失败:', error)
    nodeStatusOptions.value = []
  }
}

onMounted(() => {
  initLogicFlow()
  loadNodeStatuses()
  loadWorkflowConfig()
})

onBeforeUnmount(() => {
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

        .version-tag {
          font-size: 14px;
          color: #909399;
          margin-left: 8px;
        }
      }
    }

    .header-right {
      display: flex;
      gap: 12px;
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
        color: #303133;
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
            border-color: #409eff;
            background: #ecf5ff;
          }

          .node-icon {
            font-size: 18px;
          }

          .node-label {
            font-size: 14px;
          }

          &.start {
            border-color: #67c23a;
            .node-icon { color: #67c23a; }
          }

          &.approval {
            border-color: #409eff;
            .node-icon { color: #409eff; }
          }

          &.cc {
            border-color: #e6a23c;
            .node-icon { color: #e6a23c; }
          }

          &.condition {
            border-color: #f56c6c;
            .node-icon { color: #f56c6c; }
          }

          &.end {
            border-color: #909399;
            .node-icon { color: #909399; }
          }
        }
      }

      .toolbar-section {
        margin-top: 24px;
      }

      .help-section {
        padding: 12px;
        border-radius: 6px;
        background: #f5f7fa;

        ol {
          margin: 0;
          padding-left: 18px;
          color: #606266;
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
}
</style>
