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
              <el-form-item v-if="isEditMode" label="需求类型" prop="type" class="inline-item">
                <el-input
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
              <div
                class="editor-wrapper"
                :class="{ 'editor-wrapper--dragover': attachmentDragover }"
                @dragover.prevent="attachmentDragover = true"
                @dragleave.prevent="attachmentDragover = false"
                @drop.prevent="handleAttachmentDrop"
              >
                <IsleEditorToolbar v-if="editorInstance" :editor="editorInstance" />
                <IsleEditor v-model="formData.description" :extensions="editorExtensions" locale="zh" @create="onEditorCreate" />
                <div v-if="attachmentDragover" class="editor-drop-overlay">
                  <el-icon :size="32"><Upload /></el-icon>
                  <span>拖放文件到此处上传</span>
                </div>
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
              <el-table v-if="relatedRequirements.length > 0" :data="relatedRequirements" size="small" class="relation-table">
                <el-table-column prop="title" label="标题" min-width="200" />
                <el-table-column v-if="isEditMode" prop="type" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small">{{ row.type }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="relationType" label="关联类型" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.relationType" size="small" style="width: 100%">
                      <el-option label="阻塞" value="blocks" />
                      <el-option label="被阻塞" value="blocked_by" />
                      <el-option label="包含" value="contains" />
                      <el-option label="被包含" value="contained_by" />
                      <el-option label="相关" value="relates_to" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center">
                  <template #default="{ row }">
                    <el-button type="danger" link size="small" @click="removeRelation(row)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else-if="currentRequirement?.isDraft === true" description="暂无关联需求" :image-size="40" />

              <!-- 附件上传区 -->
              <input ref="fileInputRef" type="file" multiple style="display: none" @change="handleFileSelect" />
              <div
                class="upload-zone"
                :class="{ 'upload-zone--active': attachmentDragover }"
                @click="triggerAttachmentUpload"
                @dragover.prevent="attachmentDragover = true"
                @dragleave.prevent="attachmentDragover = false"
                @drop.prevent="handleAttachmentDrop"
                @paste.prevent="handleAttachmentPaste"
                tabindex="0"
              >
                <div class="upload-zone__content">
                  <el-icon :size="24" class="upload-zone__icon"><Upload /></el-icon>
                  <span class="upload-zone__text">点击上传、拖拽文件或粘贴截图至此处</span>
                </div>
              </div>
              <div v-if="attachmentUploading" class="attachment-uploading">附件上传中...</div>
              <div v-if="formData.attachments.length > 0" class="attachment-list">
                <div v-for="(file, index) in formData.attachments" :key="`${file.fileId || file.objectName || file.url}-${index}`" class="attachment-item">
                  <div class="attachment-meta">
                    <el-button link type="primary" @click="handleAttachmentDownload(file)">{{ file.name }}</el-button>
                    <span v-if="file.size" class="attachment-size">{{ formatFileSize(file.size) }}</span>
                  </div>
                  <el-button link type="danger" @click="removeAttachment(index)">移除</el-button>
                </div>
              </div>
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

            <el-form-item label="提出人" prop="assigneeId">
              <el-select
                v-model="formData.assigneeId"
                placeholder="请选择提出人"
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

            <el-form-item v-if="isEditMode" label="所属迭代">
              <el-select v-model="formData.iterationId" placeholder="请选择" clearable style="width: 100%">
                <el-option
                  v-for="iteration in iterations"
                  :key="iteration.id"
                  :label="iteration.name"
                  :value="iteration.id"
                />
              </el-select>
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

            <el-form-item v-if="shouldShowField('dueDate')" label="期望上线时间">
              <el-date-picker
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

            <el-form-item v-if="isEditMode" label="创建时间">
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
        <el-button :loading="submitting" @click="handleSaveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          提交流转
        </el-button>
      </template>
      <el-button v-else type="primary" :loading="submitting" @click="handleSubmit">
        保存
      </el-button>
    </div>

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
  Plus, Delete, Search, Upload
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
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { downloadRequirementAttachment, uploadRequirementAttachment } from '@/api/modules/file'
import type { RelationItem } from '@/api/modules/relation'
import { buildRichTextImagePreviewUrl, hydrateRichTextImageHtml, serializeRichTextImageHtml } from '@/utils/richTextFileImage'
import { formatDate, normalizeText, stripPriorityPrefix } from '@/utils/format'
import PageContainer from '@/components/common/PageContainer.vue'
import { useUserStore } from '@/stores'
import type { NextNodeOption, Requirement, RequirementAttachment } from '@/types/requirement'
import type { OrgNode, User } from '@/types/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const infoFormRef = ref<FormInstance>()
const submitting = ref(false)
const showRelationDialog = ref(false)
const relationSearchText = ref('')
const selectedRelations = ref<any[]>([])
const attachmentUploading = ref(false)
const attachmentDragover = ref(false)
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
const configPriorities = ref<any[]>([])

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
  type: isEditMode.value ? [{ required: true, message: '请选择需求类型', trigger: 'change' }] : [],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  assigneeId: [{ required: true, message: '请选择提出人', trigger: 'change' }],
}))

const selectedType = computed(() => configTypes.value.find((item) => item.code === formData.type))
const selectedTypeLabel = computed(() => selectedType.value?.name || '')
const selectedTypeColor = computed(() => selectedType.value?.color || '')
const showTimeCard = computed(() => isEditMode.value || shouldShowField('dueDate'))
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
  }),
  Image.configure({
    inline: false,
    allowBase64: true,
    resize: {
      enabled: true,
      directions: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
      minWidth: 100,
      minHeight: 100,
      alwaysPreserveAspectRatio: true,
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
  // 绑定粘贴事件到编辑器 DOM，支持粘贴文件/截图上传
  const editorEl = editor.view.dom as HTMLElement
  editorEl.addEventListener('paste', handleAttachmentPaste as unknown as EventListener)
}

watch(
  () => formData.description,
  (val) => {
    if (editorInstance.value && isEditMode.value && val && editorInstance.value.getHTML() !== val) {
      editorInstance.value.commands.setContent(val)
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
    console.error('Failed to load projects')
  }
}

async function loadOrgTree() {
  try {
    const res = await userApi.getOrgTree() as any
    orgTree.value = Array.isArray(res) ? res : []
  } catch {
    orgTree.value = []
    console.error('Failed to load org tree')
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
    console.error('Failed to load users')
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
    console.error('Failed to load iterations')
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
    console.error('Failed to load requirements')
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
  if (isEditMode.value) return true
  if (field === 'startDate' || field === 'estimatedHours') return false
  if (field === 'dueDate') return true
  if (createFormVisibleFields.value.length === 0) return false
  return createFormVisibleFields.value.some((item) => normalizeFieldName(item) === field)
}

function formatFileSize(size?: number | null) {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function beforeAttachmentUpload(rawFile: File) {
  const isValid = rawFile.size <= 50 * 1024 * 1024
  if (!isValid) {
    ElMessage.error('单个附件不能超过 50MB')
  }
  return isValid
}

async function handleAttachmentUpload(options: UploadRequestOptions) {
  attachmentUploading.value = true
  try {
    const attachment = await uploadRequirementAttachment(options.file as File)
    formData.attachments.push(attachment)
    ElMessage.success('附件上传成功')
    options.onSuccess?.(attachment as any)
  } catch (error) {
    ElMessage.error('附件上传失败')
    options.onError?.(error as any)
  } finally {
    attachmentUploading.value = false
  }
}

const fileInputRef = ref<HTMLInputElement>()

function triggerAttachmentUpload() {
  fileInputRef.value?.click()
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (!beforeAttachmentUpload(file)) continue
    try {
      attachmentUploading.value = true
      const attachment = await uploadRequirementAttachment(file)
      formData.attachments.push(attachment)
    } catch {
      ElMessage.error('附件上传失败')
    } finally {
      attachmentUploading.value = false
    }
  }
  input.value = ''
}

async function handleAttachmentDrop(event: DragEvent) {
  attachmentDragover.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (!beforeAttachmentUpload(file)) continue
    try {
      attachmentUploading.value = true
      const attachment = await uploadRequirementAttachment(file)
      formData.attachments.push(attachment)
    } catch {
      ElMessage.error('附件上传失败')
    } finally {
      attachmentUploading.value = false
    }
  }
  ElMessage.success('附件上传成功')
}

function getFileExtension(mimeType: string): string {
  const mimeToExt: Record<string, string> = {
    'image/jpeg': 'jpg',
    'image/png': 'png',
    'image/gif': 'gif',
    'image/webp': 'webp',
    'image/bmp': 'bmp',
    'image/svg+xml': 'svg',
    'application/pdf': 'pdf',
    'application/msword': 'doc',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
    'application/vnd.ms-excel': 'xls',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
    'application/vnd.ms-powerpoint': 'ppt',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'pptx',
    'text/plain': 'txt',
    'application/zip': 'zip',
    'application/x-rar-compressed': 'rar',
  }
  return mimeToExt[mimeType] || 'bin'
}

function createFileWithName(file: File, filename: string): File {
  return new File([file], filename, { type: file.type })
}

async function handleAttachmentPaste(event: ClipboardEvent) {
  const files = event.clipboardData?.files
  if (!files || files.length === 0) return
  // 有文件时阻止默认粘贴行为，避免编辑器重复处理
  event.preventDefault()

  for (const file of Array.from(files)) {
    const isImage = file.type.startsWith('image/')

    // 为剪贴板文件生成正确的文件名（如果没有的话）
    let processedFile = file
    if (!file.name || file.name === 'image' || !file.name.includes('.')) {
      const ext = getFileExtension(file.type)
      const timestamp = Date.now()
      const newName = `clipboard_${timestamp}.${ext}`
      processedFile = createFileWithName(file, newName)
    }

    if (isImage) {
      // 图片直接插入编辑器
      try {
        attachmentUploading.value = true
        ElMessage.info(`上传图片中: ${processedFile.name}`)
        const attachment = await uploadRequirementAttachment(processedFile)
        if (attachment.fileId && editorInstance.value) {
          editorInstance.value
            .chain()
            .focus()
            .setImage({ src: buildRichTextImagePreviewUrl(attachment.fileId), alt: processedFile.name })
            .run()
          ElMessage.success(`图片 ${processedFile.name} 已插入`)
        } else if (attachment.url && editorInstance.value) {
          editorInstance.value.chain().focus().setImage({ src: attachment.url, alt: processedFile.name }).run()
          ElMessage.success(`图片 ${processedFile.name} 已插入`)
        }
      } catch (error) {
        ElMessage.error(`图片 ${processedFile.name} 插入失败`)
      } finally {
        attachmentUploading.value = false
      }
    } else {
      // 非图片作为附件上传
      if (!beforeAttachmentUpload(processedFile)) continue
      try {
        attachmentUploading.value = true
        ElMessage.info(`上传附件中: ${processedFile.name}`)
        const attachment = await uploadRequirementAttachment(processedFile)
        formData.attachments.push(attachment)
        ElMessage.success(`附件 ${processedFile.name} 已添加`)
      } catch (error) {
        ElMessage.error(`附件 ${processedFile.name} 上传失败`)
      } finally {
        attachmentUploading.value = false
      }
    }
  }
}

function removeAttachment(index: number) {
  formData.attachments.splice(index, 1)
}

async function handleAttachmentDownload(file: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(file)
  } catch {
    ElMessage.error('附件下载失败')
  }
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

function resolveErrorMessage(error: unknown, fallback: string) {
  const message = (error as any)?.response?.data?.message || (error as any)?.message
  return typeof message === 'string' && message.trim() ? message : fallback
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

function buildRequirementPayload() {
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
    payload.dueDate = normalizeDateValue(formData.dueDate)
    payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
    return payload
  }

  if (shouldShowField('startDate')) payload.startDate = normalizeDateValue(formData.startDate)
  if (shouldShowField('dueDate')) payload.dueDate = normalizeDateValue(formData.dueDate)
  if (shouldShowField('estimatedHours')) payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
  return payload
}

function buildDraftPayload() {
  const ccUserIds = showCcField.value ? formData.ccUserIds : []
  const payload: any = {
    projectId: formData.projectId,
    title: formData.title,
    description: serializeRichTextImageHtml(formData.description),
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

async function persistDraft(showSuccess = true) {
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
  await router.replace({ name: 'RequirementCreate', query: { id: draftId } })
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
            `${option.nodeName}${option.bindStatusName ? ` (${option.bindStatusName})` : ''}${option.projectRequired ? ' [需绑定项目]' : ''}`,
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

    const draft = await persistDraft(false)
    if (hadExistingRequirement || relatedRequirements.value.length > 0) {
      await syncRelations(draft.id)
    }
    const nextNodes = await requirementApi.getRequirementNextNodes(draft.id)
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
    ElMessage.success('提交流转成功')
    router.push({ name: 'RequirementDetail', params: { id: submitted.id || draft.id } })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, isDraftMode.value ? '提交流转失败' : '更新失败'))
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
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any).data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any).data || []
    configTypes.value = typeList.map((t: any) => ({ ...t, name: normalizeText(t.name) }))
    configPriorities.value = priorityList.map((p: any) => ({ ...p, name: stripPriorityPrefix(normalizeText(p.name)) }))
    if (!isEditMode.value && configTypes.value.length > 0) {
      formData.type = configTypes.value[0].code
    }
  } catch {
    console.error('Failed to load requirement config')
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

    if (res?.defaultTypeCode) {
      formData.type = res.defaultTypeCode
    }
  } catch {
    const roleFallback = userStore.roles.some((role) => ['admin', '产品经理', 'PM'].includes(role))
    createFormVisibleFields.value = roleFallback ? CREATE_VISIBLE_FIELD_FALLBACK : []
    createFormRequiredFields.value = []
  }
}

onMounted(async () => {
  await Promise.all([
    loadProjects(),
    loadUsers(),
    loadOrgTree(),
    loadConfig(),
  ])
})
</script>

<style scoped lang="scss">
.create-page {
  padding: $page-padding;
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
  font-size: 14px;
  font-weight: 600;
  color: $text-color;
}

.card-subtitle {
  font-size: 12px;
  color: $text-color-secondary;
}

.form-container {
  flex: 1;
}

.left-panel {
  min-width: 0;
}

.right-panel {
  position: sticky;
  top: $spacing-md;
}

.form-card {
  height: 100%;
  border-radius: $card-radius;
  box-shadow: $shadow-sm;
  border: 1px solid $border-color;
}

.form-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.info-card :deep(.el-card__header) {
  padding: 14px 16px;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: $text-color;
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
  border: 1px solid $border-color;
  border-radius: 4px;
  overflow-x: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
  height: clamp(360px, calc(100vh - 520px), 760px);
  position: relative;
}

.editor-wrapper :deep(.requirement-editor-image) {
  display: block;
  max-width: 100%;
  border-radius: 6px;
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

.priority-dot.p0 { background: #f56c6c; }
.priority-dot.p1 { background: #e6a23c; }
.priority-dot.p2 { background: #409eff; }
.priority-dot.p3 { background: #909399; }

/* User Option */
.user-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Info Cards */
.info-card {
  margin-bottom: 16px;
  border-radius: $card-radius;
  box-shadow: $shadow-sm;
  border: 1px solid $border-color;
}

.info-card:last-child {
  margin-bottom: 0;
}

.card-header {
  font-weight: 500;
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
  color: $text-color;
  white-space: nowrap;
}

.extra-row-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.relation-count {
  font-size: 13px;
  color: $text-color-secondary;
}

/* Upload Zone */
.upload-zone {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
  outline: none;
}

.upload-zone:hover,
.upload-zone:focus {
  border-color: var(--el-color-primary);
}

.upload-zone--active {
  border-color: var(--el-color-primary);
  background: rgba(64, 158, 255, 0.04);
}

.upload-zone__content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.upload-zone__icon {
  color: var(--el-text-color-placeholder);
}

.upload-zone:hover .upload-zone__icon,
.upload-zone--active .upload-zone__icon {
  color: var(--el-color-primary);
}

.upload-zone__text {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.upload-zone:hover .upload-zone__text,
.upload-zone--active .upload-zone__text {
  color: var(--el-color-primary);
}

.relation-table {
  margin-bottom: 4px;
}

.relation-table :deep(.el-table__header-wrapper th) {
  background: #fafafa;
}

.attachment-uploading {
  color: $text-color-secondary;
  font-size: 13px;
  padding: 8px 0;
}

.attachment-meta .el-button {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Action Bar */
.action-bar {
  position: sticky;
  bottom: 0;
  padding: 12px 0;
  background: $bg-container;
  border-top: 1px solid $border-color;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  z-index: 100;
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
