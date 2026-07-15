<template>
  <PageContainer :breadcrumb="false">
    <div class="create-page">
    <el-row :gutter="20" class="form-container">
      <el-col :xs="24" :lg="16" class="left-panel">
        <el-card class="form-card">
          <template #header>
            <div class="card-titlebar">
              <div class="card-title">需求内容</div>
              <div class="card-subtitle">填写标题与描述，并按需关联需求与附件</div>
            </div>
          </template>
          <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top" @submit.prevent>
            <!-- 需求类型 & 优先级 -->
            <div class="inline-fields">
              <el-form-item label="需求类型" prop="type" class="inline-item">
                <el-select
                  v-if="!isEditMode"
                  v-model="formData.type"
                  placeholder="请选择需求类型"
                  style="width: 100%"
                >
                  <el-option
                    v-for="type in configTypes"
                    :key="type.code"
                    :label="type.name"
                    :value="type.code"
                  >
                    <span class="priority-option">
                      <span v-if="type.color" class="priority-dot" :style="{ backgroundColor: type.color }"></span>
                      {{ type.name }}
                    </span>
                  </el-option>
                </el-select>
                <el-input
                  v-else
                  :model-value="selectedTypeLabel"
                  readonly
                  placeholder="请先在需求配置中维护需求类型"
                >
                  <template #prefix>
                    <span v-if="selectedTypeColor" class="type-dot" :style="{ backgroundColor: selectedTypeColor }"></span>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="优先级" prop="priority" class="inline-item">
                <el-select v-model="formData.priority" placeholder="请选择" style="width: 100%">
                  <el-option
                    v-for="p in configPriorities"
                    :key="p.code"
                    :label="p.name"
                    :value="p.code"
                  >
                    <span class="priority-option">
                      <span v-if="p.color" class="priority-dot" :style="{ backgroundColor: p.color }"></span>
                      {{ p.name }}
                    </span>
                  </el-option>
                </el-select>
              </el-form-item>
            </div>

            <!-- 需求标题 -->
            <el-form-item label="需求标题" prop="title">
              <el-input
                v-model="formData.title"
                placeholder="请输入需求标题"
                maxlength="200"
                size="large"
                clearable
              />
            </el-form-item>

            <!-- 需求描述 -->
            <el-form-item label="需求描述" prop="description" class="description-item">
              <div class="editor-wrapper">
                <IsleEditorToolbar v-if="editorInstance" :editor="editorInstance" />
                <IsleEditor v-model="formData.description" :extensions="editorExtensions" locale="zh" @create="onEditorCreate" />
              </div>
            </el-form-item>

            <!-- 关联需求 & 附件 -->
            <div class="extra-section">
              <!-- 关联需求 -->
              <div class="extra-row">
                <div class="extra-row-label">关联需求</div>
                <div class="extra-row-content">
                  <el-button size="small" @click="showRelationDialog = true">
                    <el-icon><Plus /></el-icon>
                    添加
                  </el-button>
                  <span v-if="relatedRequirements.length > 0" class="relation-count">已关联 {{ relatedRequirements.length }} 个</span>
                </div>
              </div>
              <div v-if="relatedRequirements.length > 0" class="relation-chips">
                <div v-for="r in relatedRequirements" :key="r.id" class="relation-chip">
                  <el-select
                    v-model="r.relationType"
                    size="small"
                    class="relation-chip__type"
                  >
                    <el-option label="阻塞" value="blocks" />
                    <el-option label="被阻塞" value="blocked_by" />
                    <el-option label="包含" value="contains" />
                    <el-option label="被包含" value="contained_by" />
                    <el-option label="相关" value="relates_to" />
                  </el-select>
                  <el-tooltip v-if="r.title" :content="r.title" placement="top" :show-after="300">
                    <span class="relation-chip__title">{{ r.title }}</span>
                  </el-tooltip>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    class="relation-chip__remove"
                    aria-label="移除关联"
                    @click="removeRelation(r)"
                  />
                </div>
              </div>

              <!-- 附件上传区 -->
              <AttachmentUploader
                v-model="formData.attachments"
                @preview="handleAttachmentPreview"
              />
            </div>
          </el-form>
        </el-card>
      </el-col>

      <!-- Right Panel: Info Cards -->
      <el-col :xs="24" :lg="8" class="right-panel">
        <!-- 基础信息 -->
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>基础信息</span>
            </div>
          </template>
          <el-form ref="infoFormRef" :model="formData" :rules="formRules" label-position="top">
            <el-form-item label="所属项目" prop="projectId">
              <el-select
                v-model="formData.projectId"
                placeholder="请选择所属项目"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="project in projects"
                  :key="project.id"
                  :label="projectOptionLabel(project)"
                  :value="project.id"
                  :disabled="isProjectExpired(project)"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="负责人" prop="assigneeId">
              <el-select
                v-model="formData.assigneeId"
                placeholder="请选择负责人"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="user in proposerUsers"
                  :key="user.id"
                  :label="userDisplayName(user)"
                  :value="user.id"
                >
                  <div class="user-option">
                    <el-avatar :size="24" :src="user.avatar">{{ userDisplayName(user)[0] }}</el-avatar>
                    <span>{{ userDisplayName(user) }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="showCcField" label="抄送人">
              <el-select
                v-model="formData.ccUserIds"
                placeholder="默认留空，可按需选择"
                clearable
                multiple
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="user in ccUsers"
                  :key="user.id"
                  :label="userDisplayName(user)"
                  :value="user.id"
                >
                  <div class="user-option">
                    <el-avatar :size="24" :src="user.avatar">{{ userDisplayName(user)[0] }}</el-avatar>
                    <span>{{ userDisplayName(user) }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item v-if="showCurrentStatusField" label="所属迭代">
              <el-select v-model="formData.iterationId" placeholder="请选择" clearable style="width: 100%">
                <el-option
                  v-for="iteration in iterations"
                  :key="iteration.id"
                  :label="iteration.name"
                  :value="iteration.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item v-if="showCurrentStatusField" label="当前状态">
              <el-input :model-value="currentRequirement?.status || '-'" readonly />
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 时间 -->
        <el-card v-if="showTimeCard" class="info-card">
          <template #header>
            <div class="card-header">
              <span>时间</span>
            </div>
          </template>
          <el-form label-position="top">
            <el-form-item v-if="shouldShowField('startDate')" label="开始时间">
              <el-date-picker
                v-model="formData.startDate"
                type="date"
                placeholder="请选择"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item v-if="shouldShowField('dueDate')" label="期望上线日期">
              <el-input v-if="isDueDateReadOnly" :model-value="formData.dueDate || '-'" readonly />
              <el-date-picker
                v-else
                v-model="formData.dueDate"
                type="date"
                placeholder="请选择"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item v-if="shouldShowField('estimatedHours')" label="估算工时(小时)">
              <el-input-number
                v-model="formData.estimatedHours"
                :min="0"
                :precision="1"
                :step="0.5"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item v-if="showCurrentStatusField" label="创建时间">
              <el-input :model-value="formatDate(currentRequirement?.createdAt)" readonly />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- Action Bar -->
    <div class="action-bar">
      <el-button @click="handleCancel">取消</el-button>
      <template v-if="isDraftMode">
        <el-button v-permission="'button:requirement:draft'" :loading="submitting" @click="handleSaveDraft">保存草稿</el-button>
        <el-button v-permission="'button:requirement:submit'" type="primary" :loading="submitting" @click="handleSubmit">
          提交审核
        </el-button>
      </template>
      <el-button v-else v-permission="'button:requirement:submit'" type="primary" :loading="submitting" @click="handleSubmit">
        {{ submitButtonText }}
      </el-button>
    </div>

    <!-- Attachment Preview Dialog (与知识库管理共用 FilePreviewDialog，支持 image / text / office 统一处理) -->
    <FilePreviewDialog
      v-if="previewFile"
      v-model="previewVisible"
      :file-name="previewFile.name"
      :file-type="getFileExt(previewFile.name)"
      :file-id="previewFile.fileId || undefined"
      :download-url="previewFile.url"
    />

    <!-- Relation Dialog -->
    <el-dialog v-model="showRelationDialog" title="添加关联需求" width="600px">
      <div class="relation-search">
        <el-input
          v-model="relationSearchText"
          placeholder="搜索需求标题..."
          clearable
          :prefix-icon="Search"
        />
      </div>
      <el-table
        :data="filteredRequirements"
        size="small"
        @selection-change="handleRelationSelection"
        class="relation-select-table"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="showRelationDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddRelation">确定</el-button>
      </template>
    </el-dialog>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, h, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  Plus, Delete, Search, Document
} from '@element-plus/icons-vue'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import { addLocale } from '@isle-editor/core'
import Image from '@tiptap/extension-image'
import '@isle-editor/vue3/dist/style.css'

// 在编辑器实例化前注册中文 locale（补充新增字体翻译）
addLocale('zh', {
  isleEditor: '岛屿编辑器',
  fontFamily: '字体',
  fontSize: '字号',
  textStyle: '文字样式',
  background: '背景颜色',
  color: '文字颜色',
  lineHeight: '行高',
  letterSpacing: '字间距',
  bold: '加粗',
  italic: '斜体',
  underline: '下划线',
  strike: '删除线',
  code: '行内代码',
  link: '链接',
  linkPlaceholder: '请输入链接',
  openInNewTab: '在新标签页中打开',
  unlink: '取消链接',
  subscript: '下标',
  superscript: '上标',
  heading: '标题',
  heading1: '一级标题',
  heading2: '二级标题',
  heading3: '三级标题',
  heading4: '四级标题',
  heading5: '五级标题',
  heading6: '六级标题',
  paragraph: '段落',
  blockquote: '引用',
  bulletList: '无序列表',
  orderedList: '有序列表',
  taskList: '任务列表',
  codeBlock: '代码块',
  divider: '分割线',
  indent: '增加缩进',
  outdent: '减少缩进',
  hardBreak: '换行',
  undo: '撤销',
  redo: '重做',
  textAlign: '文字对齐',
  alignLeft: '左对齐',
  alignCenter: '居中对齐',
  alignRight: '右对齐',
  alignJustify: '两端对齐',
  table: '表格',
  edit: '编辑',
  textClear: '清除',
  copy: '复制',
  paste: '粘贴',
  cancel: '取消',
  open: '打开',
  empty: '空',
  fonts: {
    Default: '默认字体',
    MicrosoftYaHei: '微软雅黑',
    SimSun: '宋体',
    SimHei: '黑体',
    KaiTi: '楷体',
    FangSong: '仿宋',
    PingFangSC: '苹方',
    HiraginoSansGB: '冬青黑体',
    SourceHanSansSC: '思源黑体',
    STXihei: '华文细黑',
    STZhongsong: '华文中宋',
    Arial: 'Arial',
    TimesNewRoman: 'Times New Roman',
    CourierNew: 'Courier New',
    Georgia: 'Georgia',
  },
  sizes: {
    tiny: '超小',
    small: '小',
    normal: '中',
    large: '大',
    huge: '超大',
  },
  colors: {
    defaultColor: '默认颜色',
    baseColor: '基础颜色',
    standardColor: '标准颜色',
    recentUse: '最近使用',
    palette: '调色板',
  },
  placeholder: '写点什么 ...',
})

import { requirementApi, projectApi, relationApi, userApi, iterationApi } from '@/api'
import { getRequirementTemplateByType, getRequirementTemplatesByType } from '@/api/modules/requirement'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { downloadRequirementAttachment, uploadRequirementAttachment } from '@/api/modules/file'
import { usePermission } from '@/composables/usePermission'
import type { RelationItem } from '@/api/modules/relation'
import { buildRichTextImagePreviewUrl, hydrateRichTextImageHtml, serializeRichTextImageHtml } from '@/utils/richTextFileImage'
import { formatDate, getFileExt, normalizeText, stripPriorityPrefix } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'
import PageContainer from '@/components/common/PageContainer.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import FilePreviewDialog from '@/components/document/FilePreviewDialog.vue'
import { useUserStore } from '@/stores'
import type { NextNodeOption, Requirement, RequirementAttachment, RequirementTemplate, TemplateSection } from '@/types/requirement'
import type { OrgNode, User } from '@/types/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { hasPermission } = usePermission()

const formRef = ref<FormInstance>()
const infoFormRef = ref<FormInstance>()
const submitting = ref(false)
const showRelationDialog = ref(false)
const relationSearchText = ref('')
const selectedRelations = ref<any[]>([])
const createFormVisibleFields = ref<string[]>([])
const createFormRequiredFields = ref<string[]>([])
const currentRequirement = ref<Requirement | null>(null)

// Data
const projects = ref<any[]>([])
const users = ref<User[]>([])
const orgTree = ref<OrgNode[]>([])
const iterations = ref<any[]>([])
const allRequirements = ref<any[]>([])

// Related requirements
interface EditableRelationItem {
  id: number
  title: string
  type?: string | null
  status?: string | null
  priority?: string | null
  relationType: string
  relationId?: number
}

const relatedRequirements = ref<EditableRelationItem[]>([])

// Requirement config types and priorities
const configTypes = ref<any[]>([])
// 全量需求类型（含未绑定工作流的），用于在无可用类型时区分原因
const allConfigTypes = ref<any[]>([])
const configPriorities = ref<any[]>([])
const templateApplying = ref(false)

// 无可用需求类型时的原因文案；为空表示存在可用类型
const noAvailableTypeReason = computed(() => {
  if (configTypes.value.length > 0) return ''
  if (allConfigTypes.value.length === 0) {
    return '系统尚未配置需求类型，请联系系统管理员配置需求类型后重试'
  }
  return '需求类型未绑定启用的工作流，请联系系统管理员绑定启用的工作流后重试'
})

function isDescriptionEmpty(html: string) {
  const text = html.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim()
  return !text
}

function isEditorEffectivelyEmpty(editor: any): boolean {
  if (!editor) return true
  try {
    if (typeof editor.isEmpty === 'boolean') {
      if (!editor.isEmpty) return false
    }
    const text = (editor.getText?.() || '').trim()
    if (text) return false
    const html = (editor.getHTML?.() || '').trim()
    // 只包含空段落/占位符/换行视为空
    return html === ''
      || /^<p[^>]*>(\s|&nbsp;|<br\s*\/?>)*<\/p>$/i.test(html)
  } catch {
    return true
  }
}

function buildTemplateDescription(sections: TemplateSection[]) {
  return sections.map((section) => {
    if (section.fieldType === 'richtext') {
      if (section.defaultContent) return section.defaultContent
      return `<h3>${section.sectionName}</h3><p>${section.placeholder || '请填写...'}</p>`
    }
    if (section.fieldType === 'textarea' || section.fieldType === 'text') {
      return `<h3>${section.sectionName}</h3><p>${section.placeholder || '请填写...'}</p>`
    }
    return ''
  }).filter(Boolean).join('')
}

function resolveTemplateContent(template?: RequirementTemplate | null) {
  if (!template?.templateContent) return ''

  const directContent = template.templateContent.contentHtml?.trim()
  if (directContent) return directContent

  const sections = template.templateContent.sections || []
  if (sections.length === 0) return ''
  return buildTemplateDescription(sections)
}

async function applyRequirementTemplate(typeCode?: string, force = false) {
  if (!typeCode || isEditMode.value || templateApplying.value) return
  templateApplying.value = true
  try {
    // 获取该类型下所有启用的模板
    const templateList = await getRequirementTemplatesByType(typeCode)
    let template

    if (templateList.length === 0) {
      // 无模板，使用默认模板
      template = await getRequirementTemplateByType(typeCode)
    } else if (templateList.length === 1) {
      // 只有一个模板，直接使用
      template = templateList[0]
    } else {
      // 多个模板，弹出选择对话框
      if (!force && !isDescriptionEmpty(formData.description)) {
        try {
          await ElMessageBox.confirm('应用模板将覆盖当前描述内容，是否继续？', '应用需求模板', {
            confirmButtonText: '继续',
            cancelButtonText: '取消',
            type: 'warning',
          })
        } catch {
          return
        }
      }

      // 用 ElMessageBox.prompt 让用户输入选择的序号
      const defaultTemplate = templateList.find(t => t.isDefault === 1)
      const optionText = templateList.map((t, i) =>
        `${i + 1}. ${t.templateName}${t.isDefault === 1 ? '（默认）' : ''}`
      ).join('\n')

      try {
        const { value } = await ElMessageBox.prompt(
          `可选模板：\n${optionText}\n\n请输入序号选择模板`,
          '选择需求模板',
          {
            confirmButtonText: '应用',
            cancelButtonText: '取消',
            inputValue: defaultTemplate ? String(templateList.indexOf(defaultTemplate) + 1) : '1',
            inputPattern: new RegExp(`^[1-${templateList.length}]$`),
            inputErrorMessage: `请输入 1-${templateList.length} 之间的数字`,
          }
        )
        const index = parseInt(value) - 1
        template = templateList[index] || defaultTemplate || templateList[0]
      } catch {
        // 用户取消选择，使用默认模板
        template = defaultTemplate || templateList[0]
      }
    }

    if (!template) return
    const nextDescription = resolveTemplateContent(template)
    if (!nextDescription) return

    if (!force && !isDescriptionEmpty(formData.description)) {
      try {
        await ElMessageBox.confirm('应用模板将覆盖当前描述内容，是否继续？', '应用需求模板', {
          confirmButtonText: '应用',
          cancelButtonText: '取消',
          type: 'warning',
        })
      } catch {
        return
      }
    }
    formData.description = nextDescription
    // 强制更新编辑器内容
    if (editorInstance.value) {
      editorInstance.value.commands.setContent(nextDescription)
    }
  } catch (error) {
    // 无模板或加载失败时使用默认空白描述
    console.warn('[Template] 加载失败，使用默认空白描述', error)
  } finally {
    templateApplying.value = false
  }
}

const editId = computed(() => {
  const q = route.query.id
  return q ? Number(q) : 0
})

const parentId = computed(() => {
  const q = route.query.parentId
  if (q) return Number(q)
  return currentRequirement.value?.parentId || undefined
})

const isEditMode = computed(() => editId.value > 0)
const isDraftMode = computed(() => !isEditMode.value || currentRequirement.value?.isDraft === true)
const CREATE_VISIBLE_FIELD_FALLBACK = ['dueDate']
const FIELD_NAME_ALIASES: Record<string, string> = {
  startDate: 'startDate',
  开始时间: 'startDate',
  开始日期: 'startDate',
  dueDate: 'dueDate',
  截止时间: 'dueDate',
  截止日期: 'dueDate',
  期望上线时间: 'dueDate',
  estimatedHours: 'estimatedHours',
  估算工时: 'estimatedHours',
  ccUserIds: 'ccUserIds',
  抄送人: 'ccUserIds',
}

const formData = reactive({
  projectId: undefined as number | undefined,
  title: '',
  description: '',
  type: '' as string | undefined,
  priority: '' as string | undefined,
  assigneeId: undefined as number | undefined,
  ccUserIds: [] as number[],
  iterationId: undefined as number | undefined,
  startDate: '' as string | undefined,
  dueDate: '' as string | undefined,
  estimatedHours: undefined as number | undefined,
  attachments: [] as RequirementAttachment[],
})

const formRules = computed<FormRules>(() => ({
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择需求类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  assigneeId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
}))

const selectedType = computed(() => configTypes.value.find((item) => item.code === formData.type))
const selectedTypeLabel = computed(() => selectedType.value?.name || '')
const selectedTypeColor = computed(() => selectedType.value?.color || '')
const showTimeCard = computed(() => isEditMode.value || shouldShowField('dueDate'))
const showCurrentStatusField = computed(() => isEditMode.value && currentRequirement.value?.isDraft !== true)
const isApprovalMode = computed(() => showCurrentStatusField.value && currentRequirement.value?.canApprove === true)
const isDueDateReadOnly = computed(() => showCurrentStatusField.value)
const submitButtonText = computed(() => isApprovalMode.value ? '提交审核' : '保存')
const showCcField = computed(() => {
  if (isEditMode.value && currentRequirement.value?.isDraft !== true) {
    return true
  }
  return createFormVisibleFields.value.some((item) => normalizeFieldName(item) === 'ccUserIds')
})
const currentUserId = computed(() => userStore.userInfo?.id)
const currentUser = computed(() => (
  users.value.find((item) => item.id === currentUserId.value)
  || (userStore.userInfo
    ? {
        id: userStore.userInfo.id,
        username: userStore.userInfo.username,
        realName: userStore.userInfo.realName,
        email: userStore.userInfo.email || null,
        phone: null,
        avatar: userStore.userInfo.avatar || null,
        status: 'active',
        orgId: null,
        regionId: userStore.userInfo.regionId || null,
        departmentId: userStore.userInfo.departmentId || null,
        createdAt: '',
        updatedAt: '',
      } as User
    : null)
))

const editorExtensions = [
  RichTextKit.configure({
    placeholder: { placeholder: '请输入需求描述...' },
    fontFamily: {
      fonts: [
        { label: 'Default', value: '' },
        { label: 'MicrosoftYaHei', value: '"Microsoft YaHei", "PingFang SC", sans-serif' },
        { label: 'SimSun', value: '"SimSun", "STSong", serif' },
        { label: 'SimHei', value: '"SimHei", "STHeiti", sans-serif' },
        { label: 'KaiTi', value: '"KaiTi", "STKaiti", serif' },
        { label: 'FangSong', value: '"FangSong", "STFangsong", serif' },
        { label: 'PingFangSC', value: '"PingFang SC", "Microsoft YaHei", sans-serif' },
        { label: 'HiraginoSansGB', value: '"Hiragino Sans GB", "Microsoft YaHei", sans-serif' },
        { label: 'SourceHanSansSC', value: '"Source Han Sans SC", "Noto Sans CJK SC", sans-serif' },
        { label: 'STXihei', value: '"STXihei", "华文细黑", sans-serif' },
        { label: 'STZhongsong', value: '"STZhongsong", "华文中宋", serif' },
        { label: 'Arial', value: 'Arial, "Helvetica Neue", Helvetica, sans-serif' },
        { label: 'TimesNewRoman', value: '"Times New Roman", TimesNewRoman, serif' },
        { label: 'CourierNew', value: '"Courier New", Courier, monospace' },
        { label: 'Georgia', value: 'Georgia, serif' },
      ],
    },
    fontSize: {
      type: 'complex',
    },
    table: {
      resizable: true,
      HTMLAttributes: {
        class: 'requirement-editor-table',
      },
    },
  }),
  Image.configure({
    inline: false,
    allowBase64: true,
    resize: {
      enabled: true,
      directions: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
      minWidth: 100,
      minHeight: 100,
      alwaysPreserveAspectRatio: false,
    },
    HTMLAttributes: {
      class: 'requirement-editor-image',
    },
  }),
]

const editorInstance = ref<any>(null)

function onEditorCreate({ editor }: { editor: any }) {
  editorInstance.value = editor
  if (formData.description && isEditMode.value) {
    editor.commands.setContent(hydrateRichTextImageHtml(formData.description))
  }
  // 编辑器粘贴功能由 AttachmentUploader 组件统一处理
}

watch(
  () => formData.description,
  (val) => {
    if (editorInstance.value && val && editorInstance.value.getHTML() !== val) {
      // 编辑模式：始终同步；新建模式：仅当编辑器实质为空时同步（避免覆盖用户已输入内容）
      if (isEditMode.value || isEditorEffectivelyEmpty(editorInstance.value)) {
        editorInstance.value.commands.setContent(val)
      }
    }
  },
)

watch(
  () => formData.projectId,
  (projectId) => {
    loadIterations(projectId)
    loadRequirements(projectId)
    loadCreateFormConfig(projectId)
  },
  { immediate: true },
)

watch(
  editId,
  (value) => {
    if (value > 0) {
      void loadEditData(value)
      return
    }
    currentRequirement.value = null
  },
  { immediate: true },
)

watch(
  currentUserId,
  (value) => {
    if (!isEditMode.value && value && !formData.assigneeId) {
      formData.assigneeId = value
    }
  },
  { immediate: true },
)

watch(
  showCcField,
  (visible) => {
    const shouldReset = !visible && (!isEditMode.value || currentRequirement.value?.isDraft === true)
    if (shouldReset && formData.ccUserIds.length > 0) {
      formData.ccUserIds = []
    }
  },
  { immediate: true },
)

// Filtered requirements for relation dialog
const filteredRequirements = computed(() => {
  const candidates = allRequirements.value.filter((requirement) => {
    if (editId.value > 0 && requirement.id === editId.value) {
      return false
    }
    return !relatedRequirements.value.some((related) => related.id === requirement.id)
  })
  if (!relationSearchText.value) return candidates
  return candidates.filter(r =>
    r.title.toLowerCase().includes(relationSearchText.value.toLowerCase())
  )
})

const orgNodeMap = computed(() => {
  const map = new Map<number, OrgNode>()
  const walk = (nodes: OrgNode[]) => {
    nodes.forEach((node) => {
      map.set(node.id, node)
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(orgTree.value)
  return map
})

function userDisplayName(user: User) {
  return user.realName || user.username
}

function resolveUserOrgId(user?: User | null) {
  if (!user) return null
  return user.orgId || user.departmentId || user.regionId || null
}

function resolveOrgNode(orgId?: number | null) {
  return orgId ? orgNodeMap.value.get(orgId) || null : null
}

function isSameLevelOrg(candidateOrgId?: number | null, referenceOrgId?: number | null) {
  const candidate = resolveOrgNode(candidateOrgId)
  const reference = resolveOrgNode(referenceOrgId)
  if (!candidate || !reference) return false
  return candidate.level === reference.level && candidate.parentId === reference.parentId
}

function isDescendantOrg(candidateOrgId?: number | null, referenceOrgId?: number | null) {
  const candidate = resolveOrgNode(candidateOrgId)
  const reference = resolveOrgNode(referenceOrgId)
  if (!candidate || !reference || !candidate.path || !reference.path) return false
  return candidate.id !== reference.id && candidate.path.startsWith(reference.path)
}

function mergeSelectedUsers(baseUsers: User[], selectedIds: Array<number | undefined>) {
  const map = new Map(baseUsers.map((item) => [item.id, item]))
  selectedIds
    .filter((id): id is number => typeof id === 'number')
    .forEach((id) => {
      const selected = users.value.find((item) => item.id === id)
      if (selected) {
        map.set(selected.id, selected)
      }
    })
  return Array.from(map.values())
}

const proposerUsers = computed(() => {
  const activeUsers = users.value.filter((item) => item.status === 'active')
  const referenceUser = currentUser.value
  const referenceOrgId = resolveUserOrgId(referenceUser)

  const filtered = activeUsers.filter((candidate) => {
    if (candidate.id === currentUserId.value) return true
    if (!referenceUser) return false

    const candidateOrgId = resolveUserOrgId(candidate)
    if (!candidateOrgId || !referenceOrgId) return false

    return candidateOrgId === referenceOrgId
      || isSameLevelOrg(candidateOrgId, referenceOrgId)
      || isDescendantOrg(candidateOrgId, referenceOrgId)
  })

  return mergeSelectedUsers(filtered, [formData.assigneeId])
})

const ccUsers = computed(() => {
  const activeUsers = users.value.filter((item) => item.status === 'active')
  const referenceUser = currentUser.value
  if (!referenceUser) {
    return mergeSelectedUsers(activeUsers, formData.ccUserIds)
  }

  const filtered = activeUsers.filter((candidate) => {
    if (candidate.id === currentUserId.value) return true

    const sameDepartment = !!referenceUser.departmentId && candidate.departmentId === referenceUser.departmentId
    const sameOrg = !!referenceUser.orgId && candidate.orgId === referenceUser.orgId
    return sameDepartment || sameOrg
  })

  return mergeSelectedUsers(filtered, formData.ccUserIds)
})

// Load data
async function loadProjects() {
  try {
    const res = await projectApi.getProjectList({ pageNum: 1, pageSize: 100 }) as any
    projects.value = res?.list || []
    if (formData.projectId && formData.projectId > 0 && !projects.value.some((project: any) => project.id === formData.projectId)) {
      formData.projectId = undefined
    }
  } catch {
    projects.value = []
  }
}

async function loadOrgTree() {
  try {
    const res = await userApi.getOrgTree() as any
    orgTree.value = Array.isArray(res) ? res : []
  } catch {
    orgTree.value = []
    // ignore
  }
}

async function loadUsers() {
  try {
    const res = await userApi.getUserList({ pageNum: 1, pageSize: 1000 }) as any
    users.value = res?.list || []
    if (!isEditMode.value && !formData.assigneeId && currentUserId.value) {
      formData.assigneeId = currentUserId.value
    }
  } catch {
    users.value = []
    // ignore
  }
}

async function loadIterations(projectId = formData.projectId) {
  try {
    if (!projectId) {
      iterations.value = []
      formData.iterationId = undefined
      return
    }
    const res = await iterationApi.getIterationList(projectId) as any
    iterations.value = Array.isArray(res) ? res : []
    if (!iterations.value.some((iteration: any) => iteration.id === formData.iterationId)) {
      formData.iterationId = undefined
    }
  } catch {
    iterations.value = []
    // ignore
  }
}

async function loadRequirements(projectId = formData.projectId) {
  try {
    if (!projectId) {
      allRequirements.value = []
      return
    }
    const res = await requirementApi.getRequirementList({ pageNum: 1, pageSize: 100, projectId }) as any
    allRequirements.value = res?.list || []
  } catch {
    allRequirements.value = []
    // ignore
  }
}

function mapRelationToEditableItem(relation: RelationItem): EditableRelationItem {
  return {
    id: relation.targetId,
    title: relation.targetTitle,
    type: relation.targetType,
    status: relation.targetStatus,
    priority: relation.targetPriority,
    relationType: relation.relationType,
    relationId: relation.id,
  }
}

async function loadRelations(requirementId: number) {
  try {
    const relations = await relationApi.getRelationList(requirementId)
    relatedRequirements.value = Array.isArray(relations)
      ? relations.map(mapRelationToEditableItem)
      : []
  } catch {
    relatedRequirements.value = []
    ElMessage.error('加载关联需求失败')
  }
}

function applyRequirementToForm(data: Requirement) {
  currentRequirement.value = data
  formData.projectId = data.projectId && data.projectId > 0 ? data.projectId : undefined
  formData.title = data.title
  formData.description = hydrateRichTextImageHtml(data.description)
  formData.type = data.type
  formData.priority = data.priority
  formData.assigneeId = data.assigneeId || data.creatorId || currentUserId.value || undefined
  formData.iterationId = data.iterationId || undefined
  formData.startDate = data.startDate || undefined
  formData.dueDate = data.dueDate || undefined
  formData.estimatedHours = data.estimatedHours || undefined
  formData.ccUserIds = Array.isArray(data.ccUserIds) ? data.ccUserIds : []
  formData.attachments = Array.isArray(data.attachments) ? data.attachments : []
}

async function loadEditData(targetId = editId.value) {
  if (!targetId) {
    currentRequirement.value = null
    relatedRequirements.value = []
    return
  }
  try {
    const data = await requirementApi.getRequirementById(targetId) as Requirement
    applyRequirementToForm(data)
    await loadRelations(targetId)
  } catch {
    ElMessage.error('加载需求数据失败')
  }
}

function shouldShowField(field: string) {
  // 编辑已提交的需求时显示所有字段，草稿箱编辑时遵循模板配置
  if (showCurrentStatusField.value) return true
  if (field === 'startDate' || field === 'estimatedHours') return false
  if (field === 'dueDate') return true
  if (createFormVisibleFields.value.length === 0) return false
  return createFormVisibleFields.value.some((item) => normalizeFieldName(item) === field)
}

function formatAttachmentTime(time: string | number | Date) {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  const y = d.getFullYear()
  const m = pad(d.getMonth() + 1)
  const day = pad(d.getDate())
  const h = pad(d.getHours())
  const min = pad(d.getMinutes())
  return `${y}-${m}-${day} ${h}:${min}`
}

const previewVisible = ref(false)
const previewFile = ref<RequirementAttachment | null>(null)

function handleAttachmentPreview(file: RequirementAttachment) {
  if (!file.fileId && !file.url) {
    ElMessage.warning('该附件暂不支持预览')
    return
  }
  previewFile.value = file
  previewVisible.value = true
}

function normalizeFieldName(field: string) {
  return FIELD_NAME_ALIASES[field] || field
}

function normalizeDateValue(value?: string) {
  return value || undefined
}

function normalizeNumberValue(value?: number) {
  return typeof value === 'number' ? value : undefined
}

function isProjectExpired(project: { status?: string | null; endDate?: string | null }) {
  if (project.status === 'expired') return true
  if (!project.endDate) return false
  return new Date(project.endDate).getTime() < Date.now() - 24 * 60 * 60 * 1000
}

function projectOptionLabel(project: { name: string; status?: string | null; endDate?: string | null }) {
  return isProjectExpired(project) ? `${project.name}（已截止）` : project.name
}

// Relation operations
function handleRelationSelection(val: any[]) {
  selectedRelations.value = val
}

function confirmAddRelation() {
  for (const req of selectedRelations.value) {
    if (!relatedRequirements.value.find(r => r.id === req.id)) {
      relatedRequirements.value.push({ ...req, relationType: 'relates_to' })
    }
  }
  showRelationDialog.value = false
  selectedRelations.value = []
  relationSearchText.value = ''
}

function removeRelation(row: any) {
  relatedRequirements.value = relatedRequirements.value.filter(r => r.id !== row.id)
}

async function syncRelations(requirementId: number) {
  if (!isEditMode.value && relatedRequirements.value.length === 0) {
    return
  }

  const existingRelations = await relationApi.getRelationList(requirementId)
  const existingKeyMap = new Map(existingRelations.map((relation) => [
    `${relation.targetId}__${relation.relationType}`,
    relation,
  ]))
  const currentKeySet = new Set(
    relatedRequirements.value.map((relation) => `${relation.id}__${relation.relationType}`),
  )

  for (const relation of existingRelations) {
    const key = `${relation.targetId}__${relation.relationType}`
    if (!currentKeySet.has(key)) {
      await relationApi.deleteRelation(requirementId, relation.id)
    }
  }

  for (const relation of relatedRequirements.value) {
    const key = `${relation.id}__${relation.relationType}`
    if (!existingKeyMap.has(key)) {
      await relationApi.createRelation(requirementId, {
        targetId: relation.id,
        relationType: relation.relationType,
      })
    }
  }

  await loadRelations(requirementId)
}

function ensureDefaultAssignee() {
  if (!formData.assigneeId && currentUserId.value) {
    formData.assigneeId = currentUserId.value
  }
}

async function validateForms() {
  ensureDefaultAssignee()
  const basicValid = await formRef.value?.validate().catch(() => false)
  const infoValid = await infoFormRef.value?.validate().catch(() => false)
  return !!basicValid && !!infoValid
}

function syncDescriptionFromEditor() {
  if (editorInstance.value) {
    try {
      const html = editorInstance.value.getHTML()
      if (typeof html === 'string') {
        formData.description = html
      }
    } catch {
      // 编辑器尚未就绪时保持原值
    }
  }
}

function buildRequirementPayload() {
  syncDescriptionFromEditor()
  const ccUserIds = showCcField.value ? formData.ccUserIds : []
  const payload: any = {
    projectId: formData.projectId,
    title: formData.title,
    description: serializeRichTextImageHtml(formData.description),
    type: formData.type,
    priority: formData.priority,
    assigneeId: formData.assigneeId,
    ccUserIds,
    attachments: formData.attachments,
    parentId: parentId.value,
  }

  if (isEditMode.value) {
    payload.iterationId = formData.iterationId
    payload.ccUserIds = ccUserIds
    payload.startDate = normalizeDateValue(formData.startDate)
    if (!isDueDateReadOnly.value) {
      payload.dueDate = normalizeDateValue(formData.dueDate)
    }
    payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
    return payload
  }

  if (shouldShowField('startDate')) payload.startDate = normalizeDateValue(formData.startDate)
  if (shouldShowField('dueDate')) payload.dueDate = normalizeDateValue(formData.dueDate)
  if (shouldShowField('estimatedHours')) payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
  return payload
}

function buildDraftPayload() {
  syncDescriptionFromEditor()
  const ccUserIds = showCcField.value ? formData.ccUserIds : []
  const payload: any = {
    projectId: formData.projectId,
    title: formData.title,
    description: serializeRichTextImageHtml(formData.description),
    type: formData.type,
    priority: formData.priority,
    assigneeId: formData.assigneeId,
    ccUserIds,
    attachments: formData.attachments,
    parentId: parentId.value ?? currentRequirement.value?.parentId,
  }

  if (shouldShowField('startDate') || isEditMode.value) payload.startDate = normalizeDateValue(formData.startDate)
  if (shouldShowField('dueDate') || isEditMode.value) payload.dueDate = normalizeDateValue(formData.dueDate)
  if (shouldShowField('estimatedHours') || isEditMode.value) payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
  return payload
}

async function persistDraft(showSuccess = true, skipRouterUpdate = false) {
  const payload = buildDraftPayload()

  if (isEditMode.value && currentRequirement.value?.isDraft) {
    await requirementApi.updateRequirementDraft(editId.value, {
      ...payload,
      id: editId.value,
      version: currentRequirement.value.version,
    })
    const latest = await requirementApi.getRequirementById(editId.value) as Requirement
    applyRequirementToForm(latest)
    if (showSuccess) {
      ElMessage.success('草稿已保存')
    }
    return latest
  }

  const draftId = await requirementApi.createRequirementDraft(payload)
  const latest = await requirementApi.getRequirementById(draftId) as Requirement
  applyRequirementToForm(latest)
  // 只有在非跳过路由更新的情况下才更新 URL
  if (!skipRouterUpdate) {
    await router.replace({ name: 'RequirementCreate', query: { id: draftId } })
  }
  if (showSuccess) {
    ElMessage.success('草稿已保存')
  }
  return latest
}

async function chooseNextNode(options: NextNodeOption[]) {
  if (options.length === 0) {
    ElMessage.error('当前工作流未配置可提交的下一环节')
    return null
  }

  if (options.length === 1) {
    return options[0].nodeId
  }

  let selectedNodeId = options[0].nodeId
  try {
    await ElMessageBox({
      title: '选择下一环节',
      message: () => h('div', { class: 'next-node-selector' }, [
        h('div', { class: 'next-node-selector__tip' }, '存在多个可提交环节，请先选择目标节点。'),
        ...options.map((option) => h('label', {
          class: 'next-node-selector__option',
        }, [
          h('input', {
            type: 'radio',
            name: 'next-node',
            checked: selectedNodeId === option.nodeId,
            onChange: () => {
              selectedNodeId = option.nodeId
            },
          }),
          h(
            'span',
            `${option.nodeName}${option.projectRequired ? ' [需绑定项目]' : ''}`,
          ),
        ])),
      ]),
      showCancelButton: true,
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      closeOnClickModal: false,
    })
  } catch {
    return null
  }
  return selectedNodeId
}

async function handleSaveDraft() {
  if (noAvailableTypeReason.value) {
    ElMessage.warning({ message: noAvailableTypeReason.value, duration: 5000 })
    return
  }
  if (!(await validateForms())) return

  const hadExistingRequirement = isEditMode.value
  submitting.value = true
  try {
    const draft = await persistDraft(false)
    if (hadExistingRequirement || relatedRequirements.value.length > 0) {
      await syncRelations(draft.id)
    }
    ElMessage.success('草稿已保存')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存草稿失败'))
  } finally {
    submitting.value = false
  }
}

// Submit
async function handleSubmit() {
  if (noAvailableTypeReason.value) {
    ElMessage.warning({ message: noAvailableTypeReason.value, duration: 5000 })
    return
  }
  if (!(await validateForms())) return

  const hadExistingRequirement = isEditMode.value
  submitting.value = true
  try {
    if (!isDraftMode.value) {
      const payload = buildRequirementPayload()
      payload.id = editId.value
      await requirementApi.updateRequirement(editId.value, payload)
      await syncRelations(editId.value)
      ElMessage.success('更新成功')
      if (parentId.value) {
        router.push({ name: 'RequirementDetail', params: { id: parentId.value } })
      } else {
        router.push({ name: 'Requirements' })
      }
      return
    }

    const draft = await persistDraft(false, true)
    if (hadExistingRequirement || relatedRequirements.value.length > 0) {
      await syncRelations(draft.id)
    }

    let nextNodes: NextNodeOption[]
    try {
      nextNodes = await requirementApi.getRequirementNextNodes(draft.id)
    } catch (wfError) {
      const msg = resolveErrorMessage(wfError, '')
      if (msg.includes('工作流') || msg.includes('保存草稿')) {
        ElMessage.warning({ message: '当前没有可用的已启用工作流，已自动保存为草稿，请配置并启用工作流后再提交审核。', duration: 5000 })
        return
      }
      throw wfError
    }
    const nextNodeId = await chooseNextNode(nextNodes)
    if (!nextNodeId) return
    const selectedNode = nextNodes.find((item) => item.nodeId === nextNodeId)
    if (selectedNode?.projectRequired && !(formData.projectId && formData.projectId > 0)) {
      ElMessage.warning('所选下一环节要求绑定项目，请先在基础信息中选择所属项目')
      return
    }

    const submitted = await requirementApi.submitRequirementDraft(draft.id, {
      version: draft.version,
      nextNodeId,
      projectId: formData.projectId,
    })
    ElMessage.success('提交审核成功')
    router.push({ name: 'RequirementDetail', params: { id: submitted.id || draft.id } })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, isDraftMode.value ? '提交审核失败' : '更新失败'))
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  if (currentRequirement.value?.isDraft) {
    router.push({ name: 'Requirements', query: { view: 'drafts' } })
    return
  }
  router.push({ name: 'Requirements' })
}

async function loadConfig() {
  try {
    const [typesRes, prioritiesRes, allTypesRes] = await Promise.all([
      requirementConfigApi.listAvailableTypes(),
      requirementConfigApi.listPriorities(),
      requirementConfigApi.listTypes(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any).data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any).data || []
    const allTypeList = Array.isArray(allTypesRes) ? allTypesRes : (allTypesRes as any).data || []
    configTypes.value = typeList.map((t: any) => ({ ...t, name: normalizeText(t.name) }))
    allConfigTypes.value = allTypeList.map((t: any) => ({ ...t, name: normalizeText(t.name) }))
    configPriorities.value = priorityList.map((p: any) => ({ ...p, name: stripPriorityPrefix(normalizeText(p.name)) }))
    if (!isEditMode.value) {
      // 设置默认需求类型
      if (configTypes.value.length > 0) {
        formData.type = configTypes.value[0].code
        await applyRequirementTemplate(formData.type, true)
      }
      // 设置默认优先级
      if (configPriorities.value.length > 0) {
        const defaultPriority = configPriorities.value.find((p: any) => p.isDefault === true)
        formData.priority = defaultPriority ? defaultPriority.code : configPriorities.value[0].code
      }
    }
  } catch {
    // ignore
  }
}

async function loadCreateFormConfig(projectId = formData.projectId) {
  if (!projectId) {
    createFormVisibleFields.value = CREATE_VISIBLE_FIELD_FALLBACK
    createFormRequiredFields.value = []
    return
  }

  try {
    const res = await requirementConfigApi.getCreateFormConfig(projectId) as any
    const visibleFields = Array.isArray(res?.visibleFields)
      ? res.visibleFields
        .map((item: string) => normalizeFieldName(item))
        .filter((item: string) => item === 'dueDate' || item === 'ccUserIds')
      : []
    createFormVisibleFields.value = visibleFields.length > 0 ? visibleFields : CREATE_VISIBLE_FIELD_FALLBACK
    createFormRequiredFields.value = Array.isArray(res?.requiredFields)
      ? res.requiredFields
        .map((item: string) => normalizeFieldName(item))
        .filter((item: string) => item === 'dueDate' || item === 'ccUserIds')
      : []

    // 仅在用户尚未选择需求类型时，才应用项目默认类型
    if (res?.defaultTypeCode && !formData.type) {
      formData.type = res.defaultTypeCode
      await applyRequirementTemplate(formData.type, true)
    }
  } catch {
    const roleFallback = userStore.roles.some((role) => ['admin', '产品经理', 'PM'].includes(role))
    createFormVisibleFields.value = roleFallback ? CREATE_VISIBLE_FIELD_FALLBACK : []
    createFormRequiredFields.value = []
  }
}

onMounted(async () => {
  // 编辑模式：先校验权限，无权限直接跳回列表
  if (isEditMode.value && !hasPermission('button:requirement:update')) {
    ElMessage.error('您没有编辑需求的权限')
    router.replace({ name: 'Requirements' })
    return
  }
  await Promise.all([
    loadProjects(),
    loadUsers(),
    loadOrgTree(),
    loadConfig(),
  ])
})

watch(() => formData.type, (typeCode) => {
  void applyRequirementTemplate(typeCode, true)
})
</script>

<style scoped lang="scss">
.create-page {
  padding: var(--page-padding-lg);
  min-height: 100%;
  display: flex;
  flex-direction: column;
  max-width: 1280px;
  margin: 0 auto;
}

.card-titlebar {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.card-subtitle {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.form-container {
  flex: 1;
}

.left-panel {
  min-width: 0;
}

.right-panel {
  position: sticky;
  top: var(--spacing-md);
}

.form-card {
  height: 100%;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.form-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.info-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--color-text-primary);
}

.inline-fields {
  display: flex;
  gap: 16px;
  margin-bottom: 18px;
}

.inline-item {
  flex: 1;
  margin-bottom: 0 !important;
}

/* Editor */
.description-item :deep(.el-form-item__content) {
  width: 100%;
}

.editor-wrapper {
  border: 1px solid var(--color-border);
  border-radius: 4px;
  overflow-x: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
  height: clamp(400px, calc(100vh - 480px), 800px);
  max-height: 80vh;
  position: relative;
}

.editor-wrapper :deep(.requirement-editor-image) {
  display: block;
  max-width: 100%;
  border-radius: 6px;
}

/* 表格样式 */
.editor-wrapper :deep(.requirement-editor-table) {
  border-collapse: collapse;
  table-layout: fixed;
  width: 100%;
  margin: 12px 0;
  overflow: hidden;
}

.editor-wrapper :deep(.requirement-editor-table td),
.editor-wrapper :deep(.requirement-editor-table th) {
  min-width: 1em;
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  vertical-align: top;
  box-sizing: border-box;
  position: relative;
}

.editor-wrapper :deep(.requirement-editor-table th) {
  font-weight: 600;
  text-align: left;
  background-color: #f5f7fa;
}

.editor-wrapper :deep(.requirement-editor-table .selectedCell) {
  background-color: #e8f4ff;
}

.editor-wrapper :deep(.requirement-editor-table .column-resize-handle) {
  position: absolute;
  right: -2px;
  top: 0;
  bottom: -2px;
  width: 4px;
  background-color: #409eff;
  pointer-events: none;
}

.editor-wrapper :deep([data-resize-container]) {
  margin-top: 12px;
  margin-bottom: 12px;
  width: fit-content;
  max-width: 100%;
}

.editor-wrapper :deep([data-resize-wrapper]) {
  display: inline-block !important;
  max-width: 100%;
}

.editor-wrapper :deep([data-resize-container].ProseMirror-selectednode .requirement-editor-image),
.editor-wrapper :deep([data-resize-container][data-resize-state='true'] .requirement-editor-image),
.editor-wrapper :deep([data-resize-wrapper].ProseMirror-selectednode .requirement-editor-image) {
  outline: 2px solid rgba(64, 158, 255, 0.45);
  outline-offset: 2px;
}

.editor-wrapper :deep([data-resize-handle]) {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 999px;
  border: 2px solid #fff;
  background: var(--el-color-primary);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.28), 0 2px 8px rgba(64, 158, 255, 0.25);
  z-index: 3;
}

.editor-wrapper :deep([data-resize-handle='top-left']) {
  top: -6px;
  left: -6px;
  cursor: nwse-resize;
}

.editor-wrapper :deep([data-resize-handle='top-right']) {
  top: -6px;
  right: -6px;
  cursor: nesw-resize;
}

.editor-wrapper :deep([data-resize-handle='bottom-left']) {
  bottom: -6px;
  left: -6px;
  cursor: nesw-resize;
}

.editor-wrapper :deep([data-resize-handle='bottom-right']) {
  right: -6px;
  bottom: -6px;
  cursor: nwse-resize;
}

.editor-wrapper--dragover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}

.editor-drop-overlay {
  position: absolute;
  inset: 0;
  background: rgba(64, 158, 255, 0.08);
  border: 2px dashed var(--el-color-primary);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-color-primary);
  font-size: 14px;
  z-index: 10;
  pointer-events: none;
}

/* Priority */
.priority-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.priority-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.priority-dot.p0 { background: var(--color-danger); }
.priority-dot.p1 { background: var(--color-warning); }
.priority-dot.p2 { background: var(--color-accent); }
.priority-dot.p3 { background: var(--color-muted-text); }

/* User Option */
.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Info Cards */
.info-card {
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
}

.info-card:last-child {
  margin-bottom: 0;
}

.card-header {
  font-weight: var(--font-weight-semibold);
}

.info-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.info-card :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

/* Extra Section: Relation & Attachment */
.extra-section {
  margin-top: 8px;
}

.extra-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.extra-row-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.extra-row-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.relation-count {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* Upload Zone - 已移至 AttachmentUploader 组件 */

.relation-chips {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
}

.relation-chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 6px 8px 6px 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--el-fill-color-light);
  transition: border-color var(--duration-fast) var(--ease-standard),
              background-color var(--duration-fast) var(--ease-standard);

  &:hover {
    border-color: var(--el-color-primary-light-7);
    background: var(--el-color-primary-light-9);
  }
}

.relation-chip__type {
  width: 110px;
  flex-shrink: 0;
}

.relation-chip__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.relation-chip__remove {
  flex-shrink: 0;
  padding: 4px;
  border-radius: var(--radius-sm);
  opacity: 0.6;
  transition: opacity var(--duration-fast) var(--ease-standard);

  &:hover {
    opacity: 1;
    background: var(--el-color-danger-light-9);
  }
}

/* Attachment styles - 已移至 AttachmentUploader 组件 */

.preview-iframe {
  width: 100%;
  height: 70vh;
  border: none;
}

/* Action Bar */
.action-bar {
  position: sticky;
  bottom: 0;
  padding: 12px 0;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  z-index: 50;
}

:global(.next-node-selector) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

:global(.next-node-selector__tip) {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

:global(.next-node-selector__option) {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
}

/* Relation Dialog */
.relation-search {
  margin-bottom: 16px;
}

.relation-select-table {
  max-height: 400px;
  overflow-y: auto;
}

@media (max-width: 1100px) {
  .create-page {
    max-width: 100%;
  }

  .right-panel {
    position: static;
  }
}



</style>
