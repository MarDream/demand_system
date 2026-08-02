<template>
  <div class="bitable-editor">
    <!-- 顶部面包屑 -->
    <div class="editor-header">
      <el-button link @click="router.push('/bitable')">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <span class="editor-title">{{ base?.name }}</span>
    </div>

    <div class="editor-body" :style="sidebar.styleVars">
      <!-- 左侧数据表侧边栏 -->
      <aside class="editor-sidebar" :class="{ 'is-collapsed': sidebar.collapsed }">
        <div class="editor-sidebar__inner">
          <TableSidebar
            :tables="tables"
            :activeTableId="activeTableId"
            @select="handleSelectTable"
            @create="handleCreateTable"
            @delete="handleDeleteTable"
          />
        </div>
      </aside>
      <div class="editor-sidebar__resizer" @mousedown="sidebar.startResize" @dblclick="sidebar.toggle" />
      <button v-if="sidebar.collapsed" class="editor-sidebar__expand-btn" type="button" title="展开侧边栏" @click="sidebar.toggle">
        <el-icon><ArrowRight /></el-icon>
      </button>

      <!-- 主编辑区域 -->
      <div class="editor-main">
        <Toolbar
          :table="activeTable"
          :views="views"
          :activeViewId="activeViewId"
          @add-field="handleAddField"
          @view-switch="handleViewSwitch"
          @create-view="handleCreateView"
          @rename-view="handleRenameView"
          @duplicate-view="handleDuplicateView"
          @set-default-view="handleSetDefaultView"
          @delete-view="handleDeleteView"
          @open-comments="handleOpenComments"
          @open-members="showMemberManager = true"
          @open-ai-panel="showAiPanel = true"
          @open-import-export="showImportExport = true"
          @open-field-config="fieldConfigDrawerVisible = true"
          @open-ai-fill="showAiFillDialog = true"
          @open-ai-classify="showAiClassifyDialog = true"
          @open-ai-summarize="showAiSummarizeDialog = true"
          @open-ai-build-table="showAiBuildTableDialog = true"
        />
        <GridView
          v-if="currentViewType === 'grid'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
          @cell-change="handleCellChange"
          @row-insert="handleRowInsert"
          @row-delete="handleRowDelete"
          @rename-field="(fieldId: number) => handleRenameField(fieldId)"
          @clone-field="handleCloneField"
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
        />
        <CalendarView
          v-else-if="currentViewType === 'calendar'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
        />
        <GalleryView
          v-else-if="currentViewType === 'gallery'"
          :table="activeTable"
          :fields="visibleFields"
          :records="records"
          :loading="loadingRecords"
          :viewConfig="activeView?.config ?? null"
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
            <el-form-item label="表单隐藏">
              <el-switch v-model="editForm.formHidden" />
            </el-form-item>
            <el-form-item label="表单占位">
              <el-input v-model="editForm.formPlaceholder" placeholder="表单填写提示，可覆盖字段描述" />
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
            <template v-if="isOptionField(editingField.fieldType)">
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



            <!-- 查找/汇总配置 -->
            <template v-if="editingField.fieldType === 'lookup' || editingField.fieldType === 'rollup'">
              <el-divider content-position="left">引用计算配置</el-divider>
              <el-form-item label="关联字段">
                <el-select v-model="editForm.linkFieldId" placeholder="选择当前表关联字段" style="width: 100%;" clearable @change="handleLookupLinkFieldChange">
                  <el-option
                    v-for="f in fields.filter(f => f.id !== editingFieldId && (f.fieldType === 'link' || f.fieldType === 'bidirectional_link'))"
                    :key="f.id"
                    :label="f.name"
                    :value="f.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="目标字段">
                <el-select v-model="editForm.targetFieldId" placeholder="选择关联表中要引用/汇总的字段" style="width: 100%;" clearable>
                  <el-option v-for="f in linkTargetFields" :key="f.id" :label="f.name" :value="f.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="editingField.fieldType === 'rollup'" label="汇总方式">
                <el-select v-model="editForm.aggregation" style="width: 100%;">
                  <el-option label="计数" value="count" />
                  <el-option label="求和" value="sum" />
                  <el-option label="平均值" value="average" />
                  <el-option label="最小值" value="min" />
                  <el-option label="最大值" value="max" />
                </el-select>
              </el-form-item>
            </template>

            <!-- 关联特有配置 -->
            <template v-if="isLinkField(editingField.fieldType)">
              <el-divider content-position="left">关联配置</el-divider>
              <el-form-item label="目标表">
                <el-select v-model="editForm.linkTargetTableId" placeholder="选择关联数据表" style="width: 100%;" @change="loadLinkTargetFields">
                  <el-option v-for="t in tables.filter(t => t.id !== activeTableId)" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="显示字段">
                <el-select v-model="editForm.linkDisplayFieldId" placeholder="选择显示字段" style="width: 100%;" clearable>
                  <el-option
                    v-for="f in linkTargetFields"
                    :key="f.id"
                    :label="f.name"
                    :value="f.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="editingField.fieldType === 'bidirectional_link'" label="反向字段">
                <el-select v-model="editForm.reverseFieldId" placeholder="选择目标表中的反向关联字段" style="width: 100%;" clearable>
                  <el-option
                    v-for="f in linkTargetFields.filter(f => f.fieldType === 'link' || f.fieldType === 'bidirectional_link')"
                    :key="f.id"
                    :label="f.name"
                    :value="f.id"
                  />
                </el-select>
              </el-form-item>
            </template>

            <!-- AI 字段捷径配置 -->
            <template v-if="editingField.fieldType === 'ai_text' || editingField.fieldType === 'ai_select'">
              <el-divider content-position="left">AI 字段捷径配置</el-divider>
              <el-form-item label="AI 提示词">
                <el-input
                  v-model="editForm.aiPrompt"
                  type="textarea"
                  :rows="3"
                  placeholder="例如：根据{需求描述}和{优先级}，生成一段概要"
                />
              </el-form-item>
              <el-form-item label="源字段">
                <el-select
                  v-model="editForm.sourceFieldIds"
                  multiple
                  placeholder="选择参与生成的源字段"
                  style="width: 100%;"
                >
                  <el-option
                    v-for="f in fields.filter(f => f.id !== editingFieldId && f.fieldType !== 'ai_text' && f.fieldType !== 'ai_select')"
                    :key="f.id"
                    :label="f.name"
                    :value="f.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="自动计算">
                <el-switch v-model="editForm.autoCompute" />
                <span style="margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary);">
                  源字段数据变更时自动重新计算
                </span>
              </el-form-item>
              <el-form-item v-if="editingField.fieldType === 'ai_select'" label="选项列表">
                <div class="option-list">
                  <div v-for="(opt, idx) in editForm.options" :key="idx" class="option-item">
                    <el-input v-model="opt.label" placeholder="选项名称" size="small" />
                    <el-button link size="small" @click="removeEditOption(idx)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button link type="primary" size="small" @click="addEditOption">
                    <el-icon><Plus /></el-icon> 添加选项
                  </el-button>
                </div>
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
            <el-option-group label="常规">
              <el-option label="文本" value="text" />
              <el-option label="数字" value="number" />
              <el-option label="日期" value="date" />
              <el-option label="单选" value="single_select" />
              <el-option label="多选" value="multi_select" />
              <el-option label="人员" value="user" />
              <el-option label="群组" value="group" />
              <el-option label="复选框" value="checkbox" />
              <el-option label="附件" value="attachment" />
              <el-option label="超链接" value="url" />
            </el-option-group>
            <el-option-group label="业务">
              <el-option label="流程" value="process" />
              <el-option label="按钮" value="button" />
              <el-option label="自动编号" value="auto_number" />
              <el-option label="电话" value="phone" />
              <el-option label="邮箱" value="email" />
              <el-option label="地理位置" value="location" />
              <el-option label="条码" value="barcode" />
              <el-option label="进度" value="progress" />
              <el-option label="货币" value="currency" />
              <el-option label="评分" value="rating" />
            </el-option-group>
            <el-option-group label="高级">
              <el-option label="单向关联" value="link" />
              <el-option label="双向关联" value="bidirectional_link" />
              <el-option label="汇总" value="rollup" />
              <el-option label="查找引用" value="lookup" />
              <el-option label="公式" value="formula" />
              <el-option label="创建人" value="created_by" />
              <el-option label="修改人" value="modified_by" />
              <el-option label="创建时间" value="created_time" />
              <el-option label="最后更新时间" value="last_modified_time" />
            </el-option-group>
            <el-option-group label="AI 捷径">
              <el-option label="AI 文本" value="ai_text" />
              <el-option label="AI 选择" value="ai_select" />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item v-if="isOptionField(addFieldForm.fieldType)" label="选项">
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
        <el-form-item v-if="isLinkField(addFieldForm.fieldType)" label="目标表">
          <el-select v-model="linkTargetTableId" placeholder="选择关联数据表" style="width: 100%;">
            <el-option v-for="t in tables.filter(t => t.id !== activeTableId)" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="addFieldForm.fieldType === 'formula'" label="公式">
          <el-input v-model="formulaExpr" type="textarea" :rows="2" placeholder="例如: {单价} * {数量}" />
        </el-form-item>
        <el-form-item v-if="addFieldForm.fieldType === 'auto_number'" label="编号前缀">
          <el-input v-model="autoNumberPrefix" placeholder="例如：ORD-" />
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
import { ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { ArrowLeft, Plus, Delete, Edit, CopyDocument, ArrowRight, MagicStick } from '@element-plus/icons-vue'
import { useCollapsibleSidebar } from '@/composables/useCollapsibleSidebar'
import { useToast } from '@/composables/useToast'
import TableSidebar from './components/TableSidebar.vue'
import Toolbar from './components/Toolbar.vue'
import GridView from './components/GridView.vue'
import KanbanView from './components/KanbanView.vue'
import GanttView from './components/GanttView.vue'
import CalendarView from './components/CalendarView.vue'
import GalleryView from './components/GalleryView.vue'
import FormView from './components/FormView.vue'
import CommentPanel from './components/CommentPanel.vue'
import MemberManager from './components/MemberManager.vue'
import ConflictDialog from './components/ConflictDialog.vue'
import AiChatPanel from './components/AiChatPanel.vue'
import AiFillDialog from './components/AiFillDialog.vue'
import AiClassifyDialog from './components/AiClassifyDialog.vue'
import AiSummarizeDialog from './components/AiSummarizeDialog.vue'
import AiBuildTableDialog from './components/AiBuildTableDialog.vue'
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
  ViewConfig,
} from '@/types/bitable'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const baseId = Number(route.params.baseId)
const base = ref<BitableBase | null>(null)
const tables = ref<BitableTable[]>([])
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
const renameInputRef = ref<any>(null)
const showCommentPanel = ref(false)
const showMemberManager = ref(false)
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
const sidebar = useCollapsibleSidebar({
  defaultWidth: 240,
  minWidth: 200,
  maxWidth: 400,
  widthVar: '--editor-sidebar-width',
  resizerWidth: 4,
  resizerWidthVar: '--editor-sidebar-resizer-width',
})

const linkSelectorVisible = ref(false)
const currentLinkField = ref<BitableField | null>(null)
const currentLinkRecordId = ref<number | null>(null)
const currentLinkSelectedIds = ref<number[]>([])

const renameFieldDialogVisible = ref(false)
const renameFieldId = ref<number | null>(null)
const renameFieldName = ref('')

const formulaEditorVisible = ref(false)
const formulaExpr = ref('')
const autoNumberPrefix = ref('')
const formViewRef = ref<InstanceType<typeof FormView> | null>(null)

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
  reverseFieldId: number | null
  linkFieldId: number | null
  targetFieldId: number | null
  aggregation: 'count' | 'sum' | 'average' | 'min' | 'max'
  formHidden: boolean
  formPlaceholder: string
  // AI 字段捷径配置
  aiPrompt: string
  sourceFieldIds: number[]
  autoCompute: boolean
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
  reverseFieldId: null,
  linkFieldId: null,
  targetFieldId: null,
  aggregation: 'count',
  formHidden: false,
  formPlaceholder: '',
  aiPrompt: '',
  sourceFieldIds: [],
  autoCompute: false,
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
  return field
}

function fieldTypeLabel(type: string): string {
  return fieldTypeLabelMap[type] || type
}

function isOptionField(type?: string) {
  return type === 'single_select' || type === 'multi_select' || type === 'process'
}

function isLinkField(type?: string) {
  return type === 'link' || type === 'bidirectional_link'
}

function isReadonlyFieldType(type?: string) {
  return ['auto_number', 'created_time', 'modified_time', 'last_modified_time', 'created_user', 'modified_user', 'created_by', 'modified_by', 'formula', 'lookup', 'rollup'].includes(type || '')
}

// WebSocket 实时协作
const {
  connect: wsConnect,
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

async function handleSelectTable(tableId: number) {
  activeTableId.value = tableId
  await loadViews(tableId)
  await loadFields(tableId)
  await loadRecords(tableId)
  setActiveView()
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

async function loadRecords(tableId: number) {
  loadingRecords.value = true
  try {
    const view = activeView.value
    if (view && (view.filterConfig?.length || view.sortConfig?.length)) {
      // 视图有筛选/排序配置时，走高级查询接口
      const res = await queryRecords(tableId, {
        filterConfig: view.filterConfig,
        sortConfig: view.sortConfig,
        viewId: view.id,
        pageNum: 1,
        pageSize: 1000,
      })
      records.value = res.list || []
    } else {
      const res = await listRecords(tableId, { pageNum: 1, pageSize: 1000 })
      records.value = res.list || []
    }
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '加载记录失败'))
  } finally {
    loadingRecords.value = false
  }
}

async function handleCreateTable(name: string) {
  try {
    const newId = await createTable(baseId, { name })
    toast.success('创建成功')
    const newTableId = typeof newId === 'number' ? newId : Number(newId)
    tables.value.push({ id: newTableId, name, baseId } as BitableTable)
    handleSelectTable(newTableId)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '创建失败'))
  }
}

async function handleDeleteTable(tableId: number) {
  try {
    await deleteTable(tableId)
    toast.success('删除成功')
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
    toast.error(resolveErrorMessage(e, '删除失败'))
  }
}

function handleAddField() {
  addFieldForm.value = { name: '', fieldType: 'text' }
  addFieldOptionList.value = []
  linkTargetTableId.value = null
  formulaExpr.value = ''
  autoNumberPrefix.value = ''
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
    toast.warning('请输入字段名称')
    return
  }
  if (!activeTableId.value) {
    toast.warning('请先选择数据表')
    return
  }
  savingField.value = true
  try {
    const data: BitableFieldCreateDTO = {
      name: addFieldForm.value.name,
      fieldType: addFieldForm.value.fieldType,
    }
    if (isOptionField(addFieldForm.value.fieldType)) {
      data.config = { options: addFieldOptionList.value.filter((o) => o.label.trim()) }
    }
    if (isLinkField(addFieldForm.value.fieldType) && linkTargetTableId.value) {
      data.config = { ...(data.config || {}), linkTargetTableId: linkTargetTableId.value }
    }
    if (addFieldForm.value.fieldType === 'formula' && formulaExpr.value) {
      data.config = { ...(data.config || {}), formulaExpr: formulaExpr.value }
    }
    if (addFieldForm.value.fieldType === 'auto_number') {
      data.config = { ...(data.config || {}), prefix: autoNumberPrefix.value, digits: 4 }
    }
    if (addFieldForm.value.fieldType === 'ai_text' || addFieldForm.value.fieldType === 'ai_select') {
      // 后端 isAiField 为 Integer(tinyint)，传 1 而非 boolean true
      data.isAiField = 1
      data.aiPrompt = ''
    }
    await createField(activeTableId.value, data)
    toast.success('添加成功')
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
    if (['number', 'currency', 'progress', 'rating'].includes(field.fieldType)) {
      updateData.valueNumber = Number(data.newValue) || 0
    } else if (field.fieldType === 'date') {
      updateData.valueDate = String(data.newValue)
    } else if (field.fieldType === 'check' || field.fieldType === 'checkbox') {
      updateData.valueText = String(Boolean(data.newValue))
    } else if (field.fieldType === 'single_select' || field.fieldType === 'multi_select' || field.fieldType === 'process') {
      updateData.valueText = String(data.newValue ?? '')
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
    toast.success('已保存')
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

// 字段配置弹窗方法
function selectFieldForEdit(field: BitableField) {
  editingFieldId.value = field.id
  editForm.value.name = field.name
  editForm.value.description = ''
  editForm.value.width = field.width || 200
  editForm.value.required = Boolean(field.required)
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
  editForm.value.reverseFieldId = null
  editForm.value.linkFieldId = null
  editForm.value.targetFieldId = null
  editForm.value.aggregation = 'count'
  editForm.value.formHidden = false
  editForm.value.formPlaceholder = ''

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
    if (['number', 'currency', 'progress', 'rating'].includes(field.fieldType)) {
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
  if ((config as any).reverseFieldId) {
    editForm.value.reverseFieldId = (config as any).reverseFieldId
  }
  if ((config as any).linkFieldId) {
    editForm.value.linkFieldId = (config as any).linkFieldId
    handleLookupLinkFieldChange((config as any).linkFieldId)
  }
  if ((config as any).targetFieldId || (config as any).lookupFieldId || (config as any).rollupFieldId) {
    editForm.value.targetFieldId = (config as any).targetFieldId || (config as any).lookupFieldId || (config as any).rollupFieldId
  }
  if ((config as any).aggregation) {
    editForm.value.aggregation = (config as any).aggregation
  }
  if ((config as any).formHidden !== undefined) {
    editForm.value.formHidden = Boolean((config as any).formHidden)
  }
  if ((config as any).formPlaceholder) {
    editForm.value.formPlaceholder = String((config as any).formPlaceholder)
  }
  // AI 字段捷径配置
  editForm.value.aiPrompt = field.aiPrompt || (config as any).aiPrompt || ''
  editForm.value.sourceFieldIds = (config as any).sourceFieldIds || []
  editForm.value.autoCompute = (config as any).autoCompute ?? false
}

async function loadLinkTargetFields(tableId?: number | null) {
  if (!tableId) {
    linkTargetFields.value = []
    return
  }
  try {
    const res = await listFields(tableId)
    linkTargetFields.value = Array.isArray(res) ? res : (res as any).data || []
  } catch {
    linkTargetFields.value = []
  }
}

function handleLookupLinkFieldChange(fieldId?: number) {
  editForm.value.targetFieldId = null
  const linkField = fields.value.find((f) => f.id === fieldId)
  const targetTableId = linkField?.config?.linkTargetTableId
  if (targetTableId) {
    loadLinkTargetFields(targetTableId)
  } else {
    linkTargetFields.value = []
  }
}

async function saveFieldConfig() {
  if (!editingFieldId.value || !editingField.value) {
    toast.warning('请选择要编辑的字段')
    return
  }
  savingFieldConfig.value = true
  try {
    const data: any = {
      name: editForm.value.name,
      width: editForm.value.width,
      // 后端 required 为 Integer(tinyint)，前端 editForm.required 是 boolean，需转为 0/1
      required: editForm.value.required ? 1 : 0,
    }

    const config: any = {}
    if (editForm.value.formHidden) {
      config.formHidden = true
    }
    if (editForm.value.formPlaceholder) {
      config.formPlaceholder = editForm.value.formPlaceholder
    }

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

    if (isOptionField(editingField.value.fieldType)) {
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

    if (editingField.value.fieldType === 'lookup' || editingField.value.fieldType === 'rollup') {
      if (editForm.value.linkFieldId) {
        config.linkFieldId = editForm.value.linkFieldId
      }
      if (editForm.value.targetFieldId) {
        config.targetFieldId = editForm.value.targetFieldId
      }
      if (editingField.value.fieldType === 'rollup') {
        config.aggregation = editForm.value.aggregation
      }
    }

    if (isLinkField(editingField.value.fieldType)) {
      if (editForm.value.linkTargetTableId) {
        config.linkTargetTableId = editForm.value.linkTargetTableId
      }
      if (editForm.value.linkDisplayFieldId) {
        config.linkDisplayFieldId = editForm.value.linkDisplayFieldId
      }
      if (editingField.value.fieldType === 'bidirectional_link' && editForm.value.reverseFieldId) {
        config.reverseFieldId = editForm.value.reverseFieldId
      }
    }

    // AI 字段捷径配置
    if (editingField.value.fieldType === 'ai_text' || editingField.value.fieldType === 'ai_select') {
      data.aiPrompt = editForm.value.aiPrompt
      // 后端 isAiField 为 Integer(tinyint)，传 1 而非 boolean true
      data.isAiField = 1
      config.sourceFieldIds = editForm.value.sourceFieldIds
      config.autoCompute = editForm.value.autoCompute
      if (editingField.value.fieldType === 'ai_select') {
        config.options = editForm.value.options.filter((o) => o.label.trim())
      }
    }

    data.config = config
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
      cells[data.fieldId] = { valueText: data.groupValue === '__ungrouped__' ? '' : data.groupValue }
    }
    await createRecord(activeTableId.value, { cells })
    toast.success('记录已添加')
    await loadRecords(activeTableId.value)
  } catch (e: any) {
    toast.error(resolveErrorMessage(e, '添加失败'))
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

function handleKanbanRecordUpdate(record: BitableRecord) {
  commentRecordId.value = record.id
  showCommentPanel.value = true
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
    const updateData: CellUpdateDTO = {
      version: record.version,
      valueText: data.toGroup === '__ungrouped__' ? '' : data.toGroup,
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
    toast.warning('暂无记录，请先添加数据')
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

function handleAiTableCreated(tableId: number) {
  loadTables()
  handleSelectTable(tableId)
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
  display: grid;
  grid-template-columns: var(--editor-sidebar-width, 240px) var(--editor-sidebar-resizer-width, 4px) minmax(0, 1fr);
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
  background: #fff;
  cursor: pointer;
  z-index: 2;
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.06);

  &:hover {
    background: #f5f7fa;
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
