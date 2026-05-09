<template>
  <PageContainer :breadcrumb="false">
    <div class="create-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ name: 'Requirements' }">需求管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="parentId" :to="{ name: 'RequirementDetail', params: { id: parentId } }">父需求</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isEditMode ? '编辑需求' : '新建需求' }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">{{ isEditMode ? '编辑需求' : '新建需求' }}</h2>
    </div>

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
              <el-empty v-else-if="isEditMode" description="暂无关联需求" :image-size="40" />

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
              <el-select v-model="formData.projectId" placeholder="请选择项目" style="width: 100%">
                <el-option
                  v-for="project in projects"
                  :key="project.id"
                  :label="project.name"
                  :value="project.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="负责人">
              <el-select
                v-model="formData.assigneeId"
                placeholder="请选择"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="user in users"
                  :key="user.id"
                  :label="user.realName || user.username"
                  :value="user.id"
                >
                  <div class="user-option">
                    <el-avatar :size="24" :src="user.avatar">{{ (user.realName || user.username)[0] }}</el-avatar>
                    <span>{{ user.realName || user.username }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item label="抄送人">
              <el-select
                v-model="formData.ccUserIds"
                placeholder="请选择"
                clearable
                multiple
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="user in users"
                  :key="user.id"
                  :label="user.realName || user.username"
                  :value="user.id"
                >
                  <div class="user-option">
                    <el-avatar :size="24" :src="user.avatar">{{ (user.realName || user.username)[0] }}</el-avatar>
                    <span>{{ user.realName || user.username }}</span>
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

            <el-form-item v-if="shouldShowField('dueDate')" label="截止日期">
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
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- Action Bar -->
    <div class="action-bar">
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEditMode ? '保存' : '创建' }}
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  Plus, Delete, Search, Upload
} from '@element-plus/icons-vue'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import { addLocale } from '@isle-editor/core'
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

import { requirementApi, projectApi, userApi, iterationApi } from '@/api'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { downloadRequirementAttachment, uploadRequirementAttachment } from '@/api/modules/file'
import { normalizeText } from '@/utils/format'
import PageContainer from '@/components/common/PageContainer.vue'
import { useUserStore } from '@/stores'
import type { RequirementAttachment } from '@/types/requirement'

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

// Data
const projects = ref<any[]>([])
const users = ref<any[]>([])
const iterations = ref<any[]>([])
const allRequirements = ref<any[]>([])

// Related requirements
const relatedRequirements = ref<any[]>([])

// Requirement config types and priorities
const configTypes = ref<any[]>([])
const configPriorities = ref<any[]>([])

const editId = computed(() => {
  const q = route.query.id
  return q ? Number(q) : 0
})

const parentId = computed(() => {
  const q = route.query.parentId
  return q ? Number(q) : undefined
})

const isEditMode = computed(() => editId.value > 0)

const DEFAULT_PROJECT_ID = 1
const CREATE_VISIBLE_FIELD_FALLBACK = ['startDate', 'dueDate', 'estimatedHours']
const FIELD_NAME_ALIASES: Record<string, string> = {
  startDate: 'startDate',
  开始时间: 'startDate',
  开始日期: 'startDate',
  dueDate: 'dueDate',
  截止时间: 'dueDate',
  截止日期: 'dueDate',
  estimatedHours: 'estimatedHours',
  估算工时: 'estimatedHours',
}

const formData = reactive({
  projectId: DEFAULT_PROJECT_ID,
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
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  type: isEditMode.value ? [{ required: true, message: '请选择需求类型', trigger: 'change' }] : [],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
}))

const selectedType = computed(() => configTypes.value.find((item) => item.code === formData.type))
const selectedTypeLabel = computed(() => selectedType.value?.name || '')
const selectedTypeColor = computed(() => selectedType.value?.color || '')
const showTimeCard = computed(() =>
  shouldShowField('startDate') || shouldShowField('dueDate') || shouldShowField('estimatedHours'),
)

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
]

const editorInstance = ref<any>(null)

function onEditorCreate({ editor }: { editor: any }) {
  editorInstance.value = editor
  if (formData.description && isEditMode.value) {
    editor.commands.setContent(formData.description)
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

// Filtered requirements for relation dialog
const filteredRequirements = computed(() => {
  if (!relationSearchText.value) return allRequirements.value
  return allRequirements.value.filter(r =>
    r.title.toLowerCase().includes(relationSearchText.value.toLowerCase())
  )
})

// Load data
async function loadProjects() {
  try {
    const res = await projectApi.getProjectList({ pageNum: 1, pageSize: 100 }) as any
    projects.value = res?.list || []
    if (!projects.value.some((project: any) => project.id === formData.projectId)) {
      formData.projectId = projects.value[0]?.id || DEFAULT_PROJECT_ID
    }
  } catch {
    projects.value = []
    console.error('Failed to load projects')
  }
}

async function loadUsers() {
  try {
    const res = await userApi.getUserList({ pageNum: 1, pageSize: 100 }) as any
    users.value = res?.list || []
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

async function loadEditData() {
  if (!isEditMode.value) return
  try {
    const data = await requirementApi.getRequirementById(editId.value) as any
    formData.projectId = data.projectId || DEFAULT_PROJECT_ID
    formData.title = data.title
    formData.description = data.description
    formData.type = data.type
    formData.priority = data.priority
    formData.assigneeId = data.assigneeId || undefined
    formData.iterationId = data.iterationId || undefined
    formData.startDate = data.startDate || undefined
    formData.dueDate = data.dueDate || undefined
    formData.estimatedHours = data.estimatedHours || undefined
    formData.ccUserIds = data.ccUserIds || []
    formData.attachments = Array.isArray(data.attachments) ? data.attachments : []
  } catch {
    ElMessage.error('加载需求数据失败')
  }
}

function shouldShowField(field: string) {
  if (isEditMode.value) return true
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

async function handleAttachmentPaste(event: ClipboardEvent) {
  const files = event.clipboardData?.files
  if (!files || files.length === 0) return
  // 有文件时阻止默认粘贴行为，避免编辑器重复处理
  event.preventDefault()
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

// Submit
async function handleSubmit() {
  const basicValid = await formRef.value?.validate().catch(() => false)
  const infoValid = await infoFormRef.value?.validate().catch(() => false)
  if (!basicValid || !infoValid) return

  submitting.value = true
  try {
    const payload: any = {
      projectId: formData.projectId,
      title: formData.title,
      description: formData.description,
      type: formData.type,
      priority: formData.priority,
      assigneeId: formData.assigneeId,
      attachments: formData.attachments,
      parentId: parentId.value,
    }

    if (isEditMode.value) {
      payload.iterationId = formData.iterationId
      payload.ccUserIds = formData.ccUserIds
      payload.startDate = normalizeDateValue(formData.startDate)
      payload.dueDate = normalizeDateValue(formData.dueDate)
      payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
    } else {
      if (shouldShowField('startDate')) payload.startDate = normalizeDateValue(formData.startDate)
      if (shouldShowField('dueDate')) payload.dueDate = normalizeDateValue(formData.dueDate)
      if (shouldShowField('estimatedHours')) payload.estimatedHours = normalizeNumberValue(formData.estimatedHours)
    }

    if (isEditMode.value) {
      payload.id = editId.value
      await requirementApi.updateRequirement(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await requirementApi.createRequirement(payload)
      ElMessage.success('创建成功')
    }

    if (parentId.value) {
      router.push({ name: 'RequirementDetail', params: { id: parentId.value } })
    } else {
      router.push({ name: 'Requirements' })
    }
  } catch {
    ElMessage.error(isEditMode.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
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
    configPriorities.value = priorityList.map((p: any) => ({ ...p, name: normalizeText(p.name) }))
    if (!isEditMode.value && configTypes.value.length > 0) {
      formData.type = configTypes.value[0].code
    }
  } catch {
    console.error('Failed to load requirement config')
  }
}

async function loadCreateFormConfig(projectId = formData.projectId) {
  if (!projectId || isEditMode.value) {
    createFormVisibleFields.value = CREATE_VISIBLE_FIELD_FALLBACK
    createFormRequiredFields.value = []
    return
  }

  try {
    const res = await requirementConfigApi.getCreateFormConfig(projectId) as any
    const visibleFields = Array.isArray(res?.visibleFields)
      ? res.visibleFields.map((item: string) => normalizeFieldName(item)).filter(Boolean)
      : []
    createFormVisibleFields.value = visibleFields.length > 0 ? visibleFields : CREATE_VISIBLE_FIELD_FALLBACK
    createFormRequiredFields.value = Array.isArray(res?.requiredFields)
      ? res.requiredFields.map((item: string) => normalizeFieldName(item)).filter(Boolean)
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
    loadEditData(),
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

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 12px 0 0;
  font-size: 20px;
  font-weight: 600;
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
