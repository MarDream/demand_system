<template>
  <div class="bitable-editor">
    <!-- 顶部面包屑 + 协作者 -->
    <div class="editor-header">
      <el-button v-if="!embedded" link class="editor-header__back" @click="router.push('/bitable')">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div class="editor-header__divider" />
      <div class="editor-header__breadcrumbs">
        <button type="button" class="editor-header__crumb is-clickable" @click="handleBackToBaseList">
          多维表格
        </button>
        <el-icon class="editor-header__sep"><ArrowRight /></el-icon>
        <button
          type="button"
          class="editor-header__crumb is-clickable"
          :class="{ 'editor-header__crumb--active': !activeTable }"
          :title="activeTable ? '回到该多维表格的默认数据表' : ''"
          @click="handleBackToBaseRoot"
        >
          {{ base?.name || '加载中…' }}
        </button>
        <template v-if="activeTable">
          <el-icon class="editor-header__sep"><ArrowRight /></el-icon>
          <span class="editor-header__crumb editor-header__crumb--active" :title="activeTable.name">
            {{ activeTable.name }}
          </span>
        </template>
        <el-tag v-if="activeView" size="small" round class="editor-header__view-tag">
          <el-icon class="mr-4"><component :is="getViewIcon(activeView.viewType)" /></el-icon>
          {{ activeView.name }}
        </el-tag>
      </div>
      <div class="editor-header__spacer" />
      <!-- 高级权限入口（原工具栏迁移至此） -->
      <el-button
        size="small"
        class="editor-header__permission"
        @click="showPermissionDialog = true"
      >
        <el-icon><Lock /></el-icon> 高级权限
      </el-button>
      <div
        v-if="collaboratorCount > 0"
        class="editor-header__collaborators"
        :title="`${collaboratorCount} 位协作者在线`"
      >
        <el-popover
          v-for="(c, idx) in visibleCollaborators"
          :key="c.id"
          trigger="click"
          placement="bottom"
          :width="180"
        >
          <template #reference>
            <button
              type="button"
              class="editor-header__avatar-button"
              :aria-label="`查看协作者：${c.name}`"
              :style="{ marginLeft: idx === 0 ? 0 : '-8px', zIndex: visibleCollaborators.length - idx }"
            >
              <el-avatar
                :size="28"
                :src="c.avatar || undefined"
                :style="{ background: c.color }"
                class="editor-header__avatar"
              >
                {{ c.initial }}
              </el-avatar>
            </button>
          </template>
          <div class="collaborator-card">
            <el-avatar :size="36" :src="c.avatar || undefined" :style="{ background: c.color }">
              {{ c.initial }}
            </el-avatar>
            <div>
              <strong>{{ c.name }}</strong>
              <span>正在协作</span>
            </div>
          </div>
        </el-popover>

        <el-popover v-if="remainingCollaborators.length" trigger="click" placement="bottom" :width="200">
          <template #reference>
            <button type="button" class="editor-header__avatar-more">
              +{{ remainingCollaborators.length }}
            </button>
          </template>
          <div class="collaborator-list">
            <div v-for="c in remainingCollaborators" :key="c.id" class="collaborator-card">
              <el-avatar :size="30" :src="c.avatar || undefined" :style="{ background: c.color }">
                {{ c.initial }}
              </el-avatar>
              <div>
                <strong>{{ c.name }}</strong>
                <span>正在协作</span>
              </div>
            </div>
          </div>
        </el-popover>
      </div>
    </div>

    <div class="editor-body">
      <!-- 主编辑区域 -->
      <div class="editor-main">
        <Toolbar
          :table="activeTable"
          :views="views"
          :activeViewId="activeViewId"
          :filterCount="filterCount"
          :groupFieldId="groupFieldId"
          :fields="visibleFields"
          :rowHeight="activeTable?.rowHeight ?? null"
          :sortState="activeSortState"
          @add-row="handleRowInsert()"
          @sort-field="handleSortField"
          @sort-clear="handleSortClear"
          @row-height-change="handleRowHeightChange"
          @row-height-custom="openRowHeightCustom"
          @open-field-config="fieldConfigDrawerVisible = true"
          @open-filter="showFilterPanel = true"
          @open-group="showGroupPanel = true"
          @view-switch="handleViewSwitch"
          @create-view="handleCreateView"
          @rename-view="handleRenameView"
          @duplicate-view="handleDuplicateView"
          @set-default-view="handleSetDefaultView"
          @delete-view="handleDeleteView"
          @open-comments="handleOpenComments"
          @open-operations="showOperationHistory = true"
          @open-ai-panel="showAiPanel = true"
          @open-import-export="showImportExport = true"
          @open-export="showExport = true"
          @open-share-view="showShareViewDialog = true"
          @open-form-publish="showFormPublishDialog = true"
          @open-integration="showIntegrationDialog = true"
          @open-ai-fill="showAiFillDialog = true"
          @open-ai-classify="showAiClassifyDialog = true"
          @open-ai-summarize="showAiSummarizeDialog = true"
          @open-ai-build-table="showAiBuildTableDialog = true"
        />
        <GridView
          v-if="currentViewType === 'grid'"
          ref="gridViewRef"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          :seqStartIndex="(recordsPage - 1) * recordsPageSize"
          @cell-change="handleCellChange"
          @row-insert="handleRowInsert"
          @row-delete="handleRowDelete"
          @row-copy="handleRowCopy"
          @rows-delete="handleRowsDelete"
          @selection-change="(rowIds: number[]) => (selectedRecordIds = rowIds)"
          @rename-field="(fieldId: number) => handleRenameField(fieldId)"
          @edit-field="handleQuickEditField"
          @clone-field="handleCloneField"
          @hide-field="handleHideField"
          @delete-field="handleGridDeleteField"
          @insert-field="handleInsertField"
          @sort-field="handleSortField"
          @fill-column-color="handleFillColumnColor"
          @freeze-to-left="handleFreezeToLeft"
          @group-by-field="handleMenuGroupByField"
          @filter-by-field="handleMenuFilterByField"
          @set-remind="handleSetRemind"
          @col-resize="handleColResize"
          @add-field="handleAddField"
          @add-quick-field="handleAddQuickField"
          @row-height-change="handleRowHeightChange"
          @header-dragend="handleHeaderDragend"
          @ai-fill-column="handleAiFillColumn"
          @ai-classify-column="handleAiClassifyColumn"
          @ai-summarize-column="handleAiSummarizeColumn"
          @convert-to-ai-field="handleConvertToAiField"
        />
        <KanbanView
          v-else-if="currentViewType === 'kanban'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          @record-update="handleKanbanRecordUpdate"
          @card-move="handleCardMove"
          @row-insert="handleRowInsert"
        />
        <GanttView
          v-else-if="currentViewType === 'gantt'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          @field-change="handleGanttFieldChange"
        />
        <CalendarView
          v-else-if="currentViewType === 'calendar'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          @record-click="handleOpenRecordComments"
          @field-change="handleCalendarFieldChange"
        />
        <GalleryView
          v-else-if="currentViewType === 'gallery'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          @card-click="handleOpenRecordComments"
        />
        <FormView
          v-else-if="currentViewType === 'form'"
          ref="formViewRef"
          :table="activeTable"
          :fields="visibleFields"
          :loading="savingField"
          :viewConfig="activeView?.config ?? null"
          @submit="handleFormSubmit"
        />

        <!-- 记录分页 -->
        <div v-if="currentViewType !== 'form' && recordsTotal > recordsPageSize" class="records-pager">
          <el-pagination
            layout="total, prev, pager, next"
            :total="recordsTotal"
            :page-size="recordsPageSize"
            :current-page="recordsPage"
            @current-change="handlePageChange"
          />
        </div>
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
            :class="{ 'field-item--active': editingFieldId === field.id, 'field-item--hidden': isFieldHiddenInView(field.id) }"
            @click="selectFieldForEdit(field)"
          >
            <div class="field-item__info">
              <span class="field-item__name">{{ field.name }}</span>
              <el-tag size="small" type="info" class="field-item__type-tag">{{ fieldTypeLabel(field.fieldType) }}</el-tag>
            </div>
            <div class="field-item__actions">
              <el-button
                link
                size="small"
                :type="isFieldHiddenInView(field.id) ? 'warning' : 'info'"
                :title="isFieldHiddenInView(field.id) ? '当前视图中隐藏，点击恢复显示' : '在当前视图中隐藏此字段'"
                @click.stop="handleHideField(field.id)"
              >
                <el-icon><View /></el-icon>
              </el-button>
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
          <FieldAttributeForm
            :field-type="editingField.fieldType"
            :config="editConfig"
            :base="editBase"
            :fields="fields"
            :tables="tables"
            :active-table-id="activeTableId"
            :editing-field-id="editingFieldId"
          >

            <template #formula-extra>
              <el-button size="small" @click="openFormulaEditorForConfig">
                <el-icon><MagicStick /></el-icon> 公式编辑器
              </el-button>
            </template>
          </FieldAttributeForm>

          <div class="field-editor__actions">
            <el-button size="small" @click="cancelFieldEdit">取消</el-button>
            <el-button size="small" type="primary" @click="saveFieldConfig" :loading="savingFieldConfig">保存配置</el-button>
          </div>
        </div>
        <el-empty v-else-if="fields.length" description="请选择要编辑的字段" />
      </div>
    </el-drawer>
    <el-dialog v-model="addFieldDialogVisible" title="添加字段" width="560px">
      <FieldAttributeForm
        v-model:field-type="addFieldType"
        :config="addFieldConfig"
        :base="addFieldBase"
        :fields="fields"
        :tables="tables"
        :active-table-id="activeTableId"
        show-type-selector
        :type-groups="FIELD_TYPE_GROUPS"
      />
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

    <!-- 操作记录 -->
    <OperationHistoryPanel
      :visible="showOperationHistory"
      :base-id="baseId"
      :table-id="activeTableId"
      :fields="fields"
      @close="showOperationHistory = false"
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

    <!-- AI 智能填充 -->
    <AiFillDialog
      :visible="showAiFillDialog"
      :table-id="activeTableId ?? 0"
      :record-id="selectedRecordId"
      :fields="fields"
      @close="showAiFillDialog = false"
      @updated="handleAiUpdated"
    />

    <!-- AI 自动分类 -->
    <AiClassifyDialog
      :visible="showAiClassifyDialog"
      :table-id="activeTableId ?? 0"
      :fields="fields"
      :record-count="records.length"
      @close="showAiClassifyDialog = false"
      @completed="handleAiUpdated"
    />

    <!-- AI 自动摘要 -->
    <AiSummarizeDialog
      :visible="showAiSummarizeDialog"
      :table-id="activeTableId ?? 0"
      :fields="fields"
      @close="showAiSummarizeDialog = false"
      @completed="handleAiUpdated"
    />

    <!-- AI 智能建表 -->
    <AiBuildTableDialog
      :visible="showAiBuildTableDialog"
      :base-id="baseId"
      @close="showAiBuildTableDialog = false"
      @created="handleAiTableCreated"
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

    <!-- 记录编辑（看板卡片编辑等入口） -->
    <RecordEditDialog
      :visible="recordEditVisible"
      :record="recordEditTarget"
      :fields="fields"
      @close="recordEditVisible = false"
      @save="handleRecordEditSave"
    />

    <!-- 分享视图 / 发布表单 -->
    <ShareViewDialog
      :visible="showShareViewDialog"
      :view-id="activeViewId"
      @close="showShareViewDialog = false"
    />
    <FormPublishDialog
      :visible="showFormPublishDialog"
      :table-id="activeTableId"
      :view-id="activeViewId"
      @close="showFormPublishDialog = false"
    />

    <!-- API 与 Webhook 集成管理 -->
    <IntegrationDialog
      :visible="showIntegrationDialog"
      :base-id="baseId"
      @close="showIntegrationDialog = false"
    />

    <!-- 权限管理 -->
    <PermissionManageDialog
      v-model="showPermissionDialog"
      :base-id="baseId"
      :tables="tables"
      :table-groups="tableGroups"
    />

    <!-- 筛选面板 -->
    <FilterPanel
      v-if="activeTableId != null"
      v-model="showFilterPanel"
      :fields="fields"
      :filter-config="filterConfig"
      :preset-field-id="presetFilterFieldId"
      :table-id="activeTableId"
      @apply="handleFilterApply"
    />

    <!-- 分组面板 -->
    <GroupPanel
      v-if="activeTableId != null"
      v-model="showGroupPanel"
      :fields="fields"
      :groupFieldId="groupFieldId"
      @apply="handleGroupApply"
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

    <!-- 编辑字段弹层（钉钉风格：可改字段名 / 字段类型 / 类型属性 / 描述） -->
    <FieldQuickEditDialog
      :visible="quickEditVisible"
      :field="quickEditField"
      :fields="fields"
      :tables="tables"
      :active-table-id="activeTableId"
      :saving="quickEditSaving"
      @close="quickEditVisible = false"
      @confirm="handleQuickEditConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { resolveAvatarUrl } from '@/utils/presetAvatars'
import { updateTable } from '@/api/modules/bitable'
import { ArrowLeft, Delete, Edit, CopyDocument, ArrowRight, MagicStick, Grid, Menu, Calendar, Picture, Tickets, View, Lock } from '@element-plus/icons-vue'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores'
import Toolbar from './components/Toolbar.vue'
import GridView from './components/GridView.vue'
import KanbanView from './components/KanbanView.vue'
import GanttView from './components/GanttView.vue'
import CalendarView from './components/CalendarView.vue'
import GalleryView from './components/GalleryView.vue'
import FormView from './components/FormView.vue'
import CommentPanel from './components/CommentPanel.vue'
import OperationHistoryPanel from './components/OperationHistoryPanel.vue'
import FilterPanel from './components/FilterPanel.vue'
import GroupPanel from './components/GroupPanel.vue'
import ConflictDialog from './components/ConflictDialog.vue'
import AiChatPanel from './components/AiChatPanel.vue'
import AiFillDialog from './components/AiFillDialog.vue'
import AiClassifyDialog from './components/AiClassifyDialog.vue'
import AiSummarizeDialog from './components/AiSummarizeDialog.vue'
import AiBuildTableDialog from './components/AiBuildTableDialog.vue'
import ImportDialog from './components/ImportDialog.vue'
import ExportDialog from './components/ExportDialog.vue'
import RecordEditDialog from './components/RecordEditDialog.vue'
import FormPublishDialog from './components/FormPublishDialog.vue'
import ShareViewDialog from './components/ShareViewDialog.vue'
import IntegrationDialog from './components/IntegrationDialog.vue'
import PermissionManageDialog from './components/PermissionManageDialog.vue'
import LinkFieldSelector from './components/LinkFieldSelector.vue'
import FormulaEditor from './components/FormulaEditor.vue'
import FieldAttributeForm from './components/FieldAttributeForm.vue'
import FieldQuickEditDialog from './components/FieldQuickEditDialog.vue'
import { useBitableWebSocket, type CellUpdateEvent, type ConflictEvent, type RecordCreatedEvent, type RecordDeletedEvent } from '@/composables/useBitableWebSocket'
import { createDefaultFieldConfig, LINK_FIELD_TYPES, normalizeFieldConfig, sanitizeFieldConfig } from '@/utils/bitableFieldConfig'
import {
  getBase,
  listTables,
  listFields,
  createField,
  updateField,
  deleteField,
  sortFields,
  listRecords,
  queryRecords,
  createRecord,
  deleteRecord,
  updateCell,
  linkRecords,
  getLinkedRecordIds,
  listViews,
  createView,
  updateView,
  deleteView,
  duplicateView,
  setDefaultView,
  listTableGroups,
} from '@/api/modules/bitable'
import type {
  BitableBase,
  BitableTable,
  BitableTableGroup,
  BitableField,
  BitableRecord,
  BitableView,
  BitableFieldCreateDTO,
  BitableRecordCreateDTO,
  CellUpdateDTO,
  ViewType,
  ViewConfig,
  FilterGroup,
  FilterItem,
  FieldConfig,
  SortItem,
} from '@/types/bitable'

/** 字段类型下拉分组（新增字段与字段配置共用） */
const FIELD_TYPE_GROUPS: { label: string; options: { label: string; value: string }[] }[] = [
  {
    label: '常规',
    options: [
      { label: '文本', value: 'text' },
      { label: '富文本', value: 'rich_text' },
      { label: '数字', value: 'number' },
      { label: '日期', value: 'date' },
      { label: '单选', value: 'single_select' },
      { label: '多选', value: 'multi_select' },
      { label: '人员', value: 'user' },
      { label: '群组', value: 'group' },
      { label: '部门', value: 'department' },
      { label: '复选框', value: 'checkbox' },
      { label: '附件', value: 'attachment' },
      { label: '超链接', value: 'url' },
    ],
  },
  {
    label: '业务',
    options: [
      { label: '流程', value: 'process' },
      { label: '按钮', value: 'button' },
      { label: '自动编号', value: 'auto_number' },
      { label: '电话', value: 'phone' },
      { label: '邮箱', value: 'email' },
      { label: '地理位置', value: 'location' },
      { label: '条码', value: 'barcode' },
      { label: '进度', value: 'progress' },
      { label: '货币', value: 'currency' },
      { label: '评分', value: 'rating' },
    ],
  },
  {
    label: '高级',
    options: [
      { label: '单向关联', value: 'link' },
      { label: '双向关联', value: 'bidirectional_link' },
      { label: '汇总', value: 'rollup' },
      { label: '查找引用', value: 'lookup' },
      { label: '公式', value: 'formula' },
      { label: '创建人', value: 'created_by' },
      { label: '修改人', value: 'modified_by' },
      { label: '创建时间', value: 'created_time' },
      { label: '最后更新时间', value: 'last_modified_time' },
    ],
  },
  {
    label: 'AI 捷径',
    options: [
      { label: 'AI 文本', value: 'ai_text' },
      { label: 'AI 选择', value: 'ai_select' },
    ],
  },
]

const route = useRoute()
const router = useRouter()
const toast = useToast()

// 允许被外层工作台以 props 内嵌（baseId/tableId 传入时优先于路由）
const props = defineProps<{ baseId?: number; tableId?: number; embedded?: boolean }>()
const baseId = props.baseId ?? Number(route.params.baseId)
const base = ref<BitableBase | null>(null)
const tables = ref<BitableTable[]>([])
const tableGroups = ref<BitableTableGroup[]>([])
const activeTableId = ref<number | null>(null)
const fields = ref<BitableField[]>([])
const records = ref<BitableRecord[]>([])
const views = ref<BitableView[]>([])
const loadingRecords = ref(false)
const savingField = ref(false)
const activeViewId = ref<number | null>(null)
const activeView = computed<BitableView | null>(() => {
  if (!activeViewId.value) return null
  return views.value.find(v => v.id === activeViewId.value) ?? null
})
const currentViewType = computed<ViewType>(() => activeView.value?.viewType ?? 'grid')

// 筛选状态（阶段一：字段筛选）
const showFilterPanel = ref(false)
const filterConfig = ref<FilterGroup | FilterItem[] | null>(null)
const filterCount = computed(() => filterRuleCount(filterConfig.value))
/** 右键「按字段筛选」预置的字段：FilterPanel 打开时预置一条该字段的条件 */
const presetFilterFieldId = ref<number | null>(null)

// 分组状态
const showGroupPanel = ref(false)
const groupFieldId = computed<number | null>(() => {
  const config = activeView.value?.groupConfig
  if (!config || !Array.isArray(config)) return null
  const first = config[0] as { fieldId?: number }
  return first?.fieldId ?? null
})
const renameInputRef = ref<any>(null)
const showCommentPanel = ref(false)
const showOperationHistory = ref(false)
const showAiPanel = ref(false)
const showAiFillDialog = ref(false)
const showAiClassifyDialog = ref(false)
const showAiSummarizeDialog = ref(false)
const showAiBuildTableDialog = ref(false)
const selectedRecordId = ref<number | undefined>(undefined)
const showImport = ref(false)
const showExport = ref(false)
const showImportExport = ref(false)
const commentRecordId = ref<number | null>(null)

// 看板/记录编辑对话框
const recordEditVisible = ref(false)
const recordEditTarget = ref<BitableRecord | null>(null)

// 分享视图 / 发布表单 / 集成管理 / 权限管理
const showShareViewDialog = ref(false)
const showFormPublishDialog = ref(false)
const showIntegrationDialog = ref(false)
const showPermissionDialog = ref(false)

// 记录分页状态
const recordsPage = ref(1)
const recordsPageSize = ref(50)
const recordsTotal = ref(0)

const linkSelectorVisible = ref(false)
const currentLinkField = ref<BitableField | null>(null)
const currentLinkRecordId = ref<number | null>(null)
const currentLinkSelectedIds = ref<number[]>([])

const renameFieldDialogVisible = ref(false)
const renameFieldId = ref<number | null>(null)
const renameFieldName = ref('')

// 编辑字段快速弹层（表头右键 → 编辑字段）
const quickEditVisible = ref(false)
const quickEditField = ref<BitableField | null>(null)
const quickEditSaving = ref(false)

const formulaEditorVisible = ref(false)
const formulaExpr = ref('')
const formViewRef = ref<InstanceType<typeof FormView> | null>(null)
/** 网格视图实例引用：新增记录后滚动定位到新行用 */
const gridViewRef = ref<InstanceType<typeof GridView> | null>(null)

// 字段配置弹窗
const fieldConfigDrawerVisible = ref(false)
const editingFieldId = ref<number | null>(null)
const savingFieldConfig = ref(false)
const editingField = computed<BitableField | null>(() => {
  if (editingFieldId.value === null) return null
  return fields.value.find((f) => f.id === editingFieldId.value) ?? null
})

/**
 * 字段属性表单模型。
 * - `base` 承载后端列级字段（name/description/width/required/aiPrompt）
 * - `config` 承载 JSON 配置（各类型属性 + 唯一/表单隐藏等），键位见 `utils/bitableFieldConfig`
 * 两个对象都由本组件持有，属性面板就地修改，保存时读取同一对象。
 */
interface FieldBaseForm {
  name: string
  description: string
  width: number
  required: boolean
  aiPrompt: string
}

function createBaseForm(): FieldBaseForm {
  return { name: '', description: '', width: 200, required: false, aiPrompt: '' }
}

/** 字段配置抽屉：当前编辑字段的属性模型 */
const editBase = ref<FieldBaseForm>(createBaseForm())
const editConfig = ref<FieldConfig>({})

/** 新增字段弹窗：字段类型 + 属性模型 */
const addFieldType = ref<string>('text')
const addFieldBase = ref<FieldBaseForm>(createBaseForm())
const addFieldConfig = ref<FieldConfig>(createDefaultFieldConfig('text'))

// 字段类型中文标签
const fieldTypeLabelMap: Record<string, string> = {
  text: '文本',
  rich_text: '富文本',
  number: '数字',
  date: '日期',
  single_select: '单选',
  multi_select: '多选',
  user: '人员',
  group: '群组',
  department: '部门',
  check: '复选框',
  checkbox: '复选框',
  attachment: '附件',
  url: '链接',
  email: '邮箱',
  phone: '电话',
  location: '地理位置',
  barcode: '条码',
  currency: '货币',
  process: '流程',
  button: '按钮',
  progress: '进度',
  rating: '评分',
  link: '单向关联',
  bidirectional_link: '双向关联',
  rollup: '汇总',
  lookup: '查找引用',
  formula: '公式',
  ai_text: 'AI文本',
  ai_select: 'AI选择',
  auto_number: '自动编号',
  created_time: '创建时间',
  modified_time: '修改时间',
  created_user: '创建人',
  modified_user: '修改人',
  created_by: '创建人',
  modified_by: '修改人',
  last_modified_time: '最后更新时间',
  date_range: '日期范围',
}

function normalizeField(field: BitableField): BitableField {
  if (typeof field.config === 'string') {
    try {
      field.config = JSON.parse(field.config)
    } catch {
      field.config = undefined
    }
  }
  // 统一迁移到规范键：历史字段可能存的是 format/digits/allowedFileTypes 等旧键，
  // 各视图（网格/看板/画廊/日历）都直接读 field.config，所以在此一次性归一。
  field.config = normalizeFieldConfig(field.fieldType, field.config)
  return field
}

function fieldTypeLabel(type: string): string {
  return fieldTypeLabelMap[type] || type
}

function isLinkField(type?: string) {
  return LINK_FIELD_TYPES.has(type || '')
}

// WebSocket 实时协作
const userStore = useUserStore()
const currentUserId = computed(() => userStore.userInfo?.id)
const {
  connect: wsConnect,
  onlineUsers,
  onCellUpdated,
  onConflict,
  onRecordCreated,
  onRecordDeleted,
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

// 其他成员删除记录时，实时移除本端对应行
onRecordDeleted.value = (event: RecordDeletedEvent) => {
  if (event.userId === currentUserId.value) return
  if (event.tableId !== activeTableId.value) return
  records.value = records.value.filter((r) => r.id !== event.recordId)
}

// 其他成员新增记录时，重载以获取完整行数据
onRecordCreated.value = (event: RecordCreatedEvent) => {
  if (event.userId === currentUserId.value) return
  if (event.tableId !== activeTableId.value) return
  if (activeTableId.value) {
    loadRecords(activeTableId.value)
  }
}

onConflict.value = (event: ConflictEvent) => {
  conflictMessage.value = event.message
  conflictVisible.value = true
}

const addFieldDialogVisible = ref(false)

const activeTable = computed<BitableTable | null>(() => {
  if (!activeTableId.value) return null
  return tables.value.find((t) => t.id === activeTableId.value) ?? null
})

const visibleFields = computed<BitableField[]>(() => {
  const config = activeView.value?.config as ViewConfig | undefined
  if (!config) return fields.value

  const hiddenIds = new Set(config.hiddenFieldIds ?? [])
  let result = fields.value.filter(f => !hiddenIds.has(f.id))

  if (config.columnOrder && config.columnOrder.length > 0) {
    const orderMap = new Map(config.columnOrder.map((id, idx) => [id, idx]))
    result = result.sort((a, b) => {
      const oa = orderMap.get(a.id) ?? Number.MAX_SAFE_INTEGER
      const ob = orderMap.get(b.id) ?? Number.MAX_SAFE_INTEGER
      return oa - ob
    })
  }

  return result
})

onMounted(async () => {
  await loadBase()
  await loadTableGroups()
  await loadTables()
  const requestedTableId = props.tableId ?? Number(route.query.tableId)
  if (requestedTableId && tables.value.some((t) => t.id === requestedTableId)) {
    handleSelectTable(requestedTableId)
  } else if (tables.value.length > 0) {
    handleSelectTable(tables.value[0].id)
  }
  wsConnect()
})

async function loadBase() {
  try {
    base.value = await getBase(baseId)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载多维表格失败'))
  }
}

async function loadTables() {
  try {
    const res = await listTables(baseId)
    tables.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载数据表失败'))
  }
}

async function loadTableGroups() {
  try {
    const res = await listTableGroups(baseId)
    tableGroups.value = Array.isArray(res) ? res : (res as any).data || []
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载分组失败'))
  }
}

async function handleSelectTable(tableId: number) {
  activeTableId.value = tableId
  await loadViews(tableId)
  await loadFields(tableId)
  setActiveView()
  await loadRecords(tableId)
}

/** 面包屑「多维表格」：回退到多维表格列表页 */
function handleBackToBaseList() {
  router.push('/bitable')
}

/**
 * 面包屑「{多维表格名称}」：回退到该多维表格的默认数据表 + 默认视图。
 * 先清掉 URL 上的 viewId，setActiveView 才会回落到数据表的 defaultViewId，
 * 否则会沿用上一个数据表带过来的视图 ID。
 */
async function handleBackToBaseRoot() {
  const first = tables.value[0]
  if (!first) return
  const rest = { ...route.query }
  delete rest.viewId
  await router.replace({ query: rest })
  await handleSelectTable(first.id)
}

async function loadViews(tableId: number) {
  try {
    const res = await listViews(tableId)
    views.value = Array.isArray(res) ? res : []
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载视图失败'))
  }
}

function setActiveView() {
  const urlViewId = route.query.viewId ? Number(route.query.viewId) : null
  const table = tables.value.find(t => t.id === activeTableId.value)

  if (urlViewId && views.value.some(v => v.id === urlViewId)) {
    activeViewId.value = urlViewId
  } else if (table?.defaultViewId && views.value.some(v => v.id === table.defaultViewId)) {
    activeViewId.value = table.defaultViewId
  } else if (views.value.length > 0) {
    activeViewId.value = views.value[0].id
  } else {
    activeViewId.value = null
  }

  if (activeViewId.value) {
    router.replace({ query: { ...route.query, viewId: String(activeViewId.value) } })
  }
}

async function loadFields(tableId: number) {
  try {
    const res = await listFields(tableId)
    const rawFields = Array.isArray(res) ? res : (res as any).data || []
    fields.value = rawFields.map(normalizeField)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载字段失败'))
  }
}

// 关闭权限弹框后重新拉取字段与记录：
// 字段级权限会改变 fields[].permission（隐藏/只读），也影响后端是否下发隐藏字段的单元格值。
watch(showPermissionDialog, async (open, wasOpen) => {
  if (open || !wasOpen || !activeTableId.value) return
  await loadFields(activeTableId.value)
  await loadRecords(activeTableId.value)
})

function filterRuleCount(config: FilterGroup | FilterItem[] | null): number {
  if (!config) return 0
  const rules = Array.isArray(config) ? config : config.rules
  return rules.reduce((count, rule) => {
    if ('rules' in rule) return count + filterRuleCount(rule)
    return count + 1
  }, 0)
}

async function handleFilterApply(config: FilterGroup | null) {
  filterConfig.value = config
  const view = activeView.value
  if (!view || !activeTableId.value) return

  try {
    await updateView(view.id, {
      filterConfig: config ? (config as FilterGroup) : [],
      version: view.version,
    })
    toast.success(config ? `筛选已应用（${filterRuleCount(config)} 条条件）` : '筛选已清空')
    await loadViews(activeTableId.value)
    await loadRecords(activeTableId.value)
  } catch (e: unknown) {
    toast.error(resolveErrorMessage(e, '保存筛选配置失败'))
  }
}

async function handleGroupApply(fieldId: number | null) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const groupConfig = fieldId != null ? [{ fieldId }] : []
  try {
    await updateView(view.id, { groupConfig, version: view.version })
    toast.success(fieldId ? '分组已设置' : '分组已清除')
    await loadViews(activeTableId.value)
    await loadRecords(activeTableId.value)
  } catch (e: unknown) {
    toast.error(resolveErrorMessage(e, '保存分组配置失败'))
  }
}

async function loadRecords(tableId: number, page = 1) {
  loadingRecords.value = true
  recordsPage.value = page
  try {
    const view = activeView.value
    const currentFilter = view?.filterConfig ?? null
    filterConfig.value = currentFilter
    const hasFilter = currentFilter && filterRuleCount(currentFilter) > 0
    // 分组生效：分组字段作为第一排序键，让同组记录相邻展示
    const currentGroup = groupFieldId.value
    const sortConfig = view?.sortConfig?.length
      ? [...view.sortConfig]
      : currentGroup != null
        ? [{ fieldId: currentGroup, direction: 'asc' as const }]
        : undefined
    if (view && (hasFilter || sortConfig)) {
      // 视图有筛选/排序/分组配置时，走高级查询接口
      const res = await queryRecords(tableId, {
        filterConfig: currentFilter ?? undefined,
        sortConfig,
        groupByFieldId: currentGroup ?? undefined,
        viewId: view.id,
        pageNum: page,
        pageSize: recordsPageSize.value,
      })
      records.value = res.list || []
      recordsTotal.value = res.total
    } else {
      const res = await listRecords(tableId, { pageNum: page, pageSize: recordsPageSize.value })
      records.value = res.list || []
      recordsTotal.value = res.total
    }
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载记录失败'))
  } finally {
    loadingRecords.value = false
  }
}

async function handlePageChange(page: number) {
  if (!activeTableId.value) return
  await loadRecords(activeTableId.value, page)
}

function handleAddField() {
  addFieldType.value = 'text'
  addFieldBase.value = createBaseForm()
  addFieldConfig.value = createDefaultFieldConfig('text')
  addFieldDialogVisible.value = true
}

/**
 * 空表快捷创建字段（走查 P1-4）：以默认配置直接建列，免开弹窗。
 * 若同名字段已存在则打开「添加字段」弹窗让用户改名。
 */
async function handleAddQuickField(data: { fieldType: string; name: string }) {
  if (!activeTableId.value) return
  if (fields.value.some((f) => f.name === data.name)) {
    toast.warning(`字段「${data.name}」已存在，请在弹窗中换一个名称`)
    handleAddField()
    return
  }
  try {
    await createField(activeTableId.value, {
      name: data.name,
      fieldType: data.fieldType as BitableFieldCreateDTO['fieldType'],
      width: 200,
      required: 0,
      config: createDefaultFieldConfig(data.fieldType),
    })
    toast.success(`已添加「${data.name}」字段`)
    await loadFields(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '添加失败'))
  }
}

async function submitAddField() {
  const name = addFieldBase.value.name.trim()
  if (!name) {
    toast.warning('请输入字段名称')
    return
  }
  if (!activeTableId.value) {
    toast.warning('请先选择数据表')
    return
  }
  // 字段名同表内不可重名（后端也会兜底校验，这里先给出即时反馈）
  if (fields.value.some((f) => f.name === name)) {
    toast.warning(`字段「${name}」已存在`)
    return
  }
  savingField.value = true
  try {
    const config = sanitizeFieldConfig(addFieldType.value, addFieldConfig.value)
    const data: BitableFieldCreateDTO = {
      name,
      fieldType: addFieldType.value as BitableFieldCreateDTO['fieldType'],
      description: addFieldBase.value.description || undefined,
      width: addFieldBase.value.width,
      // 后端 required 为 Integer(tinyint)，传 0/1
      required: addFieldBase.value.required ? 1 : 0,
      config,
    }
    if (addFieldType.value === 'ai_text' || addFieldType.value === 'ai_select') {
      data.isAiField = 1
      data.aiPrompt = addFieldBase.value.aiPrompt
    }
    await createField(activeTableId.value, data)
    // 走查 P2-3：新列在表格中即时可见，不再弹顶部 toast（遮挡工具栏）
    await loadFields(activeTableId.value)
    addFieldDialogVisible.value = false
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '添加失败'))
  } finally {
    savingField.value = false
  }
}

function handleViewSwitch(viewId: number) {
  activeViewId.value = viewId
  router.replace({ query: { ...route.query, viewId: String(viewId) } })
  if (activeTableId.value) {
    loadRecords(activeTableId.value)
  }
}

function getViewTypeName(type: ViewType): string {
  const map: Record<ViewType, string> = {
    grid: '表格',
    kanban: '看板',
    gantt: '甘特',
    calendar: '日历',
    gallery: '画廊',
    form: '表单',
  }
  return map[type] || type
}

// 视图类型图标映射（用于顶部 view-tag）
const viewTypeIconMap: Record<string, any> = {
  grid: Grid,
  kanban: Menu,
  gantt: ArrowRight,
  calendar: Calendar,
  gallery: Picture,
  form: Tickets,
}
function getViewIcon(type: string) {
  return viewTypeIconMap[type] || Grid
}

// 顶部真实在线协作者，由 WebSocket presence_updated 消息维护。
// 后端 presence 里的 avatar 是原始字符串（如 preset:cartoon-9），必须先经
// resolveAvatarUrl 转成可加载的 URL，否则浏览器按字面请求该字符串报 ERR_UNKNOWN_URL_SCHEME。
const collaboratorColors = ['#6366F1', '#10B981', '#F59E0B', '#EC4899', '#8B5CF6', '#06B6D4']
const collaborators = computed(() => onlineUsers.value.map((user) => ({
  ...user,
  avatar: resolveAvatarUrl(user.avatar),
  initial: Array.from(user.name?.trim() || '?')[0]?.toUpperCase() || '?',
  color: collaboratorColors[Math.abs(user.id - 1) % collaboratorColors.length],
})))
const visibleCollaborators = computed(() => collaborators.value.slice(0, 3))
const remainingCollaborators = computed(() => collaborators.value.slice(3))
const collaboratorCount = computed(() => collaborators.value.length)

async function handleCreateView(viewType: ViewType) {
  if (!activeTableId.value) return
  try {
    const viewName = getViewTypeName(viewType) + '视图'
    const newView = await createView(activeTableId.value, { name: viewName, viewType })
    toast.success('创建成功')
    await loadViews(activeTableId.value)
    const newId = typeof newView === 'object' ? (newView as any).id : Number(newView)
    activeViewId.value = newId
    router.replace({ query: { ...route.query, viewId: String(newId) } })
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '创建视图失败'))
  }
}

async function handleRenameView(viewId: number, name: string) {
  const view = views.value.find(v => v.id === viewId)
  if (!view) return
  try {
    await updateView(viewId, { name, version: view.version })
    toast.success('重命名成功')
    await loadViews(activeTableId.value!)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '重命名失败'))
  }
}

async function handleDuplicateView(viewId: number) {
  try {
    const newId = await duplicateView(viewId)
    toast.success('复制成功')
    await loadViews(activeTableId.value!)
    activeViewId.value = newId
    router.replace({ query: { ...route.query, viewId: String(newId) } })
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '复制视图失败'))
  }
}

async function handleSetDefaultView(tableId: number, viewId: number) {
  try {
    await setDefaultView(tableId, viewId)
    toast.success('已设为默认视图')
    await loadViews(activeTableId.value!)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '设置默认视图失败'))
  }
}

async function handleDeleteView(viewId: number) {
  const view = views.value.find(v => v.id === viewId)
  if (!view) return
  try {
    await ElMessageBox.confirm(`确定删除视图「${view.name}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteView(viewId)
    toast.success('删除成功')
    await loadViews(activeTableId.value!)
    if (activeViewId.value === viewId) {
      setActiveView()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      toast.error(resolveErrorMessage(e, '删除视图失败'))
    }
  }
}

async function handleCellChange(data: { rowId: number; fieldId: number; newValue: unknown }) {
  const record = records.value.find((r) => r.id === data.rowId)
  if (!record) return
  const field = fields.value.find((f) => f.id === data.fieldId)
  if (!field) return

  if (isLinkField(field.fieldType)) {
    currentLinkField.value = field
    currentLinkRecordId.value = data.rowId
    currentLinkSelectedIds.value = await getCurrentLinkedIds(field.id, data.rowId, record)
    linkSelectorVisible.value = true
    return
  }

  try {
    const updateData: any = { version: record.version }
    if (data.newValue && typeof data.newValue === 'object' && !Array.isArray(data.newValue) && 'valueJson' in (data.newValue as Record<string, unknown>)) {
      // 人员字段：GridView 已规整为 {valueJson: [{id,name}], valueText} 整包下发
      const structured = data.newValue as { valueJson?: unknown; valueText?: string }
      updateData.valueJson = structured.valueJson
      updateData.valueText = structured.valueText ?? ''
    } else if (['number', 'currency', 'progress', 'rating'].includes(field.fieldType)) {
      updateData.valueNumber = Number(data.newValue) || 0
    } else if (field.fieldType === 'date') {
      updateData.valueDate = String(data.newValue)
    } else if (field.fieldType === 'check' || field.fieldType === 'checkbox') {
      updateData.valueText = String(Boolean(data.newValue))
    } else if (field.fieldType === 'single_select' || field.fieldType === 'multi_select' || field.fieldType === 'process') {
      updateData.valueText = String(data.newValue ?? '')
    } else if (field.fieldType === 'user') {
      // 人员字段兜底（非网格入口）：valueJson 直接透传
      updateData.valueJson = data.newValue
    } else if (field.fieldType === 'date_range' || field.fieldType === 'attachment' || field.fieldType === 'location' || isLinkField(field.fieldType)) {
      updateData.valueJson = data.newValue
    } else {
      updateData.valueText = String(data.newValue ?? '')
    }
    const newVersion: number = await updateCell(data.rowId, data.fieldId, updateData)
    // 关键修复：就地更新本地单元格值 + 版本号，不再整表 reload。
    // 原因：原逻辑每次保存都 loadRecords() 全量重载 :data；当用户紧接着编辑下一格时，
    // 异步 reload 会在该格编辑进行中替换 :data，vxe-table 在“编辑态 + 数据替换”下会卡死/无限渲染（闪退）。
    // 改为就地写回 cells（与 WS onCellUpdated 的就地修改保持一致），彻底消除该竞态。
    record.version = newVersion
    record.cells = record.cells || {}
    record.cells[data.fieldId] = {
      fieldId: data.fieldId,
      ...(updateData.valueText !== undefined ? { valueText: updateData.valueText } : {}),
      ...(updateData.valueNumber !== undefined ? { valueNumber: updateData.valueNumber } : {}),
      ...(updateData.valueDate !== undefined ? { valueDate: updateData.valueDate } : {}),
      ...(updateData.valueJson !== undefined ? { valueJson: updateData.valueJson } : {}),
    }
    // 静默保存：单元格编辑是高频操作，成功时不弹 toast（失败仍会提示），
    // 单元格即时回显本身就是保存成功的反馈。
    // 不再通过 WS 上行 sendCellUpdate：后端 REST updateCell 写库成功后已统一广播（afterCommit），
    // 否则会触发 WS handler 二次广播/重复操作日志，且原 WS 路径会重复写库导致 version 乐观锁竞态（单用户编辑也 409）。
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '更新失败'))
  }
}

async function getCurrentLinkedIds(fieldId: number, recordId: number, record: BitableRecord) {
  try {
    const ids = await getLinkedRecordIds(fieldId, recordId)
    if (Array.isArray(ids)) return ids
  } catch {
    // 兼容旧数据：接口不可用时回退到当前单元格 valueJson。
  }
  const raw = record.cells?.[fieldId]?.valueJson
  if (Array.isArray(raw)) {
    return raw.map((item) => Number(item)).filter((id) => Number.isFinite(id))
  }
  return []
}

async function handleLinkConfirm(ids: number[]) {
  if (!currentLinkRecordId.value || !currentLinkField.value) return
  try {
    const record = records.value.find((r) => r.id === currentLinkRecordId.value)
    if (!record) return
    await linkRecords(currentLinkField.value.id, {
      recordId: currentLinkRecordId.value,
      targetRecordIds: ids,
    })
    toast.success('关联成功')
    if (activeTableId.value) await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '关联失败'))
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
    toast.warning('字段名称不能为空')
    return
  }
  if (renameFieldId.value === null) return
  savingField.value = true
  updateField(renameFieldId.value, { name })
    .then(() => {
      toast.success('重命名成功')
      renameFieldDialogVisible.value = false
      if (activeTableId.value) loadFields(activeTableId.value)
    })
    .catch((e: any) => {
      toast.error(resolveErrorMessage(e, '重命名失败'))
    })
    .finally(() => {
      savingField.value = false
    })
}

/**
 * 表头右键 →「编辑字段」：打开钉钉风格的快速编辑弹层。
 * 弹层内可改字段名 / 字段类型 / 类型属性 / 描述，确认走 handleQuickEditConfirm。
 */
function handleQuickEditField(fieldId: number) {
  const field = fields.value.find((f) => f.id === fieldId)
  if (!field) return
  quickEditField.value = field
  quickEditVisible.value = true
}

/** 编辑字段弹层确认：统一走 updateField（含 field_type 变更）落库 */
async function handleQuickEditConfirm(data: {
  fieldId: number
  name: string
  fieldType: string
  description: string
  config: FieldConfig
}) {
  quickEditSaving.value = true
  try {
    const payload: Partial<BitableFieldCreateDTO> = {
      name: data.name,
      // 字段类型联合类型断言（弹层里是宽松 string）
      fieldType: data.fieldType as BitableFieldCreateDTO['fieldType'],
      description: data.description,
      config: data.config,
    }
    await updateField(data.fieldId, payload)
    toast.success('字段已更新')
    quickEditVisible.value = false
    if (activeTableId.value) {
      await loadFields(activeTableId.value)
    }
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '保存字段失败'))
  } finally {
    quickEditSaving.value = false
  }
}

/**
 * 右键菜单 → 向左 / 向右插入列。
 * 复制锚点字段的类型与默认配置新开一列（钉钉行为：新列继承锚点列类型），
 * 并按锚点字段在新序插入 sortOrder：左插取锚点序号，右插取锚点序号 +1，
 * 其余字段（含锚点右侧的）整体后移，由后端 sortFields 重排。
 */
async function handleInsertField(data: { position: 'left' | 'right'; fieldId: number }) {
  if (!activeTableId.value) return
  const anchor = fields.value.find((f) => f.id === data.fieldId)
  if (!anchor) return
  savingField.value = true
  try {
    // 深拷贝锚点配置，避免新列与锚点列共享引用
    const config = anchor.config ? JSON.parse(JSON.stringify(anchor.config)) : undefined
    const created = await createField(activeTableId.value, {
      name: `${anchor.name}_新列`,
      fieldType: anchor.fieldType,
      config,
      width: anchor.width,
    })
    // 后端 createField 返回新字段 ID（Result<Long>）；兼容直接返回对象的情况
    const newFieldId = typeof created === 'number' ? created : Number((created as any)?.id ?? created)
    if (!Number.isFinite(newFieldId)) {
      throw new Error('创建字段失败')
    }
    // 计算 sortOrder：createField 默认追加到末尾，这里按插入位置重排
    const orderedIds = [...fields.value]
      .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
      .map((f) => f.id)
    const anchorIdx = orderedIds.indexOf(anchor.id)
    const insertIdx = data.position === 'left' ? anchorIdx : anchorIdx + 1
    orderedIds.splice(insertIdx, 0, newFieldId)
    await sortFields(activeTableId.value, orderedIds)
    toast.success('已插入列')
    await loadFields(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '插入列失败'))
  } finally {
    savingField.value = false
  }
}

/**
 * 右键菜单 → 升序 / 降序：持久化到视图 sortConfig 并重查记录。
 * 再次对同一字段点同一方向 = 取消排序（钉钉行为），sortConfig 清空。
 */
async function handleSortField(data: { fieldId: number; direction: 'asc' | 'desc' }) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const current = Array.isArray(view.sortConfig) ? view.sortConfig : []
  const existing = current.find((item) => item.fieldId === data.fieldId)
  const isToggleOff = existing && existing.direction === data.direction && current.length === 1
  const nextSort: SortItem[] = isToggleOff ? [] : [{ fieldId: data.fieldId, direction: data.direction }]
  try {
    await updateView(view.id, { sortConfig: nextSort, version: view.version })
    toast.success(isToggleOff ? '已取消排序' : data.direction === 'asc' ? '已按升序排序' : '已按降序排序')
    await loadViews(activeTableId.value)
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '排序失败'))
  }
}

/** 工具栏 → 当前视图排序状态（排序弹层预填 + 按钮高亮） */
const activeSortState = computed<{ fieldId: number; direction: 'asc' | 'desc' } | null>(() => {
  const sc = activeView.value?.sortConfig
  if (Array.isArray(sc) && sc.length > 0) {
    return { fieldId: sc[0].fieldId, direction: sc[0].direction }
  }
  return null
})

/** 工具栏 → 清除排序 */
async function handleSortClear() {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  try {
    await updateView(view.id, { sortConfig: [], version: view.version })
    toast.success('已清除排序')
    await loadViews(activeTableId.value)
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '清除排序失败'))
  }
}

/** 工具栏 → 行高「自定义…」：打开 GridView 的自定义行高弹层 */
function openRowHeightCustom() {
  gridViewRef.value?.openRowHeightMenu()
}

/** 整列填色预设色（与 GridView 菜单色点一致） */
const COLUMN_COLOR_HEX: Record<string, string> = {
  gray: '#94a3b8',
  blue: '#3b82f6',
  green: '#22c55e',
  orange: '#f97316',
  purple: '#8b5cf6',
}

/**
 * 右键菜单 → 整列填色：持久化到视图 config.columnColors（fieldId → 色值），
 * 表头背景按此着色；color='none' 表示清除该列填色。
 */
async function handleFillColumnColor(data: { fieldId: number; color: string }) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const config: ViewConfig = { ...(view.config || { schemaVersion: 1 }) }
  const colors: Record<number, string> = { ...(config.columnColors || {}) }
  if (data.color === 'none') {
    delete colors[data.fieldId]
  } else {
    const hex = COLUMN_COLOR_HEX[data.color]
    if (!hex) return
    colors[data.fieldId] = hex
  }
  config.columnColors = colors
  try {
    await updateView(view.id, { config, version: view.version })
    await loadViews(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '设置列颜色失败'))
  }
}

/**
 * 右键菜单 → 冻结至此列：该列及之前的所有列固定在左侧（钉钉行为）。
 * 持久化到视图 config.frozenFieldIds；传空数组表示解冻（后续可加「取消冻结」菜单）。
 */
async function handleFreezeToLeft(fieldId: number) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const config: ViewConfig = { ...(view.config || { schemaVersion: 1 }) }
  // 计算冻结集合：按当前展示顺序，取目标列及其之前的所有字段
  const ordered = visibleFields.value.map((f) => f.id)
  const idx = ordered.indexOf(fieldId)
  if (idx < 0) return
  const frozen = ordered.slice(0, idx + 1)
  // 全部列都冻结 = 实际没意义，视为解冻
  config.frozenFieldIds = frozen.length >= ordered.length ? [] : frozen
  try {
    await updateView(view.id, { config, version: view.version })
    toast.success(config.frozenFieldIds.length ? `已冻结前 ${config.frozenFieldIds.length} 列` : '已解冻列')
    await loadViews(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '冻结列失败'))
  }
}

/** 右键菜单 → 按「字段」分组：复用分组链路，已按该字段分组时再次点击 = 清除分组 */
async function handleMenuGroupByField(fieldId: number) {
  await handleGroupApply(groupFieldId.value === fieldId ? null : fieldId)
}

/**
 * 右键菜单 → 按「字段」筛选：打开筛选面板并预置一条「该字段」条件。
 * FilterPanel 打开时会从 filterConfig 初始化，所以先写入一条预置规则再打开。
 */
function handleMenuFilterByField(fieldId: number) {
  presetFilterFieldId.value = fieldId
  showFilterPanel.value = true
}

/** 设置提醒（占位）：提醒能力依赖后端推送，先接入菜单避免死入口 */
function handleSetRemind(data: { fieldId: number; mode: 'daily' | 'weekly' | 'off' }) {
  if (data.mode === 'off') {
    toast.success('已关闭该列提醒')
  } else {
    toast.info(data.mode === 'daily' ? '已设置每天提醒（推送能力接入后生效）' : '已设置每周提醒（推送能力接入后生效）')
  }
}

/**
 * 拖拽列边框调整列宽：持久化到视图 config.fieldWidths（fieldId → px）。
 * 静默保存（拖拽是高频微操作，不弹 toast）；仅宽度变化时才落库。
 */
async function handleColResize(data: { fieldId: number; width: number }) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const config: ViewConfig = { ...(view.config || { schemaVersion: 1 }) }
  const widths: Record<number, number> = { ...(config.fieldWidths || {}) }
  // 宽度未变化（如点击边框）不重复落库
  if (widths[data.fieldId] === data.width) return
  widths[data.fieldId] = data.width
  config.fieldWidths = widths
  try {
    await updateView(view.id, { config, version: view.version })
    await loadViews(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '保存列宽失败'))
  }
}

/**
 * 行距设置（表头右键 → 行距设置）：持久化到当前数据表 row_height。
 * 就地更新 activeTable.rowHeight，网格立即生效，无需整表刷新。
 */
async function handleRowHeightChange(height: number) {
  if (!activeTableId.value) return
  try {
    await updateTable(activeTableId.value, { rowHeight: height })
    if (activeTable.value) {
      activeTable.value.rowHeight = height
    }
    toast.success('行距已更新')
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '设置行距失败'))
  }
}

// 字段配置弹窗方法
function selectFieldForEdit(field: BitableField) {
  editingFieldId.value = field.id
  editBase.value = {
    name: field.name,
    description: field.description || '',
    width: field.width || 200,
    required: Boolean(field.required),
    aiPrompt: field.aiPrompt || '',
  }
  // 归一化：迁移历史键 + 按字段类型补默认值，面板与保存共用同一份配置对象
  editConfig.value = normalizeFieldConfig(field.fieldType, field.config)
}

async function saveFieldConfig() {
  if (!editingFieldId.value || !editingField.value) {
    toast.warning('请选择要编辑的字段')
    return
  }
  const name = editBase.value.name.trim()
  if (!name) {
    toast.warning('请输入字段名称')
    return
  }
  if (fields.value.some((f) => f.name === name && f.id !== editingFieldId.value)) {
    toast.warning(`字段「${name}」已存在`)
    return
  }
  savingFieldConfig.value = true
  try {
    const fieldType = editingField.value.fieldType
    const data: Partial<BitableFieldCreateDTO> = {
      name,
      description: editBase.value.description,
      width: editBase.value.width,
      // 后端 required 为 Integer(tinyint)，前端是 boolean，需转为 0/1
      required: editBase.value.required ? 1 : 0,
      // 清洗：只保留该字段类型允许的键，避免切换类型后残留无关配置
      config: sanitizeFieldConfig(fieldType, editConfig.value),
    }

    // AI 字段捷径：提示词是后端列，单独回传
    if (fieldType === 'ai_text' || fieldType === 'ai_select') {
      data.aiPrompt = editBase.value.aiPrompt
      data.isAiField = 1
    }

    await updateField(editingFieldId.value, data)
    toast.success('配置保存成功')
    if (activeTableId.value) {
      await loadFields(activeTableId.value)
    }
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '保存配置失败'))
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

// ===== 字段隐藏 / 恢复（当前视图）=====
// 隐藏状态持久化到当前视图的 config.hiddenFieldIds（各视图记录独立，按视图生效）。
// visibleFields 已按它过滤，当前视图的所有展示形态（网格/看板/日历等）一致遵循。
function isFieldHiddenInView(fieldId: number) {
  const config = activeView.value?.config as ViewConfig | undefined
  return (config?.hiddenFieldIds ?? []).includes(fieldId)
}

async function handleHideField(fieldId: number) {
  const view = activeView.value
  if (!view || !activeTableId.value) return
  const currentHidden = new Set((view.config as ViewConfig | undefined)?.hiddenFieldIds ?? [])
  const willHide = !currentHidden.has(fieldId)

  if (willHide) {
    // 至少保留一列可见，避免用户隐藏全部列后无网格入口可恢复
    const visibleCount = fields.value.filter(f => !currentHidden.has(f.id)).length
    if (visibleCount <= 1) {
      toast.warning('至少保留一列可见')
      return
    }
  }

  const nextHidden = new Set(currentHidden)
  if (willHide) {
    nextHidden.add(fieldId)
  } else {
    nextHidden.delete(fieldId)
  }

  const config: ViewConfig = view.config
    ? { ...view.config, hiddenFieldIds: Array.from(nextHidden) }
    : { schemaVersion: 1, hiddenFieldIds: Array.from(nextHidden) }

  try {
    await updateView(view.id, { config, version: view.version })
    toast.success(willHide ? '列已隐藏' : '列已恢复显示')
    await loadViews(activeTableId.value!)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '更新视图配置失败'))
  }
}

/**
 * 视图级字段绑定（日历的日期字段、甘特的起止字段）改动后落库。
 * 不弹成功 toast：选择框里的值本身就是结果，日历/甘特会立刻重绘，再提示一遍是噪音。
 */
async function persistViewFieldConfig(patch: Partial<ViewConfig>) {
  const view = activeView.value
  if (!view) return
  const config: ViewConfig = { ...(view.config || { schemaVersion: 1 }), ...patch }
  try {
    await updateView(view.id, { config, version: view.version })
    await loadViews(activeTableId.value!)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '更新视图配置失败'))
  }
}

function handleCalendarFieldChange(patch: { startFieldId: number | null }) {
  const view = activeView.value
  persistViewFieldConfig({
    calendar: { ...(view?.config?.calendar || {}), startFieldId: patch.startFieldId ?? undefined },
  })
}

function handleGanttFieldChange(patch: { startFieldId: number | null; endFieldId: number | null }) {
  const view = activeView.value
  persistViewFieldConfig({
    gantt: {
      ...(view?.config?.gantt || {}),
      startFieldId: patch.startFieldId ?? undefined,
      endFieldId: patch.endFieldId ?? undefined,
    },
  })
}

async function handleCopyField(field: BitableField) {
  if (!activeTableId.value) return
  try {
    const data: BitableFieldCreateDTO = {
      name: field.name + '_副本',
      fieldType: field.fieldType,
      config: field.config ? JSON.parse(JSON.stringify(field.config)) : undefined,
      width: field.width,
      // 后端 required 为 Integer(tinyint)，field.required 运行时可能是 0/1 或 boolean，统一转 0/1
      required: field.required ? 1 : 0,
    }
    await createField(activeTableId.value, data)
    toast.success('复制成功')
    await loadFields(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '复制字段失败'))
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
    toast.success('删除成功')
    if (editingFieldId.value === field.id) {
      editingFieldId.value = null
    }
    if (activeTableId.value) {
      await loadFields(activeTableId.value)
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      toast.error(resolveErrorMessage(e, '删除字段失败'))
    }
  }
}

function openFormulaEditorForConfig() {
  formulaExpr.value = editConfig.value.formulaExpr || ''
  formulaEditorVisible.value = true
}

// 公式编辑器回填到字段配置（配置对象由 FieldAttributeForm 持有）
watch(formulaExpr, (val) => {
  if (formulaEditorVisible.value) {
    editConfig.value.formulaExpr = val
  }
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
    toast.success('克隆成功')
    await loadFields(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '克隆失败'))
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
    toast.error(resolveErrorMessage(e, '排序失败'))
  }
}

async function handleRowInsert(data?: { position?: 'above' | 'below'; rowId?: number; groupValue?: string; fieldId?: number }) {
  if (!activeTableId.value) return
  try {
    const cells: BitableRecordCreateDTO['cells'] = {}
    // 看板视图传入 groupValue + fieldId
    if (data?.fieldId && data?.groupValue !== undefined) {
      const insertField = fields.value.find((f) => f.id === data.fieldId)
      if (data.groupValue === '__ungrouped__') {
        cells[data.fieldId] = { valueText: '' }
      } else if (insertField?.fieldType === 'multi_select') {
        // 多选字段以 JSON 数组存储选项标签
        cells[data.fieldId] = { valueJson: [data.groupValue] }
      } else {
        cells[data.fieldId] = { valueText: data.groupValue }
      }
    }
    const newRecordId = await createRecord(activeTableId.value, { cells })
    // 不弹「记录已添加」：新行已可见即为反馈（钉钉多维表格同款行为）
    // 新行恒追加到末尾，若当前不在末尾页则跳到末尾页，保证点 + 后能看到新空行
    const lastPage = Math.max(1, Math.ceil((recordsTotal.value + 1) / recordsPageSize.value))
    if (recordsPage.value !== lastPage) {
      await loadRecords(activeTableId.value, lastPage)
    } else {
      await loadRecords(activeTableId.value, recordsPage.value)
    }
    gridViewRef.value?.scrollToRecord(newRecordId)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '添加失败'))
  }
}

/** 复制行：按源记录 cells 映射创建 DTO（剥离 fieldId/displayText）→ 创建 → 刷新并定位新行 */
async function handleRowCopy(rowId: number) {
  if (!activeTableId.value) return
  const source = records.value.find((r) => r.id === rowId)
  if (!source) return
  try {
    const cells: BitableRecordCreateDTO['cells'] = {}
    for (const [key, cell] of Object.entries(source.cells ?? {})) {
      const dto: NonNullable<BitableRecordCreateDTO['cells']>[number] = {}
      if (cell.valueText !== undefined && cell.valueText !== null) dto.valueText = cell.valueText
      if (cell.valueNumber !== undefined && cell.valueNumber !== null) dto.valueNumber = cell.valueNumber
      if (cell.valueDate !== undefined && cell.valueDate !== null) dto.valueDate = cell.valueDate
      if (cell.valueJson !== undefined && cell.valueJson !== null) dto.valueJson = cell.valueJson
      cells[Number(key)] = dto
    }
    const newRecordId = await createRecord(activeTableId.value, { cells })
    toast.success('已复制')
    // 新行恒追加到末尾：若当前不在末尾页则跳到末尾页，保证复制结果可见
    const lastPage = Math.max(1, Math.ceil((recordsTotal.value + 1) / recordsPageSize.value))
    if (recordsPage.value !== lastPage) {
      await loadRecords(activeTableId.value, lastPage)
    } else {
      await loadRecords(activeTableId.value, recordsPage.value)
    }
    gridViewRef.value?.scrollToRecord(newRecordId)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '复制失败'))
  }
}

async function handleFormSubmit(cells: BitableRecordCreateDTO['cells']) {
  if (!activeTableId.value) return
  savingField.value = true
  try {
    await createRecord(activeTableId.value, { cells })
    toast.success('提交成功')
    formViewRef.value?.reset()
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '提交失败'))
  } finally {
    savingField.value = false
  }
}

async function handleRowDelete(rowId: number) {
  try {
    await deleteRecord(rowId)
    toast.success('删除成功')
    records.value = records.value.filter((r) => r.id !== rowId)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '删除失败'))
  }
}

// 网格复选选区（记录 ID 列表，供批量操作与后续扩展使用）
const selectedRecordIds = ref<number[]>([])

/** 批量删除勾选行：并行逐条删除，聚合成功/失败结果后刷新 */
async function handleRowsDelete(rowIds: number[]) {
  if (!rowIds?.length) return
  const results = await Promise.allSettled(rowIds.map((id) => deleteRecord(id)))
  const failed = results.filter((r) => r.status === 'rejected').length
  const deleted = results.length - failed
  if (deleted > 0) {
    const deletedSet = new Set(
      results
        .map((r, i) => (r.status === 'fulfilled' ? rowIds[i] : null))
        .filter((id): id is number => id !== null),
    )
    records.value = records.value.filter((r) => !deletedSet.has(r.id))
    toast.success(`已删除 ${deleted} 行`)
  }
  if (failed > 0) {
    toast.error(`${failed} 行删除失败${deleted > 0 ? '，其余已删除' : ''}`)
    // 有失败时整体刷新一次，保证与服务端一致
    if (activeTableId.value) await loadRecords(activeTableId.value)
  }
}

function handleKanbanRecordUpdate(record: BitableRecord) {
  // 打开记录编辑对话框（此前误绑定为评论面板）
  recordEditTarget.value = record
  recordEditVisible.value = true
}

async function handleRecordEditSave(data: { recordId: number; version: number; cells: Record<number, { valueText?: string; valueNumber?: number; valueDate?: string; valueJson?: unknown }> }) {
  try {
    // 逐字段提交（乐观锁），任一字段版本冲突即提示刷新
    let latestVersion = data.version
    for (const [fieldIdStr, value] of Object.entries(data.cells)) {
      const newVersion = await updateCell(data.recordId, Number(fieldIdStr), { version: latestVersion, ...value })
      latestVersion = newVersion
    }
    toast.success('保存成功')
    recordEditVisible.value = false
    if (activeTableId.value) {
      await loadRecords(activeTableId.value)
    }
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '保存失败'))
  }
}

// 日历/画廊点击记录：打开评论面板作为记录详情入口
function handleOpenRecordComments(record: BitableRecord) {
  commentRecordId.value = record.id
  showCommentPanel.value = true
}

// 网格右键删除列
async function handleGridDeleteField(fieldId: number) {
  const field = fields.value.find((f) => f.id === fieldId)
  if (!field) return
  await handleDeleteField(field)
}

async function handleCardMove(data: { recordId: number; fieldId: number; fromGroup: string; toGroup: string }) {
  const record = records.value.find((r) => r.id === data.recordId)
  if (!record || !activeTableId.value) return
  const selectField = fields.value.find((f) => f.id === data.fieldId)
  if (!selectField) {
    toast.warning('未找到可分组字段')
    return
  }
  try {
    const updateData: CellUpdateDTO = { version: record.version }
    if (selectField.fieldType === 'multi_select') {
      // 多选字段以 valueJson 数组存储：保留原有选项，追加/移除目标标签
      const currentCell = record.cells?.[selectField.id]
      let current: string[] = []
      const raw = currentCell?.valueJson
      if (Array.isArray(raw)) {
        current = raw.filter((v): v is string => typeof v === 'string')
      } else if (typeof raw === 'string' && raw.trim().startsWith('[')) {
        try {
          const parsed = JSON.parse(raw)
          current = Array.isArray(parsed) ? parsed.filter((v: unknown): v is string => typeof v === 'string') : []
        } catch { current = [] }
      } else if (currentCell?.valueText) {
        current = [currentCell.valueText]
      }
      const next = data.toGroup === '__ungrouped__'
        ? current.filter((label) => label !== data.fromGroup)
        : Array.from(new Set([...current, data.toGroup]))
      updateData.valueJson = next
      if (next.length === 0) updateData.valueText = ''
    } else {
      updateData.valueText = data.toGroup === '__ungrouped__' ? '' : data.toGroup
    }
    const newVersion: number = await updateCell(data.recordId, selectField.id, updateData)
    record.version = newVersion
    toast.success('卡片已移动')
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '移动卡片失败'))
  }
}

function handleOpenComments() {
  if (!records.value.length) {
    toast.warning('暂无记录')
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

function handleAiUpdated() {
  if (activeTableId.value) {
    loadFields(activeTableId.value)
    loadRecords(activeTableId.value)
  }
}

async function handleAiTableCreated(tableId: number) {
  // 先等表列表加载完成，再选中新表，否则 activeTable 解析不到
  await loadTables()
  await handleSelectTable(tableId)
}

function handleAiFillColumn(fieldId: number) {
  selectedRecordId.value = undefined
  showAiFillDialog.value = true
}

function handleAiClassifyColumn(fieldId: number) {
  showAiClassifyDialog.value = true
}

function handleAiSummarizeColumn(fieldId: number) {
  showAiSummarizeDialog.value = true
}

async function handleConvertToAiField(fieldId: number) {
  try {
    await ElMessageBox.confirm('确认将该字段转为 AI 文本字段吗？转换后原有类型配置将丢失。', '转为 AI 字段', {
      confirmButtonText: '确认转换',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await updateField(fieldId, { fieldType: 'ai_text', isAiField: true } as any)
    toast.success('转换成功')
    if (activeTableId.value) loadFields(activeTableId.value)
  } catch (e: any) {
    if (e !== 'cancel') {
      toast.error(resolveErrorMessage(e, '转换失败'))
    }
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
  background: var(--color-background);
  height: 100vh;
  overflow: hidden;
}

.records-pager {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  border-bottom: 1px solid var(--color-border, var(--color-border));
  background: var(--color-surface, var(--color-surface));
  flex-shrink: 0;
  min-height: 52px;

  .editor-header__back {
    color: var(--color-text-secondary, var(--color-text-secondary));
    font-weight: 500;
  }

  .editor-header__divider {
    width: 1px;
    height: 18px;
    background: var(--color-border, var(--color-muted));
  }

  .editor-header__breadcrumbs {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .editor-header__crumb {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text-secondary, var(--color-text-secondary));
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 240px;

    &--active {
      color: var(--color-text-primary, var(--color-text-primary));
      font-weight: 700;
    }

    /* 面包屑可点击回退：用 button 承载，需要抹掉浏览器默认按钮样式 */
    &.is-clickable {
      padding: 2px 6px;
      margin: 0 -2px;
      border: none;
      background: none;
      font-family: inherit;
      line-height: inherit;
      border-radius: var(--border-radius-sm, 4px);
      cursor: pointer;
      transition: background 0.12s ease, color 0.12s ease;

      &:hover {
        background: var(--color-surface-alt, var(--color-surface-alt));
        color: var(--color-primary, var(--color-primary));
      }

      &:focus-visible {
        outline: 2px solid var(--color-primary, #2563eb);
        outline-offset: 1px;
      }
    }
  }

  .editor-header__sep {
    font-size: 12px;
    color: var(--color-text-placeholder, #cbd5e1);
  }

  .editor-header__view-tag {
    margin-left: 4px;
    font-weight: 500;
    background: var(--color-primary-subtle, var(--color-primary-subtle)) !important;
    color: var(--color-primary, var(--color-primary)) !important;
    border: 0.5px solid var(--color-primary-light, #dbeafe) !important;
  }

  .mr-4 {
    margin-right: 4px;
  }

  .editor-header__spacer {
    flex: 1;
  }

  /* 高级权限入口：与头部协作者区同排，视觉上轻量不抢焦点 */
  .editor-header__permission {
    flex-shrink: 0;
    color: var(--color-text-secondary, var(--color-text-secondary));
    background: var(--color-background, var(--color-background));
    border-color: var(--color-border, var(--color-border));

    .el-icon {
      margin-right: 4px;
    }

    &:hover {
      color: var(--color-primary, var(--color-primary));
      border-color: var(--color-primary, var(--color-primary));
      background: var(--color-primary-subtle, var(--color-primary-subtle));
    }
  }

  .editor-header__collaborators {
    display: flex;
    align-items: center;
    padding: 4px 4px 4px 8px;
    border-radius: 14px;
    background: var(--color-background, var(--color-background));
  }

  .editor-header__avatar-button {
    display: inline-flex;
    padding: 0;
    border: 0;
    border-radius: 50%;
    background: transparent;
    cursor: pointer;
  }

  .editor-header__avatar {
    border: 2px solid var(--color-surface, #fff);
    font-weight: 600;
    font-size: 12px;
    color: #fff;
    transition: transform 200ms var(--ease-spring, cubic-bezier(0.34, 1.56, 0.64, 1));
  }

  .editor-header__avatar-button:hover .editor-header__avatar,
  .editor-header__avatar-button:focus-visible .editor-header__avatar {
    transform: translateY(-2px) scale(1.08);
  }

  .editor-header__avatar-button:focus-visible {
    outline: 2px solid var(--color-primary, #2563eb);
    outline-offset: 2px;
  }

  .editor-header__avatar-more {
    margin-left: 6px;
    padding: 2px 5px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: var(--color-text-secondary, var(--color-text-secondary));
    cursor: pointer;
    font-size: 12px;
    font-weight: 600;
  }

  .editor-header__avatar-more:hover,
  .editor-header__avatar-more:focus-visible {
    background: var(--color-primary-subtle, var(--color-primary-subtle));
    color: var(--color-primary, var(--color-primary));
    outline: none;
  }
}

.collaborator-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;

  > div {
    display: flex;
    min-width: 0;
    flex-direction: column;
  }

  strong {
    overflow: hidden;
    color: var(--color-text-primary, var(--color-text-primary));
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 2px;
    color: var(--color-success, #10b981);
    font-size: 12px;
  }
}

.collaborator-list {
  display: flex;
  max-height: 240px;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.editor-body {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
  min-height: 0;
  position: relative;
}

.editor-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);

  &.is-collapsed {
    display: none;
  }

  .editor-sidebar__inner {
    flex: 1;
    overflow: hidden;
    min-width: 0;
  }
}

.editor-sidebar__resizer {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  align-self: stretch;
  z-index: 1;

  &:hover,
  &:active {
    background: var(--color-accent);
  }
}

.editor-sidebar__expand-btn {
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-left: 0;
  border-radius: 0 6px 6px 0;
  background: var(--color-surface);
  cursor: pointer;
  z-index: 2;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.06);

  &:hover {
    background: var(--color-fill-secondary);
  }
}

.editor-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
  min-height: 0;
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
  max-height: 320px;
  overflow-y: auto;
  border-bottom: 1px solid var(--color-border, var(--color-border));
  padding: 8px 4px;
  margin-bottom: 8px;
}

.field-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  margin-bottom: 2px;
  transition: all 150ms var(--ease-standard, ease);

  &:hover {
    background: var(--color-row-hover-bg, rgba(59, 130, 246, 0.04));
  }

  &--active {
    background: var(--color-primary-subtle, var(--color-primary-subtle)) !important;
    color: var(--color-primary, var(--color-primary));
    box-shadow: inset 3px 0 0 var(--color-primary, #2563eb);
  }

  &--hidden {
    .field-item__name {
      color: var(--color-text-placeholder, var(--color-text-tertiary));
      text-decoration: line-through;
    }
  }

  .field-item__info {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .field-item__name {
    font-size: 13px;
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
  padding: 12px 4px 16px;

  .field-editor__title {
    font-size: 15px;
    font-weight: 700;
    margin-bottom: 16px;
    color: var(--color-text-primary, var(--color-text-primary));
    letter-spacing: -0.01em;
  }

  .field-editor__actions {
    display: flex;
    justify-content: flex-end;
    gap: 10px;
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid var(--color-border, var(--color-border));
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
