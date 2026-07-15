<template>
  <div class="bitable-editor">
    <!-- 顶部面包屑 -->
    <div class="editor-header">
      <el-button link @click="router.push('/bitable')">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="editor-title">{{ base?.name }}</span>
    </div>

    <div class="editor-body">
      <!-- 左侧数据表侧边栏 -->
      <div class="editor-sidebar" :class="{ 'editor-sidebar--collapsed': sidebarCollapsed }">
        <div class="editor-sidebar__inner">
          <TableSidebar
            :tables="tables"
            :activeTableId="activeTableId"
            @select="handleSelectTable"
            @create="handleCreateTable"
            @delete="handleDeleteTable"
          />
        </div>
        <div class="editor-sidebar__toggle" @click="toggleSidebar">
          <el-icon><ArrowLeft v-if="!sidebarCollapsed" /><ArrowRight v-else /></el-icon>
        </div>
      </div>

      <!-- 主编辑区域 -->
      <div class="editor-main">
        <Toolbar
          :table="activeTable"
          :views="views"
          :viewType="currentViewType"
          @add-field="handleAddField"
          @manage-view="handleManageView"
          @open-comments="handleOpenComments"
          @open-members="showMemberManager = true"
          @view-type-change="handleViewTypeChange"
          @open-ai-panel="showAiPanel = true"
          @open-import-export="showImportExport = true"
          @open-field-config="fieldConfigDrawerVisible = true"
        />
        <GridView
          v-if="currentViewType === 'grid'"
          :table="activeTable"
          :fields="fields"
          :records="records"
          :loading="loadingRecords"
          @cell-change="handleCellChange"
          @row-insert="handleRowInsert"
          @row-delete="handleRowDelete"
          @rename-field="(fieldId: number) => handleRenameField(fieldId)"
          @clone-field="handleCloneField"
          @header-dragend="handleHeaderDragend"
        />
        <KanbanView
          v-else-if="currentViewType === 'kanban'"
          :table="activeTable"
          :fields="fields"
          :records="records"
          :loading="loadingRecords"
          @record-update="handleKanbanRecordUpdate"
          @card-move="handleCardMove"
          @row-insert="handleRowInsert"
        />
        <GanttView
          v-else-if="currentViewType === 'gantt'"
          :table="activeTable"
          :fields="fields"
          :records="records"
          :loading="loadingRecords"
        />
        <CalendarView
          v-else-if="currentViewType === 'calendar'"
          :table="activeTable"
          :fields="fields"
          :records="records"
          :loading="loadingRecords"
        />
        <GalleryView
          v-else-if="currentViewType === 'gallery'"
          :table="activeTable"
          :fields="fields"
          :records="records"
          :loading="loadingRecords"
        />
      </div>
    </div>

    <!-- 字段配置弹窗 -->
    <el-drawer v-model="fieldConfigDrawerVisible" title="字段配置" size="500px" @close="handleFieldConfigClose">
      <div class="field-config-content">
        <!-- 字段列表 -->
        <div class="field-list">
          <div
            v-for="field in fields"
            :key="field.id"
            class="field-item"
            :class="{ 'field-item--active': editingFieldId === field.id }"
            @click="selectFieldForEdit(field)"
          >
            <div class="field-item__info">
              <span class="field-item__name">{{ field.name }}</span>
              <el-tag size="small" type="info" class="field-item__type-tag">{{ fieldTypeLabel(field.fieldType) }}</el-tag>
            </div>
            <div class="field-item__actions">
              <el-button link size="small" @click.stop="selectFieldForEdit(field)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button link size="small" @click.stop="handleCopyField(field)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
              <el-button link size="small" type="danger" @click.stop="handleDeleteField(field)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <el-empty v-if="!fields.length" description="暂无字段" />
        </div>

        <!-- 编辑区域 -->
        <div v-if="editingField" class="field-editor">
          <h4 class="field-editor__title">字段配置</h4>
          <el-form :model="editForm" label-width="100px" size="small">
            <!-- 公共配置 -->
            <el-form-item label="字段名称">
              <el-input v-model="editForm.name" placeholder="字段名称" maxlength="200" />
            </el-form-item>
            <el-form-item label="字段描述">
              <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="字段描述" />
            </el-form-item>
            <el-form-item label="字段宽度">
              <el-input-number v-model="editForm.width" :min="50" :max="500" />
            </el-form-item>
            <el-form-item label="是否必填">
              <el-switch v-model="editForm.required" />
            </el-form-item>

            <!-- 文本特有配置 -->
            <template v-if="editingField.fieldType === 'text'">
              <el-divider content-position="left">文本配置</el-divider>
              <el-form-item label="默认值">
                <el-input v-model="editForm.defaultValue" placeholder="默认文本" />
              </el-form-item>
            </template>

            <!-- 数字特有配置 -->
            <template v-if="editingField.fieldType === 'number'">
              <el-divider content-position="left">数字配置</el-divider>
              <el-form-item label="小数位数">
                <el-input-number v-model="editForm.precision" :min="0" :max="10" />
              </el-form-item>
              <el-form-item label="默认值">
                <el-input-number v-model="editForm.defaultNumber" :min="-999999999" :max="999999999" />
              </el-form-item>
            </template>

            <!-- 日期特有配置 -->
            <template v-if="editingField.fieldType === 'date'">
              <el-divider content-position="left">日期配置</el-divider>
              <el-form-item label="日期格式">
                <el-select v-model="editForm.dateFormat" style="width: 100%;">
                  <el-option label="YYYY-MM-DD" value="YYYY-MM-DD" />
                  <el-option label="YYYY/MM/DD" value="YYYY/MM/DD" />
                  <el-option label="DD/MM/YYYY" value="DD/MM/YYYY" />
                  <el-option label="MM/DD/YYYY" value="MM/DD/YYYY" />
                  <el-option label="YYYY年MM月DD日" value="YYYY年MM月DD日" />
                </el-select>
              </el-form-item>
              <el-form-item label="默认值">
                <el-date-picker v-model="editForm.defaultDate" type="date" placeholder="选择默认日期" style="width: 100%;" value-format="YYYY-MM-DD" />
              </el-form-item>
            </template>

            <!-- 单选/多选特有配置 -->
            <template v-if="editingField.fieldType === 'single_select' || editingField.fieldType === 'multi_select'">
              <el-divider content-position="left">选项配置</el-divider>
              <div class="option-list">
                <div v-for="(opt, idx) in editForm.options" :key="idx" class="option-item">
                  <span class="option-color-dot" :style="{ background: opt.color || '#409eff' }" />
                  <el-input v-model="opt.label" placeholder="选项名称" size="small" />
                  <el-color-picker v-model="opt.color" size="small" />
                  <el-button link size="small" @click="removeEditOption(idx)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button link type="primary" size="small" @click="addEditOption">
                  <el-icon><Plus /></el-icon> 添加选项
                </el-button>
              </div>
            </template>

            <!-- 评分特有配置 -->
            <template v-if="editingField.fieldType === 'rating'">
              <el-divider content-position="left">评分配置</el-divider>
              <el-form-item label="评分符号">
                <el-input v-model="editForm.ratingSymbol" placeholder="★" maxlength="10" style="width: 120px;" />
              </el-form-item>
              <el-form-item label="最大分值">
                <el-input-number v-model="editForm.maxRating" :min="1" :max="10" />
              </el-form-item>
            </template>

            <!-- 进度特有配置 -->
            <template v-if="editingField.fieldType === 'progress'">
              <el-divider content-position="left">进度配置</el-divider>
              <el-form-item label="显示格式">
                <el-select v-model="editForm.progressFormat" style="width: 100%;">
                  <el-option label="百分比" value="percent" />
                  <el-option label="数值" value="value" />
                </el-select>
              </el-form-item>
            </template>

            <!-- 公式特有配置 -->
            <template v-if="editingField.fieldType === 'formula'">
              <el-divider content-position="left">公式配置</el-divider>
              <el-form-item label="公式表达式">
                <el-input v-model="editForm.formulaExpr" type="textarea" :rows="3" placeholder="例如: {单价} * {数量}" />
              </el-form-item>
              <el-form-item>
                <el-button size="small" @click="openFormulaEditorForConfig">
                  <el-icon><MagicStick /></el-icon> 公式编辑器
                </el-button>
              </el-form-item>
            </template>

            <!-- 关联特有配置 -->
            <template v-if="editingField.fieldType === 'link'">
              <el-divider content-position="left">关联配置</el-divider>
              <el-form-item label="目标表">
                <el-select v-model="editForm.linkTargetTableId" placeholder="选择关联数据表" style="width: 100%;">
                  <el-option v-for="t in tables.filter(t => t.id !== activeTableId)" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="显示字段">
                <el-select v-model="editForm.linkDisplayFieldId" placeholder="选择显示字段" style="width: 100%;">
                  <el-option
                    v-for="f in linkTargetFields"
                    :key="f.id"
                    :label="f.name"
                    :value="f.id"
                  />
                </el-select>
              </el-form-item>
            </template>
          </el-form>

          <div class="field-editor__actions">
            <el-button size="small" @click="cancelFieldEdit">取消</el-button>
            <el-button size="small" type="primary" @click="saveFieldConfig" :loading="savingFieldConfig">保存配置</el-button>
          </div>
        </div>
        <el-empty v-else-if="fields.length" description="请选择要编辑的字段" />
      </div>
    </el-drawer>
    <el-dialog v-model="addFieldDialogVisible" title="添加字段" width="500px">
      <el-form :model="addFieldForm" label-width="80px">
        <el-form-item label="字段名称" required>
          <el-input v-model="addFieldForm.name" placeholder="输入字段名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="字段类型" required>
          <el-select v-model="addFieldForm.fieldType" placeholder="选择字段类型" style="width: 100%;">
            <el-option label="文本" value="text" />
            <el-option label="数字" value="number" />
            <el-option label="日期" value="date" />
            <el-option label="单选" value="single_select" />
            <el-option label="多选" value="multi_select" />
            <el-option label="用户" value="user" />
            <el-option label="复选框" value="check" />
            <el-option label="附件" value="attachment" />
            <el-option label="链接" value="url" />
            <el-option label="邮箱" value="email" />
            <el-option label="电话" value="phone" />
            <el-option label="进度" value="progress" />
            <el-option label="评分" value="rating" />
            <el-option label="关联" value="link" />
            <el-option label="汇总" value="rollup" />
            <el-option label="引用" value="lookup" />
            <el-option label="公式" value="formula" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="addFieldForm.fieldType === 'single_select' || addFieldForm.fieldType === 'multi_select'" label="选项">
          <div class="option-list">
            <div v-for="(opt, idx) in addFieldOptionList" :key="idx" class="option-item">
              <el-input v-model="opt.label" placeholder="选项名称" size="small" />
              <el-button link size="small" @click="removeOption(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button link type="primary" size="small" @click="addOption">
              <el-icon><Plus /></el-icon> 添加选项
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="addFieldForm.fieldType === 'link'" label="目标表">
          <el-select v-model="linkTargetTableId" placeholder="选择关联数据表" style="width: 100%;">
            <el-option v-for="t in tables.filter(t => t.id !== activeTableId)" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="addFieldForm.fieldType === 'formula'" label="公式">
          <el-input v-model="formulaExpr" type="textarea" :rows="2" placeholder="例如: {单价} * {数量}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addFieldDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAddField" :loading="savingField">添加</el-button>
      </template>
    </el-dialog>

    <!-- 关联选择器 -->
    <LinkFieldSelector
      :visible="linkSelectorVisible"
      :target-table-id="currentLinkField?.config?.linkTargetTableId"
      :selected-ids="currentLinkSelectedIds"
      @confirm="handleLinkConfirm"
      @close="linkSelectorVisible = false"
    />

    <!-- 公式编辑器 -->
    <FormulaEditor
      :visible="formulaEditorVisible"
      :formula-text="formulaExpr"
      :fields="fields"
      @confirm="handleFormulaConfirm"
      @close="formulaEditorVisible = false"
    />

    <!-- 评论面板 -->
    <CommentPanel
      :visible="showCommentPanel"
      :record-id="commentRecordId"
      :table-id="activeTableId"
      @close="showCommentPanel = false"
    />

    <!-- 成员管理 -->
    <MemberManager
      :visible="showMemberManager"
      :base-id="baseId"
      @close="showMemberManager = false"
    />

    <!-- AI 面板 -->
    <AiChatPanel
      :visible="showAiPanel"
      :base-id="baseId"
      :table-id="activeTableId ?? undefined"
      :records="records"
      :fields="fields"
      @close="showAiPanel = false"
    />

    <!-- 导入/导出 -->
    <ImportDialog
      v-if="activeTableId != null"
      :visible="showImport"
      :table-id="activeTableId"
      @close="showImport = false"
      @imported="handleImported"
    />
    <ExportDialog
      v-if="activeTableId != null"
      :visible="showExport"
      :table-id="activeTableId"
      :table-name="activeTable?.name || ''"
      @close="showExport = false"
    />

    <!-- 冲突提示弹窗 -->
    <ConflictDialog
      :visible="conflictVisible"
      :message="conflictMessage"
      @refresh="handleRefresh"
      @close="conflictVisible = false"
    />

    <!-- 重命名字段弹窗 -->
    <el-dialog v-model="renameFieldDialogVisible" title="重命名字段" width="400px" @close="() => { renameFieldDialogVisible = false; renameFieldId = null; renameFieldName = '' }">
      <el-input
        v-model="renameFieldName"
        placeholder="输入新的字段名称"
        maxlength="200"
        ref="renameInputRef"
        @keyup.enter="handleRenameConfirm"
      />
      <template #footer>
        <el-button @click="renameFieldDialogVisible = false; renameFieldId = null; renameFieldName = ''">取消</el-button>
        <el-button type="primary" @click="handleRenameConfirm" :loading="savingField">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { ArrowLeft, Plus, Delete, Edit, CopyDocument, ArrowRight, MagicStick } from '@element-plus/icons-vue'
import TableSidebar from './components/TableSidebar.vue'
import Toolbar from './components/Toolbar.vue'
import GridView from './components/GridView.vue'
import KanbanView from './components/KanbanView.vue'
import GanttView from './components/GanttView.vue'
import CalendarView from './components/CalendarView.vue'
import GalleryView from './components/GalleryView.vue'
import CommentPanel from './components/CommentPanel.vue'
import MemberManager from './components/MemberManager.vue'
import ConflictDialog from './components/ConflictDialog.vue'
import AiChatPanel from './components/AiChatPanel.vue'
import ImportDialog from './components/ImportDialog.vue'
import ExportDialog from './components/ExportDialog.vue'
import LinkFieldSelector from './components/LinkFieldSelector.vue'
import FormulaEditor from './components/FormulaEditor.vue'
import { useBitableWebSocket, type CellUpdateEvent, type ConflictEvent } from '@/composables/useBitableWebSocket'
import {
  getBase,
  listTables,
  createTable,
  deleteTable,
  listFields,
  createField,
  updateField,
  deleteField,
  sortFields,
  listRecords,
  createRecord,
  deleteRecord,
  updateCell,
} from '@/api/modules/bitable'
import type {
  BitableBase,
  BitableTable,
  BitableField,
  BitableRecord,
  BitableView,
  BitableFieldCreateDTO,
  BitableRecordCreateDTO,
  CellUpdateDTO,
  ViewType,
} from '@/types/bitable'

const route = useRoute()
const router = useRouter()

const baseId = Number(route.params.baseId)
const base = ref<BitableBase | null>(null)
const tables = ref<BitableTable[]>([])
const activeTableId = ref<number | null>(null)
const fields = ref<BitableField[]>([])
const records = ref<BitableRecord[]>([])
const views = ref<BitableView[]>([])
const loadingRecords = ref(false)
const savingField = ref(false)
const currentViewType = ref<ViewType>('grid')
const renameInputRef = ref<any>(null)
const showCommentPanel = ref(false)
const showMemberManager = ref(false)
const showAiPanel = ref(false)
const showImport = ref(false)
const showExport = ref(false)
const showImportExport = ref(false)
const commentRecordId = ref<number | null>(null)
const sidebarCollapsed = ref(false)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const linkSelectorVisible = ref(false)
const currentLinkField = ref<BitableField | null>(null)
const currentLinkRecordId = ref<number | null>(null)
const currentLinkSelectedIds = ref<number[]>([])

const renameFieldDialogVisible = ref(false)
const renameFieldId = ref<number | null>(null)
const renameFieldName = ref('')

const formulaEditorVisible = ref(false)
const formulaExpr = ref('')

// 字段配置弹窗
const fieldConfigDrawerVisible = ref(false)
const editingFieldId = ref<number | null>(null)
const savingFieldConfig = ref(false)
const editingField = computed<BitableField | null>(() => {
  if (editingFieldId.value === null) return null
  return fields.value.find((f) => f.id === editingFieldId.value) ?? null
})

// 字段编辑表单
const editForm = ref<{
  name: string
  description: string
  width: number
  required: boolean
  defaultValue: string
  defaultNumber: number
  defaultDate: string
  precision: number
  dateFormat: string
  options: { label: string; color?: string }[]
  ratingSymbol: string
  maxRating: number
  progressFormat: string
  formulaExpr: string
  linkTargetTableId: number | null
  linkDisplayFieldId: number | null
}>({
  name: '',
  description: '',
  width: 200,
  required: false,
  defaultValue: '',
  defaultNumber: 0,
  defaultDate: '',
  precision: 0,
  dateFormat: 'YYYY-MM-DD',
  options: [],
  ratingSymbol: '★',
  maxRating: 5,
  progressFormat: 'percent',
  formulaExpr: '',
  linkTargetTableId: null,
  linkDisplayFieldId: null,
})

// 关联目标表的字段列表
const linkTargetFields = ref<BitableField[]>([])

// 字段类型中文标签
const fieldTypeLabelMap: Record<string, string> = {
  text: '文本',
  number: '数字',
  date: '日期',
  single_select: '单选',
  multi_select: '多选',
  user: '用户',
  department: '部门',
  check: '复选框',
  attachment: '附件',
  url: '链接',
  email: '邮箱',
  phone: '电话',
  progress: '进度',
  rating: '评分',
  link: '关联',
  rollup: '汇总',
  lookup: '引用',
  formula: '公式',
  ai_text: 'AI文本',
  ai_select: 'AI选择',
  auto_number: '自动编号',
  created_time: '创建时间',
  modified_time: '修改时间',
  created_user: '创建人',
  modified_user: '修改人',
  date_range: '日期范围',
}

function fieldTypeLabel(type: string): string {
  return fieldTypeLabelMap[type] || type
}

// WebSocket 实时协作
const {
  connect: wsConnect,
  sendCellUpdate,
  onCellUpdated,
  onConflict,
} = useBitableWebSocket(baseId)
const conflictVisible = ref(false)
const conflictMessage = ref('')

onCellUpdated.value = (event: CellUpdateEvent) => {
  const record = records.value.find((r) => r.id === event.recordId)
  if (record && record.cells) {
    record.cells[event.fieldId] = {
      fieldId: event.fieldId,
      valueText: typeof event.value === 'string' ? event.value : undefined,
      valueNumber: typeof event.value === 'number' ? event.value : undefined,
    }
    record.updatedBy = event.userId
    record.version = event.version
  }
}

onConflict.value = (event: ConflictEvent) => {
  conflictMessage.value = event.message
  conflictVisible.value = true
}

const addFieldDialogVisible = ref(false)
const addFieldForm = ref<BitableFieldCreateDTO>({
  name: '',
  fieldType: 'text',
})
const addFieldOptionList = ref<{ label: string; color?: string }[]>([])
const linkTargetTableId = ref<number | null>(null)

const activeTable = computed<BitableTable | null>(() => {
  if (!activeTableId.value) return null
  return tables.value.find((t) => t.id === activeTableId.value) ?? null
})

onMounted(async () => {
  await loadBase()
  await loadTables()
  if (tables.value.length > 0) {
    handleSelectTable(tables.value[0].id)
  }
  wsConnect()
})

async function loadBase() {
  try {
    base.value = await getBase(baseId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载多维表格失败'))
  }
}

async function loadTables() {
  try {
    const res = await listTables(baseId)
    tables.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载数据表失败'))
  }
}

async function handleSelectTable(tableId: number) {
  activeTableId.value = tableId
  await loadFields(tableId)
  await loadRecords(tableId)
}

async function loadFields(tableId: number) {
  try {
    const res = await listFields(tableId)
    fields.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载字段失败'))
  }
}

async function loadRecords(tableId: number) {
  loadingRecords.value = true
  try {
    const res = await listRecords(tableId, { pageNum: 1, pageSize: 1000 })
    records.value = res.list || []
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载记录失败'))
  } finally {
    loadingRecords.value = false
  }
}

async function handleCreateTable(name: string) {
  try {
    const newId = await createTable(baseId, { name })
    ElMessage.success('创建成功')
    const newTableId = typeof newId === 'number' ? newId : Number(newId)
    tables.value.push({ id: newTableId, name, baseId } as BitableTable)
    handleSelectTable(newTableId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '创建失败'))
  }
}

async function handleDeleteTable(tableId: number) {
  try {
    await deleteTable(tableId)
    ElMessage.success('删除成功')
    tables.value = tables.value.filter((t) => t.id !== tableId)
    if (activeTableId.value === tableId) {
      if (tables.value.length > 0) {
        handleSelectTable(tables.value[0].id)
      } else {
        activeTableId.value = null
        fields.value = []
        records.value = []
      }
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '删除失败'))
  }
}

function handleAddField() {
  addFieldForm.value = { name: '', fieldType: 'text' }
  addFieldOptionList.value = []
  linkTargetTableId.value = null
  formulaExpr.value = ''
  addFieldDialogVisible.value = true
}

function addOption() {
  addFieldOptionList.value.push({ label: '' })
}

function removeOption(idx: number) {
  addFieldOptionList.value.splice(idx, 1)
}

async function submitAddField() {
  if (!addFieldForm.value.name.trim()) {
    ElMessage.warning('请输入字段名称')
    return
  }
  if (!activeTableId.value) {
    ElMessage.warning('请先选择数据表')
    return
  }
  savingField.value = true
  try {
    const data: BitableFieldCreateDTO = {
      name: addFieldForm.value.name,
      fieldType: addFieldForm.value.fieldType,
    }
    if (addFieldForm.value.fieldType === 'single_select' || addFieldForm.value.fieldType === 'multi_select') {
      data.config = { options: addFieldOptionList.value.filter((o) => o.label.trim()) }
    }
    if (addFieldForm.value.fieldType === 'link' && linkTargetTableId.value) {
      data.config = { ...(data.config || {}), linkTargetTableId: linkTargetTableId.value }
    }
    if (addFieldForm.value.fieldType === 'formula' && formulaExpr.value) {
      data.config = { ...(data.config || {}), formulaExpr: formulaExpr.value }
    }
    await createField(activeTableId.value, data)
    ElMessage.success('添加成功')
    await loadFields(activeTableId.value)
    addFieldDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '添加失败'))
  } finally {
    savingField.value = false
  }
}

function handleManageView() {
  ElMessage.info('视图管理功能开发中')
}

async function handleCellChange(data: { rowId: number; fieldId: number; newValue: unknown }) {
  const record = records.value.find((r) => r.id === data.rowId)
  if (!record) return
  const field = fields.value.find((f) => f.id === data.fieldId)
  if (!field) return

  if (field.fieldType === 'link') {
    currentLinkField.value = field
    currentLinkRecordId.value = data.rowId
    currentLinkSelectedIds.value = []
    linkSelectorVisible.value = true
    return
  }

  try {
    const updateData: any = { version: record.version }
    if (field.fieldType === 'number') {
      updateData.valueNumber = Number(data.newValue) || 0
    } else if (field.fieldType === 'date') {
      updateData.valueDate = String(data.newValue)
    } else if (field.fieldType === 'check') {
      updateData.valueText = String(Boolean(data.newValue))
    } else {
      updateData.valueText = String(data.newValue)
    }
    await updateCell(data.rowId, data.fieldId, updateData)
    ElMessage.success('更新成功')
    if (activeTableId.value) {
      sendCellUpdate(activeTableId.value, data.rowId, data.fieldId, data.newValue, record.version)
      await loadRecords(activeTableId.value)
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '更新失败'))
  }
}

async function handleLinkConfirm(ids: number[]) {
  if (!currentLinkRecordId.value || !currentLinkField.value) return
  try {
    const record = records.value.find((r) => r.id === currentLinkRecordId.value)
    if (!record) return
    await updateCell(currentLinkRecordId.value, currentLinkField.value.id, {
      version: record.version,
      valueJson: ids,
    })
    ElMessage.success('关联成功')
    if (activeTableId.value) await loadRecords(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '关联失败'))
  } finally {
    linkSelectorVisible.value = false
  }
}

function handleFormulaConfirm(val: string) {
  formulaExpr.value = val
  formulaEditorVisible.value = false
}

function handleRenameField(fieldId: number) {
  const field = fields.value.find((f) => f.id === fieldId)
  if (!field) return
  renameFieldId.value = fieldId
  renameFieldName.value = field.name
  renameFieldDialogVisible.value = true
  nextTick(() => {
    renameInputRef.value?.focus()
  })
}

function handleRenameConfirm() {
  const name = renameFieldName.value.trim()
  if (!name) {
    ElMessage.warning('字段名称不能为空')
    return
  }
  if (renameFieldId.value === null) return
  savingField.value = true
  updateField(renameFieldId.value, { name })
    .then(() => {
      ElMessage.success('重命名成功')
      renameFieldDialogVisible.value = false
      if (activeTableId.value) loadFields(activeTableId.value)
    })
    .catch((e: any) => {
      ElMessage.error(resolveErrorMessage(e, '重命名失败'))
    })
    .finally(() => {
      savingField.value = false
    })
}

// 字段配置弹窗方法
function selectFieldForEdit(field: BitableField) {
  editingFieldId.value = field.id
  editForm.value.name = field.name
  editForm.value.description = ''
  editForm.value.width = field.width || 200
  editForm.value.required = field.required || false
  editForm.value.defaultValue = ''
  editForm.value.defaultNumber = 0
  editForm.value.defaultDate = ''
  editForm.value.precision = 0
  editForm.value.dateFormat = 'YYYY-MM-DD'
  editForm.value.options = []
  editForm.value.ratingSymbol = '★'
  editForm.value.maxRating = 5
  editForm.value.progressFormat = 'percent'
  editForm.value.formulaExpr = ''
  editForm.value.linkTargetTableId = null
  editForm.value.linkDisplayFieldId = null

  const config = field.config || {}
  if (config.options) {
    editForm.value.options = JSON.parse(JSON.stringify(config.options))
  }
  if (config.format) {
    editForm.value.dateFormat = config.format
  }
  if (config.precision !== undefined) {
    editForm.value.precision = config.precision
  }
  if (config.defaultValue !== undefined) {
    if (field.fieldType === 'number') {
      editForm.value.defaultNumber = Number(config.defaultValue) || 0
    } else if (field.fieldType === 'date') {
      editForm.value.defaultDate = String(config.defaultValue) || ''
    } else {
      editForm.value.defaultValue = String(config.defaultValue) || ''
    }
  }
  if (config.formulaExpr) {
    editForm.value.formulaExpr = config.formulaExpr
  }
  if (config.linkTargetTableId) {
    editForm.value.linkTargetTableId = config.linkTargetTableId
    loadLinkTargetFields(config.linkTargetTableId)
  }
  if (config.symbol) {
    editForm.value.ratingSymbol = config.symbol
  }
  if ((config as any).maxRating !== undefined) {
    editForm.value.maxRating = (config as any).maxRating
  }
  if ((config as any).progressFormat) {
    editForm.value.progressFormat = (config as any).progressFormat
  }
  if ((config as any).linkDisplayFieldId) {
    editForm.value.linkDisplayFieldId = (config as any).linkDisplayFieldId
  }
}

async function loadLinkTargetFields(tableId: number) {
  try {
    const res = await listFields(tableId)
    linkTargetFields.value = Array.isArray(res) ? res : (res as any).data || []
  } catch {
    linkTargetFields.value = []
  }
}

async function saveFieldConfig() {
  if (!editingFieldId.value || !editingField.value) {
    ElMessage.warning('请选择要编辑的字段')
    return
  }
  savingFieldConfig.value = true
  try {
    const data: any = {
      name: editForm.value.name,
      width: editForm.value.width,
      required: editForm.value.required,
    }

    const config: any = {}

    if (editingField.value.fieldType === 'text' && editForm.value.defaultValue) {
      config.defaultValue = editForm.value.defaultValue
    }

    if (editingField.value.fieldType === 'number') {
      config.precision = editForm.value.precision
      if (editForm.value.defaultNumber !== 0) {
        config.defaultValue = editForm.value.defaultNumber
      }
    }

    if (editingField.value.fieldType === 'date') {
      config.format = editForm.value.dateFormat
      if (editForm.value.defaultDate) {
        config.defaultValue = editForm.value.defaultDate
      }
    }

    if (editingField.value.fieldType === 'single_select' || editingField.value.fieldType === 'multi_select') {
      config.options = editForm.value.options.filter((o) => o.label.trim())
    }

    if (editingField.value.fieldType === 'rating') {
      config.symbol = editForm.value.ratingSymbol || '★'
      config.maxRating = editForm.value.maxRating
    }

    if (editingField.value.fieldType === 'progress') {
      config.progressFormat = editForm.value.progressFormat
    }

    if (editingField.value.fieldType === 'formula' && editForm.value.formulaExpr) {
      config.formulaExpr = editForm.value.formulaExpr
    }

    if (editingField.value.fieldType === 'link') {
      if (editForm.value.linkTargetTableId) {
        config.linkTargetTableId = editForm.value.linkTargetTableId
      }
      if (editForm.value.linkDisplayFieldId) {
        config.linkDisplayFieldId = editForm.value.linkDisplayFieldId
      }
    }

    data.config = config
    await updateField(editingFieldId.value, data)
    ElMessage.success('配置保存成功')
    if (activeTableId.value) {
      await loadFields(activeTableId.value)
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '保存配置失败'))
  } finally {
    savingFieldConfig.value = false
  }
}

function cancelFieldEdit() {
  editingFieldId.value = null
}

function handleFieldConfigClose() {
  editingFieldId.value = null
}

async function handleCopyField(field: BitableField) {
  if (!activeTableId.value) return
  try {
    const data: BitableFieldCreateDTO = {
      name: field.name + '_副本',
      fieldType: field.fieldType,
      config: field.config ? JSON.parse(JSON.stringify(field.config)) : undefined,
      width: field.width,
      required: field.required,
    }
    await createField(activeTableId.value, data)
    ElMessage.success('复制成功')
    await loadFields(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '复制字段失败'))
  }
}

async function handleDeleteField(field: BitableField) {
  try {
    await ElMessageBox.confirm(`确定删除字段「${field.name}」吗？删除后该字段的数据将丢失且不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteField(field.id)
    ElMessage.success('删除成功')
    if (editingFieldId.value === field.id) {
      editingFieldId.value = null
    }
    if (activeTableId.value) {
      await loadFields(activeTableId.value)
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(resolveErrorMessage(e, '删除字段失败'))
    }
  }
}

function addEditOption() {
  editForm.value.options.push({ label: '', color: '#409eff' })
}

function removeEditOption(idx: number) {
  editForm.value.options.splice(idx, 1)
}

function openFormulaEditorForConfig() {
  formulaExpr.value = editForm.value.formulaExpr
  formulaEditorVisible.value = true
}

watch(formulaExpr, (val) => {
  editForm.value.formulaExpr = val
})

async function handleCloneField(fieldId: number) {
  if (!activeTableId.value) return
  const original = fields.value.find((f) => f.id === fieldId)
  if (!original) return
  savingField.value = true
  try {
    const data: BitableFieldCreateDTO = {
      name: `${original.name}_副本`,
      fieldType: original.fieldType,
      config: original.config ? JSON.parse(JSON.stringify(original.config)) : undefined,
      width: original.width,
    }
    await createField(activeTableId.value, data)
    ElMessage.success('克隆成功')
    await loadFields(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '克隆失败'))
  } finally {
    savingField.value = false
  }
}

async function handleHeaderDragend(fieldOrder: { fieldId: number; newIndex: number }[]) {
  if (!activeTableId.value) return
  try {
    const sortedFieldIds = fieldOrder
      .sort((a, b) => a.newIndex - b.newIndex)
      .map((item) => item.fieldId)
    await sortFields(activeTableId.value, sortedFieldIds)
    await loadFields(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '排序失败'))
  }
}

async function handleRowInsert(data?: { position?: 'above' | 'below'; rowId?: number; groupValue?: string; fieldId?: number }) {
  if (!activeTableId.value) return
  try {
    const cells: BitableRecordCreateDTO['cells'] = {}
    // 看板视图传入 groupValue + fieldId
    if (data?.fieldId && data?.groupValue !== undefined) {
      cells[data.fieldId] = { valueText: data.groupValue === '__ungrouped__' ? '' : data.groupValue }
    }
    await createRecord(activeTableId.value, { cells })
    ElMessage.success('添加成功')
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '添加失败'))
  }
}

async function handleRowDelete(rowId: number) {
  try {
    await deleteRecord(rowId)
    ElMessage.success('删除成功')
    records.value = records.value.filter((r) => r.id !== rowId)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '删除失败'))
  }
}

function handleViewTypeChange(type: ViewType) {
  currentViewType.value = type
}

function handleKanbanRecordUpdate(record: BitableRecord) {
  commentRecordId.value = record.id
  showCommentPanel.value = true
}

async function handleCardMove(data: { recordId: number; fieldId: number; fromGroup: string; toGroup: string }) {
  const record = records.value.find((r) => r.id === data.recordId)
  if (!record || !activeTableId.value) return
  const selectField = fields.value.find((f) => f.id === data.fieldId)
  if (!selectField) {
    ElMessage.warning('未找到可分组字段')
    return
  }
  try {
    const updateData: CellUpdateDTO = {
      version: record.version,
      valueText: data.toGroup === '__ungrouped__' ? '' : data.toGroup,
    }
    await updateCell(data.recordId, selectField.id, updateData)
    ElMessage.success('卡片已移动')
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '移动卡片失败'))
  }
}

function handleOpenComments() {
  if (!records.value.length) {
    ElMessage.warning('暂无记录，请先添加数据')
    return
  }
  commentRecordId.value = records.value[0].id
  showCommentPanel.value = true
}

function handleImported() {
  if (activeTableId.value) {
    loadRecords(activeTableId.value)
  }
}

function handleRefresh() {
  window.location.reload()
}

watch(showImportExport, (val) => {
  if (!val) return
  showImport.value = true
})
</script>

<style scoped lang="scss">
.bitable-editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-background);
}

.editor-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 8px 16px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
  flex-shrink: 0;

  .editor-title {
    font-size: var(--font-size-lg);
    font-weight: var(--font-weight-semibold);
    color: var(--color-text-primary);
  }
}

.editor-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.editor-sidebar {
  width: 240px;
  border-right: 1px solid var(--color-border);
  flex-shrink: 0;
  overflow: hidden;
  display: flex;
  transition: width 0.25s ease;

  &--collapsed {
    width: 28px;
  }

  .editor-sidebar__inner {
    flex: 1;
    overflow: hidden;
    min-width: 0;
  }

  .editor-sidebar__toggle {
    width: 28px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    border-left: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-text-secondary);
    font-size: 14px;
    transition: background-color 0.2s, color 0.2s;

    &:hover {
      background: var(--color-surface-alt);
      color: var(--color-accent);
    }
  }

  &--collapsed .editor-sidebar__inner {
    display: none;
  }
}

.editor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.option-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.field-config-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.field-list {
  flex-shrink: 0;
  max-height: 300px;
  overflow-y: auto;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 8px;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover {
    background: var(--color-surface-alt);
  }

  &--active {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  .field-item__info {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .field-item__name {
    font-size: 14px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .field-item__type-tag {
    flex-shrink: 0;
  }

  .field-item__actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }
}

.field-editor {
  flex: 1;
  overflow-y: auto;
  padding-top: 16px;

  .field-editor__title {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 16px;
    color: var(--color-text-primary);
  }

  .field-editor__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 20px;
    padding-top: 12px;
    border-top: 1px solid var(--color-border);
  }
}

.option-color-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-block;
}
</style>
