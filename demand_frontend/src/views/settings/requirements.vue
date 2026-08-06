<template>
  <div class="config-container">
    <div class="config-header">
      <h2>需求基本配置</h2>
      <p class="config-desc">管理系统中需求类型和优先级的配置</p>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="需求类型" name="types">
        <div class="tab-content">
          <Toolbar class="tab-header">
            <template #right>
              <el-tooltip content="列表字段设置">
                <el-button link :icon="Setting" @click="openTypeColumnConfig" />
              </el-tooltip>
              <AppButton type="primary" permission="button:requirement-config:create" @click="openTypeDialog()">
                <el-icon><Plus /></el-icon>
                新增类型
              </AppButton>
            </template>
          </Toolbar>

          <el-table ref="typeTableRef" :data="types" border style="width: 100%" row-key="id">
            <el-table-column v-if="isTypeColumnVisible('drag')" width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="code" label="编码" min-width="100" />
            <el-table-column prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column prop="enabled" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled !== false"
                  :loading="row._enabledLoading"
                  @change="(val: boolean) => toggleTypeEnabled(row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column label="绑定工作流" min-width="180">
              <template #default="{ row }">
                <template v-if="row.workflowVersionId">
                  <span v-for="v in activeWorkflowVersions.filter(wv => wv.id === row.workflowVersionId)" :key="v.id">
                    <el-tag size="small" type="success">{{ v.workflowDefinitionName || v.name }}</el-tag>
                    <span v-if="v.workflowDefinitionName" style="margin-left: 4px; color: var(--el-text-color-secondary); font-size: 12px;">v{{ v.version }}</span>
                  </span>
                </template>
                <span v-else style="color: var(--el-text-color-placeholder)">未绑定</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openTypeDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="success" @click="goToTemplateDesign(row)"><el-icon><Document /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deleteType(row.id!)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="优先级" name="priorities">
        <div class="tab-content">
          <Toolbar class="tab-header">
            <template #right>
              <el-tooltip content="列表字段设置">
                <el-button link :icon="Setting" @click="openPriorityColumnConfig" />
              </el-tooltip>
              <AppButton type="primary" permission="button:requirement-config:create" @click="openPriorityDialog()">
                <el-icon><Plus /></el-icon>
                新增优先级
              </AppButton>
            </template>
          </Toolbar>

          <el-table ref="priorityTableRef" :data="priorities" border style="width: 100%" row-key="id">
            <el-table-column v-if="isPriorityColumnVisible('drag')" width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column v-if="isPriorityColumnVisible('name')" prop="name" label="名称" min-width="120" />
            <el-table-column v-if="isPriorityColumnVisible('code')" prop="code" label="编码" min-width="100" />
            <el-table-column v-if="isPriorityColumnVisible('level')" prop="level" label="级别" width="80" align="center" />
            <el-table-column v-if="isPriorityColumnVisible('color')" prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isPriorityColumnVisible('sortOrder')" prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column v-if="isPriorityColumnVisible('isDefault')" prop="isDefault" label="默认" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.isDefault"
                  :loading="row._defaultLoading"
                  @change="(val: boolean) => togglePriorityDefault(row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="isPriorityColumnVisible('operations')" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openPriorityDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deletePriority(row.id!)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="节点状态" name="nodeStatuses">
        <div class="tab-content">
          <Toolbar class="tab-header">
            <template #right>
              <el-tooltip content="列表字段设置">
                <el-button link :icon="Setting" @click="openNodeStatusColumnConfig" />
              </el-tooltip>
              <AppButton type="primary" permission="button:requirement-config:create" @click="openNodeStatusDialog()">
                <el-icon><Plus /></el-icon>
                新增节点状态
              </AppButton>
            </template>
          </Toolbar>

          <el-table ref="nodeStatusTableRef" :data="nodeStatuses" border style="width: 100%" row-key="id">
            <el-table-column v-if="isNodeStatusColumnVisible('drag')" width="60" align="center">
              <template #default>
                <el-icon class="drag-handle" :size="18">
                  <Operation />
                </el-icon>
              </template>
            </el-table-column>
            <el-table-column v-if="isNodeStatusColumnVisible('name')" prop="name" label="状态名称" min-width="120" />
            <el-table-column v-if="isNodeStatusColumnVisible('code')" prop="code" label="编码" min-width="150" />
            <el-table-column v-if="isNodeStatusColumnVisible('color')" prop="color" label="颜色" min-width="100">
              <template #default="{ row }">
                <div class="color-cell">
                  <span class="color-dot" :style="{ backgroundColor: row.color }"></span>
                  <span>{{ row.color }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-if="isNodeStatusColumnVisible('flags')" label="类型标记" min-width="160">
              <template #default="{ row }">
                <el-tag v-if="row.isStart" type="success" size="small" style="margin-right:4px">开始</el-tag>
                <el-tag v-if="row.isEnd" type="info" size="small" style="margin-right:4px">结束</el-tag>
                <el-tag v-if="row.isCancel" type="danger" size="small">取消</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="isNodeStatusColumnVisible('sortOrder')" prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column v-if="isNodeStatusColumnVisible('operations')" label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <AppButton link type="primary" permission="button:requirement-config:update" @click="openNodeStatusDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
                <AppButton link type="danger" permission="button:requirement-config:delete" @click="deleteNodeStatus(row.id)"><el-icon><Delete /></el-icon></AppButton>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="需求模板" name="templates">
        <div class="tab-content">
          <Toolbar class="tab-header">
            <template #right>
              <el-tooltip content="列表字段设置">
                <el-button link :icon="Setting" @click="openTemplateColumnConfig" />
              </el-tooltip>
              <AppButton type="primary" permission="button:requirement-template:create" @click="openTemplateCreate">
                <el-icon><Plus /></el-icon>
                新建模板
              </AppButton>
            </template>
          </Toolbar>
          <RequirementTemplateManager ref="templateManagerRef" :preselected-type-code="selectedTypeCodeForTemplate" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 类型对话框 -->
    <el-dialog v-model="typeDialogVisible" :title="editingType ? '编辑需求类型' : '新增需求类型'" width="500px" class="settings-form-dialog" @close="resetTypeForm">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="请输入类型名称" @input="handleTypeNameInput" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input
              v-model="typeForm.code"
              placeholder="如: FEATURE"
              :disabled="!!editingType"
              style="flex: 1"
              @input="typeCodeManuallyEdited = true"
            />
            <el-tooltip
              v-if="!editingType"
              :content="typeCodeAiGenerating ? 'AI 正在生成编码...' : 'AI 自动生成编码'"
              placement="top"
            >
              <el-button
                :loading="typeCodeAiGenerating"
                :disabled="!typeForm.name.trim() || !!editingType"
                @click="handleTypeAiGenerateCode"
              >
                <el-icon v-if="!typeCodeAiGenerating"><MagicStick /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="typeForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="typeForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="默认">
          <el-switch v-model="typeForm.isDefault" />
        </el-form-item>
        <el-form-item label="绑定工作流">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-select
              v-model="typeForm.workflowDefinitionId"
              placeholder="请选择工作流"
              clearable
              style="flex: 1"
              @change="handleDefinitionChange"
            >
              <el-option
                v-for="d in workflowDefinitions"
                :key="d.id"
                :label="d.name"
                :value="d.id"
              />
            </el-select>
            <el-select
              v-model="typeForm.workflowVersionId"
              :placeholder="typeForm.workflowDefinitionId ? '请选择版本号' : '请先选择工作流'"
              :disabled="!typeForm.workflowDefinitionId"
              clearable
              style="flex: 1"
            >
              <el-option
                v-for="v in filteredVersionOptions"
                :key="v.id"
                :label="v.version"
                :value="v.id"
              />
            </el-select>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button v-permission="'button:requirement-config:create'" type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 优先级对话框 -->
    <el-dialog v-model="priorityDialogVisible" :title="editingPriority ? '编辑优先级' : '新增优先级'" width="500px" class="settings-form-dialog">
      <el-form ref="priorityFormRef" :model="priorityForm" :rules="priorityRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="priorityForm.name" placeholder="如: P0-紧急" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <el-input v-model="priorityForm.code" placeholder="如: P0" />
        </el-form-item>
        <el-form-item label="级别" prop="level">
          <el-input-number v-model="priorityForm.level" :min="0" />
          <span class="form-tip">数字越小优先级越高</span>
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <el-color-picker v-model="priorityForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="priorityForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="默认">
          <el-switch v-model="priorityForm.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priorityDialogVisible = false">取消</el-button>
        <el-button v-permission="'button:requirement-config:create'" type="primary" @click="savePriority">保存</el-button>
      </template>
    </el-dialog>

    <!-- 节点状态对话框 -->
    <el-dialog v-model="nodeStatusDialogVisible" :title="editingNodeStatus ? '编辑节点状态' : '新增节点状态'" width="500px" class="settings-form-dialog">
      <el-form ref="nodeStatusFormRef" :model="nodeStatusForm" :rules="nodeStatusRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="nodeStatusForm.name" placeholder="如: 待评审" @input="handleNodeStatusNameInput" />
        </el-form-item>
        <el-form-item label="编码" prop="code">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input
              v-model="nodeStatusForm.code"
              placeholder="如: PENDING_REVIEW"
              style="flex: 1"
              @input="nodeStatusCodeManuallyEdited = true"
            />
            <el-tooltip
              v-if="!editingNodeStatus"
              :content="nodeStatusCodeAiGenerating ? 'AI 正在生成编码...' : 'AI 自动生成编码'"
              placement="top"
            >
              <el-button
                :loading="nodeStatusCodeAiGenerating"
                :disabled="!nodeStatusForm.name.trim() || !!editingNodeStatus"
                @click="handleNodeStatusAiGenerateCode"
              >
                <el-icon v-if="!nodeStatusCodeAiGenerating"><MagicStick /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="nodeStatusForm.color" show-alpha />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="nodeStatusForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="特殊标记" class="node-status-flags-item">
          <div class="node-status-flags">
            <el-checkbox v-model="nodeStatusForm.isStart" class="node-status-flags__option">开始状态</el-checkbox>
            <el-checkbox v-model="nodeStatusForm.isEnd" class="node-status-flags__option">结束状态</el-checkbox>
            <el-checkbox v-model="nodeStatusForm.isCancel" class="node-status-flags__option">取消状态</el-checkbox>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeStatusDialogVisible = false">取消</el-button>
        <el-button v-permission="'button:requirement-config:create'" type="primary" @click="saveNodeStatus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Rank, Operation, EditPen, Delete, Document, Setting, MagicStick } from '@element-plus/icons-vue'
import { requirementConfigApi, type RequirementType, type Priority, type SortItem } from '@/api/modules/requirementConfig'
import { nodeStatusApi, type NodeStatus, type SortItem as NodeStatusSortItem } from '@/api/modules/workflow-engine'
import { listActiveWorkflowVersions, listWorkflowDefinitions } from '@/api/modules/workflow-visual'
import type { WorkflowVersionDTO, WorkflowDefinitionInfoDTO } from '@/types/workflow-visual'
import { normalizeText } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'
import Sortable, { type SortableEvent } from 'sortablejs'
import AppButton from '@/components/common/AppButton.vue'
import Toolbar from '@/components/common/Toolbar.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import RequirementTemplateManager from '@/views/settings/requirement-templates/index.vue'
import { llmProviderApi } from '@/api/modules/llmProvider'

// ── 列表字段设置：需求类型表 ──
const typeAllColumns: ColumnDef[] = [
  { key: 'drag', label: '拖拽', group: '基础字段', width: 60 },
  { key: 'name', label: '名称', group: '基础字段', minWidth: 120 },
  { key: 'code', label: '编码', group: '基础字段', minWidth: 100 },
  { key: 'color', label: '颜色', group: '基础字段', minWidth: 100 },
  { key: 'sortOrder', label: '排序', group: '基础字段', width: 80 },
  { key: 'enabled', label: '状态', group: '状态信息', width: 90 },
  { key: 'workflow', label: '绑定工作流', group: '状态信息', minWidth: 160 },
  { key: 'operations', label: '操作', width: 150 },
]
const typeDefaultKeys = ['drag', 'name', 'code', 'color', 'sortOrder', 'enabled', 'workflow', 'operations']

const {
  showColumnConfig: showTypeColumnConfig,
  openColumnConfig: openTypeColumnConfig,
  saveColumns: saveTypeColumns,
  loadColumnConfig: loadTypeColumnConfig,
  columnGroups: typeColumnGroups,
  draftSelectedColumns: typeDraftSelectedColumns,
  draftColumnKeys: typeDraftColumnKeys,
  visibleColumns: typeVisibleColumns,
  removeDraftColumn: removeTypeDraftColumn,
} = useColumnConfig({
  pageKey: 'requirement_type_list',
  columns: typeAllColumns,
  defaultKeys: typeDefaultKeys,
})

function isTypeColumnVisible(key: string) {
  return typeVisibleColumns.value.some((c) => c.key === key)
}

// ── 列表字段设置：优先级表 ──
const priorityAllColumns: ColumnDef[] = [
  { key: 'drag', label: '拖拽', group: '基础字段', width: 60 },
  { key: 'name', label: '名称', group: '基础字段', minWidth: 120 },
  { key: 'code', label: '编码', group: '基础字段', minWidth: 100 },
  { key: 'level', label: '级别', group: '基础字段', width: 80 },
  { key: 'color', label: '颜色', group: '基础字段', minWidth: 100 },
  { key: 'sortOrder', label: '排序', group: '基础字段', width: 80 },
  { key: 'isDefault', label: '默认', group: '状态信息', width: 80 },
  { key: 'operations', label: '操作', width: 100 },
]
const priorityDefaultKeys = ['drag', 'name', 'code', 'level', 'color', 'sortOrder', 'isDefault', 'operations']

const {
  showColumnConfig: showPriorityColumnConfig,
  openColumnConfig: openPriorityColumnConfig,
  saveColumns: savePriorityColumns,
  loadColumnConfig: loadPriorityColumnConfig,
  columnGroups: priorityColumnGroups,
  draftSelectedColumns: priorityDraftSelectedColumns,
  draftColumnKeys: priorityDraftColumnKeys,
  visibleColumns: priorityVisibleColumns,
  removeDraftColumn: removePriorityDraftColumn,
} = useColumnConfig({
  pageKey: 'requirement_priority_list',
  columns: priorityAllColumns,
  defaultKeys: priorityDefaultKeys,
})

function isPriorityColumnVisible(key: string) {
  return priorityVisibleColumns.value.some((c) => c.key === key)
}

// ── 列表字段设置：节点状态表 ──
const nodeStatusAllColumns: ColumnDef[] = [
  { key: 'drag', label: '拖拽', group: '基础字段', width: 60 },
  { key: 'name', label: '状态名称', group: '基础字段', minWidth: 120 },
  { key: 'code', label: '编码', group: '基础字段', minWidth: 150 },
  { key: 'color', label: '颜色', group: '基础字段', minWidth: 100 },
  { key: 'flags', label: '类型标记', group: '状态信息', minWidth: 160 },
  { key: 'sortOrder', label: '排序', group: '基础字段', width: 80 },
  { key: 'operations', label: '操作', width: 100 },
]
const nodeStatusDefaultKeys = ['drag', 'name', 'code', 'color', 'flags', 'sortOrder', 'operations']

const {
  showColumnConfig: showNodeStatusColumnConfig,
  openColumnConfig: openNodeStatusColumnConfig,
  saveColumns: saveNodeStatusColumns,
  loadColumnConfig: loadNodeStatusColumnConfig,
  columnGroups: nodeStatusColumnGroups,
  draftSelectedColumns: nodeStatusDraftSelectedColumns,
  draftColumnKeys: nodeStatusDraftColumnKeys,
  visibleColumns: nodeStatusVisibleColumns,
  removeDraftColumn: removeNodeStatusDraftColumn,
} = useColumnConfig({
  pageKey: 'requirement_node_status_list',
  columns: nodeStatusAllColumns,
  defaultKeys: nodeStatusDefaultKeys,
})

function isNodeStatusColumnVisible(key: string) {
  return nodeStatusVisibleColumns.value.some((c) => c.key === key)
}

// 需求模板表的操作：委托给 RequirementTemplateManager 内部方法
const templateManagerRef = ref<InstanceType<typeof RequirementTemplateManager> | null>(null)
function openTemplateColumnConfig() {
  templateManagerRef.value?.openColumnConfig?.()
}
function openTemplateCreate() {
  templateManagerRef.value?.handleCreate?.()
}

const selectedTypeCodeForTemplate = ref('')

const activeTab = ref('types')

function goToTemplateDesign(row: RequirementType) {
  selectedTypeCodeForTemplate.value = row.code
  activeTab.value = 'templates'
}
const types = ref<RequirementType[]>([])
const priorities = ref<Priority[]>([])

// 活跃工作流版本列表（用于类型绑定下拉）
const activeWorkflowVersions = ref<WorkflowVersionDTO[]>([])

// 工作流定义列表（绑定弹框第一级下拉：先选工作流名称）
const workflowDefinitions = ref<WorkflowDefinitionInfoDTO[]>([])

/** 当前选中工作流下的版本选项 */
const filteredVersionOptions = computed(() => {
  if (!typeForm.value.workflowDefinitionId) return []
  return activeWorkflowVersions.value.filter(
    (v) => v.workflowDefinitionId === typeForm.value.workflowDefinitionId
  )
})

const loadActiveWorkflowVersions = async () => {
  try {
    const res = await listActiveWorkflowVersions() as any
    const list = Array.isArray(res) ? res : res?.data || []
    activeWorkflowVersions.value = list.filter((v: WorkflowVersionDTO) => v.activationStatus === 'active' && v.isActive === 1)
  } catch (error) {
    ElMessage.warning(resolveErrorMessage(error, '加载可绑定工作流失败，请稍后刷新重试'))
    activeWorkflowVersions.value = []
  }
}

const loadWorkflowDefinitions = async () => {
  try {
    const res = await listWorkflowDefinitions() as any
    const list = Array.isArray(res) ? res : res?.data || []
    workflowDefinitions.value = list
  } catch (error) {
    ElMessage.warning(resolveErrorMessage(error, '加载工作流定义失败，请稍后刷新重试'))
    workflowDefinitions.value = []
  }
}

/** 切换工作流时清空已选版本号 */
const handleDefinitionChange = () => {
  typeForm.value.workflowVersionId = null
}

// 表格ref
const typeTableRef = ref()
const priorityTableRef = ref()
const nodeStatusTableRef = ref()

// 类型对话框
const typeDialogVisible = ref(false)
const typeFormRef = ref<FormInstance>()
const editingType = ref<RequirementType | null>(null)

const typeForm = ref({
  name: '',
  code: '',
  color: 'var(--color-accent)',
  sortOrder: 0,
  isDefault: false,
  workflowDefinitionId: null as number | null,
  workflowVersionId: null as number | null
})

const typeCodeManuallyEdited = ref(false)
const typeCodeAiGenerating = ref(false)

const typeRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

// 优先级对话框
const priorityDialogVisible = ref(false)
const priorityFormRef = ref<FormInstance>()
const editingPriority = ref<Priority | null>(null)

const priorityForm = ref({
  name: '',
  code: '',
  level: 2,
  color: 'var(--color-accent)',
  sortOrder: 0,
  isDefault: false
})

const priorityRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

const loadTypes = async () => {
  try {
    const res = await requirementConfigApi.listTypes() as any
    const list = Array.isArray(res) ? res : res?.data || []
    types.value = list.map((t: RequirementType) => ({ ...t, name: normalizeText(t.name) }))
  } catch (error) {
    console.error(error)
  }
}

const loadPriorities = async () => {
  try {
    const res = await requirementConfigApi.listPriorities() as any
    const list = Array.isArray(res) ? res : res?.data || []
    priorities.value = list.map((p: Priority) => ({ ...p, name: normalizeText(p.name) }))
  } catch (error) {
    console.error(error)
  }
}

const openTypeDialog = (type?: RequirementType) => {
  editingType.value = type || null
  typeCodeManuallyEdited.value = !!type
  if (type) {
    // 编辑时反查所属工作流定义，回填第一级下拉
    const boundVersion = type.workflowVersionId
      ? activeWorkflowVersions.value.find((v) => v.id === type.workflowVersionId)
      : undefined
    typeForm.value = {
      name: type.name,
      code: type.code,
      color: type.color || 'var(--color-accent)',
      sortOrder: type.sortOrder || 0,
      isDefault: type.isDefault || false,
      workflowDefinitionId: boundVersion?.workflowDefinitionId ?? null,
      workflowVersionId: type.workflowVersionId ?? null
    }
  } else {
    typeForm.value = {
      name: '',
      code: '',
      color: 'var(--color-accent)',
      sortOrder: 0,
      isDefault: false,
      workflowDefinitionId: null,
      workflowVersionId: null
    }
  }
  typeDialogVisible.value = true
}

const saveType = async () => {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingType.value?.id) {
          await requirementConfigApi.updateType(editingType.value.id, typeForm.value as RequirementType)
          // 工作流绑定变更时才同步
          const originalVersionId = editingType.value.workflowVersionId ?? null
          const newVersionId = typeForm.value.workflowVersionId
          if (originalVersionId !== newVersionId) {
            // 使用原始编码，因为数据库还没更新成新编码
            await requirementConfigApi.bindWorkflow(editingType.value.code, newVersionId)
          }
          ElMessage.success('更新成功')
        } else {
          await requirementConfigApi.createType(typeForm.value as RequirementType)
          ElMessage.success('创建成功')
        }
        typeDialogVisible.value = false
        loadTypes()
      } catch (error) {
        ElMessage.error(resolveErrorMessage(error, '保存失败'))
      }
    }
  })
}

const deleteType = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该类型吗？', '提示', { type: 'warning' })
  try {
    await requirementConfigApi.deleteType(id)
    ElMessage.success('删除成功')
    loadTypes()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

/** 行内切换需求类型启用状态 */
const toggleTypeEnabled = async (row: RequirementType, val: boolean) => {
  const previous = row.enabled
  row._enabledLoading = true
  try {
    await requirementConfigApi.updateTypeEnabled(row.id!, val)
    row.enabled = val
    ElMessage.success(val ? '已启用' : '已禁用')
    // 禁用后刷新列表（确保 available 下拉同步）；开启成功无需重载
    if (!val) await loadTypes()
  } catch (error) {
    row.enabled = previous
    ElMessage.error(resolveErrorMessage(error, val ? '启用失败' : '禁用失败'))
  } finally {
    row._enabledLoading = false
  }
}

const openPriorityDialog = (priority?: Priority) => {
  editingPriority.value = priority || null
  if (priority) {
    priorityForm.value = {
      name: priority.name,
      code: priority.code,
      level: priority.level || 2,
      color: priority.color || 'var(--color-accent)',
      sortOrder: priority.sortOrder || 0,
      isDefault: priority.isDefault || false
    }
  } else {
    priorityForm.value = {
      name: '',
      code: '',
      level: 2,
      color: 'var(--color-accent)',
      sortOrder: 0,
      isDefault: false
    }
  }
  priorityDialogVisible.value = true
}

const savePriority = async () => {
  if (!priorityFormRef.value) return
  await priorityFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingPriority.value?.id) {
          await requirementConfigApi.updatePriority(editingPriority.value.id, priorityForm.value as Priority)
          ElMessage.success('更新成功')
        } else {
          await requirementConfigApi.createPriority(priorityForm.value as Priority)
          ElMessage.success('创建成功')
        }
        priorityDialogVisible.value = false
        loadPriorities()
      } catch (error) {
        ElMessage.error(resolveErrorMessage(error, '保存失败'))
      }
    }
  })
}

const deletePriority = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该优先级吗？', '提示', { type: 'warning' })
  try {
    await requirementConfigApi.deletePriority(id)
    ElMessage.success('删除成功')
    loadPriorities()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

/** 行内切换优先级默认状态 */
const togglePriorityDefault = async (row: Priority, val: boolean) => {
  const previous = row.isDefault
  row._defaultLoading = true
  try {
    await requirementConfigApi.updatePriority(row.id!, { ...row, isDefault: val })
    // 后端会互斥同步其他优先级，重新拉取列表刷新
    await loadPriorities()
    ElMessage.success(val ? '已设为默认优先级' : '已取消默认优先级')
  } catch (error) {
    // 失败回滚UI状态
    row.isDefault = previous
    ElMessage.error(resolveErrorMessage(error, '切换默认失败'))
  } finally {
    row._defaultLoading = false
  }
}

// 初始化拖拽排序
const initTypeSortable = () => {
  nextTick(() => {
    const el = typeTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        // 更新本地数据
        const movedItem = types.value.splice(oldIndex!, 1)[0]
        types.value.splice(newIndex!, 0, movedItem)

        // 重新计算sortOrder
        const items: SortItem[] = types.value.map((item, index) => ({
          id: item.id!,
          sortOrder: index
        }))

        try {
          await requirementConfigApi.sortTypes(items)
          ElMessage.success('排序已保存')
          loadTypes()
        } catch (error) {
          ElMessage.error(resolveErrorMessage(error, '排序保存失败'))
          loadTypes() // 恢复原始顺序
        }
      }
    })
  })
}

const initPrioritySortable = () => {
  nextTick(() => {
    const el = priorityTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        // 更新本地数据
        const movedItem = priorities.value.splice(oldIndex!, 1)[0]
        priorities.value.splice(newIndex!, 0, movedItem)

        // 重新计算sortOrder
        const items: SortItem[] = priorities.value.map((item, index) => ({
          id: item.id!,
          sortOrder: index
        }))

        try {
          await requirementConfigApi.sortPriorities(items)
          ElMessage.success('排序已保存')
          loadPriorities()
        } catch (error) {
          ElMessage.error(resolveErrorMessage(error, '排序保存失败'))
          loadPriorities() // 恢复原始顺序
        }
      }
    })
  })
}

const initNodeStatusSortable = () => {
  nextTick(() => {
    const el = nodeStatusTableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!el) return

    Sortable.create(el, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'sortable-ghost',
      onEnd: async (evt: SortableEvent) => {
        const { oldIndex, newIndex } = evt
        if (oldIndex === newIndex) return

        const movedItem = nodeStatuses.value.splice(oldIndex!, 1)[0]
        nodeStatuses.value.splice(newIndex!, 0, movedItem)

        const items: NodeStatusSortItem[] = nodeStatuses.value.map((item, index) => ({
          id: item.id,
          sortOrder: index
        }))

        try {
          await nodeStatusApi.sort(items)
          ElMessage.success('排序已保存')
          await loadNodeStatuses()
        } catch (error) {
          ElMessage.error(resolveErrorMessage(error, '排序保存失败'))
          await loadNodeStatuses()
        }
      }
    })
  })
}

// 节点状态管理
const nodeStatuses = ref<NodeStatus[]>([])
const nodeStatusDialogVisible = ref(false)
const nodeStatusFormRef = ref<FormInstance>()
const editingNodeStatus = ref<NodeStatus | null>(null)
const nodeStatusCodeManuallyEdited = ref(false)
const nodeStatusCodeAiGenerating = ref(false)

const nodeStatusForm = ref({
  name: '',
  code: '',
  color: 'var(--color-accent)',
  sortOrder: 0,
  isStart: false,
  isEnd: false,
  isCancel: false
})

const nodeStatusRules: FormRules = {
  name: [{ required: true, message: '请输入状态名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入状态编码', trigger: 'blur' }]
}

const loadNodeStatuses = async () => {
  try {
    const res = await nodeStatusApi.list() as any
    nodeStatuses.value = Array.isArray(res) ? res : res?.data || []
  } catch (error) {
    console.error(error)
  }
}

const openNodeStatusDialog = (status?: NodeStatus) => {
  editingNodeStatus.value = status || null
  nodeStatusCodeManuallyEdited.value = false
  if (status) {
    nodeStatusForm.value = {
      name: status.name,
      code: status.code,
      color: status.color || 'var(--color-accent)',
      sortOrder: status.sortOrder || 0,
      isStart: status.isStart || false,
      isEnd: status.isEnd || false,
      isCancel: status.isCancel || false
    }
  } else {
    nodeStatusForm.value = { name: '', code: '', color: 'var(--color-accent)', sortOrder: 0, isStart: false, isEnd: false, isCancel: false }
  }
  nodeStatusDialogVisible.value = true
}

const saveNodeStatus = async () => {
  if (!nodeStatusFormRef.value) return
  if (!nodeStatusForm.value.code && nodeStatusForm.value.name) {
    nodeStatusForm.value.code = generateNodeStatusCode(nodeStatusForm.value.name)
  }
  await nodeStatusFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (editingNodeStatus.value?.id) {
          await nodeStatusApi.update(editingNodeStatus.value.id, nodeStatusForm.value)
          ElMessage.success('更新成功')
        } else {
          await nodeStatusApi.create(nodeStatusForm.value)
          ElMessage.success('创建成功')
        }
        nodeStatusDialogVisible.value = false
        loadNodeStatuses()
      } catch (error) {
        ElMessage.error(resolveErrorMessage(error, '保存失败'))
      }
    }
  })
}

const deleteNodeStatus = async (id: number) => {
  await ElMessageBox.confirm('确定要删除该节点状态吗？', '提示', { type: 'warning' })
  try {
    await nodeStatusApi.delete(id)
    ElMessage.success('删除成功')
    loadNodeStatuses()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

/** 手动点击 AI 按钮生成需求类型编码 */
async function handleTypeAiGenerateCode() {
  const name = typeForm.value.name.trim()
  if (!name) return

  try {
    typeCodeAiGenerating.value = true
    const result = await llmProviderApi.translate(name) as any
    const translated = result?.data ?? result
    if (translated && typeof translated === 'string' && /^[A-Z][A-Z0-9_]*$/.test(translated)) {
      typeForm.value.code = translated.slice(0, 50)
      typeCodeManuallyEdited.value = false
    } else {
      typeForm.value.code = generateTypeCode(name)
      ElMessage.info('未配置可用模型，已使用本地映射生成编码')
    }
  } catch {
    typeForm.value.code = generateTypeCode(name)
    ElMessage.info('AI 服务暂不可用，已使用本地映射生成编码')
  } finally {
    typeCodeAiGenerating.value = false
  }
}

/** 本地映射生成需求类型编码 */
function generateTypeCode(name: string) {
  const normalized = name.trim()
  if (!normalized) return ''
  const ascii = normalized
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase()
  if (/^[A-Z][A-Z0-9_]*$/.test(ascii)) {
    return ascii.slice(0, 50)
  }
  const translated = translateChineseTypeName(normalized)
  if (translated) {
    return translated.slice(0, 50)
  }
  return `TYPE_${shortHash(normalized)}`.slice(0, 50)
}

function translateChineseTypeName(name: string) {
  const exactMap: Record<string, string> = {
    '功能需求': 'FEATURE',
    '功能': 'FEATURE',
    '需求': 'REQUIREMENT',
    '缺陷': 'BUG',
    '故障': 'INCIDENT',
    '优化': 'OPTIMIZATION',
    '改进': 'IMPROVEMENT',
    '重构': 'REFACTOR',
    '技术债务': 'TECH_DEBT',
    '任务': 'TASK',
    '子任务': 'SUBTASK',
    '史诗': 'EPIC',
    '用户故事': 'USER_STORY',
    '调研': 'RESEARCH',
    '测试': 'TEST',
    '文档': 'DOCUMENTATION',
    '配置': 'CONFIG',
    '安全': 'SECURITY',
  }
  if (exactMap[name]) return exactMap[name]

  const segments: Array<[RegExp, string]> = [
    [/功能|特性/g, 'FEATURE'],
    [/缺陷|bug|BUG|故障/g, 'BUG'],
    [/优化/g, 'OPTIMIZATION'],
    [/改进/g, 'IMPROVEMENT'],
    [/重构/g, 'REFACTOR'],
    [/技术/g, 'TECH'],
    [/任务/g, 'TASK'],
    [/史诗/g, 'EPIC'],
    [/调研|研究/g, 'RESEARCH'],
    [/测试/g, 'TEST'],
    [/文档/g, 'DOCUMENTATION'],
    [/配置/g, 'CONFIG'],
    [/安全/g, 'SECURITY'],
    [/需求/g, 'REQUIREMENT'],
  ]
  const parts = segments
    .filter(([pattern]) => pattern.test(name))
    .map(([, word]) => word)
  return Array.from(new Set(parts)).join('_')
}

function shortHash(value: string) {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = ((hash << 5) - hash + value.charCodeAt(index)) >>> 0
  }
  return hash.toString(36).toUpperCase().padStart(6, '0')
}

/** 输入节点状态名称时本地映射生成编码 */
function handleNodeStatusNameInput() {
  if (editingNodeStatus.value || nodeStatusCodeManuallyEdited.value) return
  nodeStatusForm.value.code = generateNodeStatusCode(nodeStatusForm.value.name)
}

/** 手动点击 AI 按钮生成节点状态编码 */
async function handleNodeStatusAiGenerateCode() {
  const name = nodeStatusForm.value.name.trim()
  if (!name) return

  try {
    nodeStatusCodeAiGenerating.value = true
    const result = await llmProviderApi.translate(name) as any
    const translated = result?.data ?? result
    if (translated && typeof translated === 'string' && /^[A-Z][A-Z0-9_]*$/.test(translated)) {
      nodeStatusForm.value.code = translated.slice(0, 50)
      nodeStatusCodeManuallyEdited.value = false
    } else {
      nodeStatusForm.value.code = generateNodeStatusCode(name)
      ElMessage.info('未配置可用模型，已使用本地映射生成编码')
    }
  } catch {
    nodeStatusForm.value.code = generateNodeStatusCode(name)
    ElMessage.info('AI 服务暂不可用，已使用本地映射生成编码')
  } finally {
    nodeStatusCodeAiGenerating.value = false
  }
}

/** 本地映射生成节点状态编码 */
function generateNodeStatusCode(name: string) {
  const normalized = name.trim()
  if (!normalized) return ''
  const ascii = normalized
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase()
  if (/^[A-Z][A-Z0-9_]*$/.test(ascii)) {
    return ascii.slice(0, 50)
  }
  const translated = translateChineseNodeStatusName(normalized)
  if (translated) {
    return translated.slice(0, 50)
  }
  return `STATUS_${shortHash(normalized)}`.slice(0, 50)
}

function translateChineseNodeStatusName(name: string) {
  const exactMap: Record<string, string> = {
    '待评审': 'PENDING_REVIEW',
    '评审中': 'UNDER_REVIEW',
    '已评审': 'REVIEWED',
    '待审批': 'PENDING_APPROVAL',
    '审批中': 'UNDER_APPROVAL',
    '已审批': 'APPROVED',
    '待开发': 'PENDING_DEVELOPMENT',
    '开发中': 'IN_PROGRESS',
    '开发完成': 'DEVELOPMENT_DONE',
    '待测试': 'PENDING_TEST',
    '测试中': 'TESTING',
    '测试通过': 'TEST_PASSED',
    '测试失败': 'TEST_FAILED',
    '已发布': 'RELEASED',
    '已上线': 'DEPLOYED',
    '已验收': 'ACCEPTED',
    '已关闭': 'CLOSED',
    '已取消': 'CANCELLED',
    '已挂起': 'SUSPENDED',
    '已拒绝': 'REJECTED',
    '已废弃': 'DISCARDED',
  }
  if (exactMap[name]) return exactMap[name]

  const segments: Array<[RegExp, string]> = [
    [/评审|审查/g, 'REVIEW'],
    [/审批|批准/g, 'APPROVAL'],
    [/开发|研发/g, 'DEVELOPMENT'],
    [/测试/g, 'TEST'],
    [/发布/g, 'RELEASE'],
    [/上线|部署/g, 'DEPLOY'],
    [/验收/g, 'ACCEPT'],
    [/关闭/g, 'CLOSE'],
    [/取消/g, 'CANCEL'],
    [/挂起|暂停/g, 'SUSPEND'],
    [/拒绝/g, 'REJECT'],
    [/废弃|作废/g, 'DISCARD'],
    [/开始/g, 'START'],
    [/结束|完成/g, 'DONE'],
    [/待/g, 'PENDING'],
    [/中/g, 'IN_PROGRESS'],
    [/已/g, ''],
  ]
  const parts = segments
    .filter(([pattern]) => pattern.test(name))
    .map(([, word]) => word)
    .filter(word => word)
  return Array.from(new Set(parts)).join('_')
}

/** 输入名称时本地映射生成编码 */
function handleTypeNameInput() {
  if (editingType.value || typeCodeManuallyEdited.value) return
  typeForm.value.code = generateTypeCode(typeForm.value.name)
}

/** 关闭类型弹窗时重置 AI 生成状态 */
function resetTypeForm() {
  typeCodeManuallyEdited.value = false
  typeCodeAiGenerating.value = false
  typeFormRef.value?.resetFields()
}

const initializePage = async () => {
  await Promise.all([
    loadTypes(),
    loadPriorities(),
    loadNodeStatuses(),
    loadActiveWorkflowVersions(),
    loadWorkflowDefinitions(),
  ])
  initTypeSortable()
  initPrioritySortable()
  initNodeStatusSortable()
}

onMounted(() => {
  void initializePage()
  loadTypeColumnConfig()
  loadPriorityColumnConfig()
  loadNodeStatusColumnConfig()
})
</script>

<style lang="scss" scoped>
.config-container {
  padding: 20px;
}

.config-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: var(--color-text-primary);
  }

  .config-desc {
    margin: 0;
    color: var(--color-muted-text);
    font-size: 14px;
  }
}

.config-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }
}

.tab-content {
  padding: 0 4px;
}

.tab-header {
  margin-bottom: 16px;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .color-dot {
    width: 16px;
    height: 16px;
    border-radius: 4px;
  }
}

.form-tip {
  margin-left: 8px;
  color: var(--color-muted-text);
  font-size: 12px;
}

.node-status-flags {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 140px));
  column-gap: 28px;
  row-gap: 14px;
  width: 100%;
  padding-top: 4px;
}

.node-status-flags__option {
  margin-right: 0;
}

:deep(.node-status-flags__option .el-checkbox__label) {
  white-space: nowrap;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  padding: 6px;
  border-radius: 4px;
  transition: color 0.2s, background-color 0.2s, transform 0.15s;
  user-select: none;

  &:hover {
    color: var(--color-accent);
    background-color: var(--color-info-light);
  }

  &:active {
    cursor: grabbing;
    color: #337ecc;
    background-color: #d9ecff;
    transform: scale(1.1);
  }
}

:deep(.sortable-ghost) {
  opacity: 0.35;
  background: var(--color-info-light) !important;
  outline: 2px dashed var(--color-accent);
  outline-offset: -2px;
}

:deep(.sortable-chosen) {
  background: #f5f7fa;
}

:deep(.el-table__body-wrapper tbody) {
  tr {
    transition: transform 0.3s;
  }
}
</style>

