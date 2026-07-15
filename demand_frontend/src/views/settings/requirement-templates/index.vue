<template>
  <el-table :data="templates" border v-loading="loading">
    <el-table-column v-if="isColumnVisible('requirementTypeName')" prop="requirementTypeName" label="需求类型" width="150" />
    <el-table-column v-if="isColumnVisible('templateName')" prop="templateName" label="模板名称" min-width="200" />
      <el-table-column v-if="isColumnVisible('isActive')" prop="isActive" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive === 1 ? 'success' : 'info'">
            {{ row.isActive === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('isDefault')" prop="isDefault" label="默认" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="warning" size="small">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('operations')" label="操作" width="280">
        <template #default="{ row }">
          <AppButton size="small" permission="button:requirement-template:update" @click="handleEdit(row)">编辑</AppButton>
          <AppButton
            size="small"
            :type="row.isDefault === 1 ? '' : 'warning'"
            permission="button:requirement-template:toggle"
            @click="handleSetDefault(row)"
          >
            {{ row.isDefault === 1 ? '已是默认' : '设为默认' }}
          </AppButton>
          <AppButton
            size="small"
            :type="row.isActive === 1 ? 'warning' : 'success'"
            permission="button:requirement-template:toggle"
            @click="handleToggleStatus(row)"
          >
            {{ row.isActive === 1 ? '禁用' : '启用' }}
          </AppButton>
          <AppButton size="small" type="danger" permission="button:requirement-template:delete" @click="handleDelete(row)">删除</AppButton>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="920px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      class="template-dialog"
      @closed="onDialogClosed"
    >
      <el-form :model="form" label-width="120px" @submit.prevent>
        <el-form-item label="需求类型">
          <el-select
            v-model="form.requirementTypeCode"
            :disabled="isEditMode"
            placeholder="请选择需求类型"
          >
            <el-option
              v-for="type in configTypes"
              :key="type.code"
              :label="type.name"
              :value="type.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" placeholder="如: 功能需求模板" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="isDefaultSwitch" />
          <span class="form-tip">同一类型下只能有一个默认模板</span>
        </el-form-item>
        <el-form-item label="模板内容">
          <div class="template-editor" v-loading="saving">
            <IsleEditorToolbar v-if="templateEditorInstance" :editor="templateEditorInstance" />
            <IsleEditor
              v-if="dialogVisible"
              :extensions="editorExtensions"
              locale="zh"
              @create="onTemplateEditorCreate"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <AppButton type="primary" :permission="savePermission" @click="handleSave">保存</AppButton>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { Setting } from '@element-plus/icons-vue'
import { computed, ref, onBeforeUnmount, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAllRequirementTemplates,
  saveRequirementTemplate,
  deleteRequirementTemplate,
  toggleRequirementTemplateStatus,
  setDefaultRequirementTemplate
} from '@/api/modules/requirement'
import { requirementConfigApi, type RequirementType } from '@/api/modules/requirementConfig'
import type { RequirementTemplate, RequirementTemplateSave, TemplateSection } from '@/types/requirement'
import AppButton from '@/components/common/AppButton.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import Image from '@tiptap/extension-image'
import '@isle-editor/vue3/dist/style.css'
import { resolveErrorMessage } from '@/utils/error'

const props = defineProps<{
  preselectedTypeCode?: string
}>()

const templates = ref<RequirementTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新建模板')
const isEditMode = ref(false)
const configTypes = ref<RequirementType[]>([])
const templateEditorInstance = ref<any>(null)

// ── 列表字段设置 ──
const templateAllColumns: ColumnDef[] = [
  { key: 'requirementTypeName', label: '需求类型', group: '基础字段', width: 150 },
  { key: 'templateName', label: '模板名称', group: '基础字段', minWidth: 200 },
  { key: 'isActive', label: '状态', group: '状态信息', width: 100 },
  { key: 'isDefault', label: '默认', group: '状态信息', width: 80 },
  { key: 'operations', label: '操作', width: 280 },
]
const templateDefaultKeys = ['requirementTypeName', 'templateName', 'isActive', 'isDefault', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'requirement_template_list',
  columns: templateAllColumns,
  defaultKeys: templateDefaultKeys,
})

function isColumnVisible(key: string) {
  return visibleColumns.value.some((c) => c.key === key)
}

// 暴露给父组件（需求基本配置）调用的方法，必须在 useColumnConfig 解构后
defineExpose({ openColumnConfig, handleCreate })

const savePermission = computed(() => isEditMode.value ? 'button:requirement-template:update' : 'button:requirement-template:create')

const form = ref<RequirementTemplateSave>({
  requirementTypeCode: '',
  templateName: '',
  templateContent: {
    contentHtml: '',
  },
  isDefault: 0,
})

const editorExtensions = [
  RichTextKit.configure({
    placeholder: { placeholder: '请输入模板富文本内容...' },
  }),
  Image.configure({
    inline: false,
    allowBase64: true,
  }),
]

const isDefaultSwitch = computed({
  get: () => form.value.isDefault === 1,
  set: (val: boolean) => { form.value.isDefault = val ? 1 : 0 }
})

onMounted(() => {
  loadTemplates()
  loadConfigTypes()
  loadColumnConfig()
})

onBeforeUnmount(() => {
  templateEditorInstance.value?.destroy?.()
  templateEditorInstance.value = null
})

watch(dialogVisible, (visible) => {
  if (visible) {
    // 对话框打开时，等待编辑器创建完成后设置内容
    setTimeout(() => {
      const html = form.value.templateContent.contentHtml || ''
      if (templateEditorInstance.value && html) {
        templateEditorInstance.value.commands?.setContent?.(html)
      }
    }, 100)
  }
})

async function loadConfigTypes() {
  try {
    const res = await requirementConfigApi.listTypes() as any
    const list = Array.isArray(res) ? res : res?.data || []
    configTypes.value = list
  } catch (error) {
    console.error(error)
  }
}

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await getAllRequirementTemplates()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '加载模板列表失败'))
  } finally {
    loading.value = false
  }
}

function buildLegacyTemplateContent(sections: TemplateSection[] = []) {
  return sections.map((section) => {
    if (section.fieldType === 'richtext' && section.defaultContent) {
      return section.defaultContent
    }
    return `<h3>${section.sectionName || '未命名段落'}</h3><p>${section.placeholder || '请填写...'}</p>`
  }).filter(Boolean).join('')
}

function normalizeTemplateContent(template?: RequirementTemplate | null) {
  const directContent = template?.templateContent?.contentHtml?.trim()
  if (directContent) return directContent
  const sections = template?.templateContent?.sections || []
  return buildLegacyTemplateContent(sections)
}

function handleCreate() {
  dialogTitle.value = '新建模板'
  isEditMode.value = false
  form.value = {
    requirementTypeCode: props.preselectedTypeCode || '',
    templateName: '',
    templateContent: {
      contentHtml: '',
    },
    isDefault: 0,
  }
  dialogVisible.value = true
}

function handleEdit(row: RequirementTemplate) {
  dialogTitle.value = '编辑模板'
  isEditMode.value = true
  form.value = {
    id: row.id,
    requirementTypeCode: row.requirementTypeCode,
    templateName: row.templateName,
    templateContent: {
      contentHtml: normalizeTemplateContent(row),
    },
    isDefault: row.isDefault || 0,
    sortOrder: row.sortOrder,
  }
  dialogVisible.value = true
}

function onTemplateEditorCreate({ editor }: { editor: any }) {
  templateEditorInstance.value = editor
}

function onDialogClosed() {
  // 不销毁编辑器实例，只清空内容
  if (templateEditorInstance.value) {
    templateEditorInstance.value.commands?.clearContent?.()
  }
}

function isMeaningfulHtml(html: string) {
  const text = html.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
  return text.length > 0
}

async function handleSave() {
  if (!form.value.requirementTypeCode) {
    ElMessage.warning('请选择需求类型')
    return
  }
  if (!form.value.templateName) {
    ElMessage.warning('请输入模板名称')
    return
  }

  const html = templateEditorInstance.value?.getHTML?.() || form.value.templateContent.contentHtml || ''
  if (!isMeaningfulHtml(html)) {
    ElMessage.warning('请输入模板内容')
    return
  }

  saving.value = true
  try {
    const payload: RequirementTemplateSave = {
      id: isEditMode.value ? form.value.id : undefined,
      requirementTypeCode: form.value.requirementTypeCode,
      templateName: form.value.templateName,
      templateContent: {
        contentHtml: html,
      },
      isDefault: form.value.isDefault,
      sortOrder: form.value.sortOrder,
    }
    await saveRequirementTemplate(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadTemplates()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function handleSetDefault(row: RequirementTemplate) {
  if (row.isDefault === 1) {
    ElMessage.info('该模板已是默认模板')
    return
  }
  try {
    await setDefaultRequirementTemplate(row.id!)
    ElMessage.success('已设为默认模板')
    loadTemplates()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleToggleStatus(row: RequirementTemplate) {
  const newStatus = row.isActive === 1 ? 0 : 1
  try {
    await toggleRequirementTemplateStatus(row.id!, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    loadTemplates()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '操作失败'))
  }
}

async function handleDelete(row: RequirementTemplate) {
  try {
    await ElMessageBox.confirm('确定删除该模板吗？', '提示', {
      type: 'warning'
    })
    await deleteRequirementTemplate(row.id!)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(resolveErrorMessage(error, '删除失败'))
    }
  }
}
</script>

<style scoped lang="scss">
.form-tip {
  margin-left: 8px;
  color: var(--color-muted-text);
  font-size: 12px;
}

.template-editor {
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
}

:deep(.template-editor .isle-editor) {
  min-height: 320px;
}

:deep(.template-editor .ProseMirror) {
  min-height: 260px;
  padding: 16px;
}
</style>
