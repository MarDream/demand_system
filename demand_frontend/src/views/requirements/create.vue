<template>
  <div class="create-page">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ name: 'Requirements' }">需求管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="parentId" :to="{ name: 'RequirementDetail', params: { id: parentId } }">父需求</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isEditMode ? '编辑需求' : '新建需求' }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h2 class="page-title">{{ isEditMode ? '编辑需求' : '新建需求' }}</h2>
    </div>

    <div class="form-container">
      <!-- Left Panel: Main Form -->
      <div class="left-panel">
        <el-card class="form-card">
          <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
            <!-- 需求类型 & 优先级 -->
            <div class="inline-fields">
              <el-form-item label="需求类型" prop="type" class="inline-item">
                <el-select v-model="formData.type" placeholder="请选择" style="width: 100%">
                  <el-option
                    v-for="t in configTypes"
                    :key="t.code"
                    :label="t.name"
                    :value="t.code"
                  >
                    <span v-if="t.color" class="type-option">
                      <span class="type-dot" :style="{ backgroundColor: t.color }"></span>
                      {{ t.name }}
                    </span>
                    <span v-else>{{ t.name }}</span>
                  </el-option>
                </el-select>
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
            <el-form-item label="需求描述" prop="description">
              <div class="editor-wrapper">
                <div class="editor-toolbar">
                  <el-tooltip content="撤销" placement="top">
                    <el-button @click="editor?.chain().focus().undo().run()" :disabled="!editor?.can().undo()">
                      <el-icon><RefreshLeft /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="重做" placement="top">
                    <el-button @click="editor?.chain().focus().redo().run()" :disabled="!editor?.can().redo()">
                      <el-icon><RefreshRight /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="标题1" placement="top">
                    <el-button @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()" :class="{ 'is-active': editor?.isActive('heading', { level: 1 }) }">H1</el-button>
                  </el-tooltip>
                  <el-tooltip content="标题2" placement="top">
                    <el-button @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()" :class="{ 'is-active': editor?.isActive('heading', { level: 2 }) }">H2</el-button>
                  </el-tooltip>
                  <el-tooltip content="标题3" placement="top">
                    <el-button @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()" :class="{ 'is-active': editor?.isActive('heading', { level: 3 }) }">H3</el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="加粗" placement="top">
                    <el-button @click="editor?.chain().focus().toggleBold().run()" :class="{ 'is-active': editor?.isActive('bold') }">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="斜体" placement="top">
                    <el-button @click="editor?.chain().focus().toggleItalic().run()" :class="{ 'is-active': editor?.isActive('italic') }">
                      <el-icon><ICON_NAME /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="删除线" placement="top">
                    <el-button @click="editor?.chain().focus().toggleStrike().run()" :class="{ 'is-active': editor?.isActive('strike') }">
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="有序列表" placement="top">
                    <el-button @click="editor?.chain().focus().toggleOrderedList().run()" :class="{ 'is-active': editor?.isActive('orderedList') }">
                      <el-icon><List /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="无序列表" placement="top">
                    <el-button @click="editor?.chain().focus().toggleBulletList().run()" :class="{ 'is-active': editor?.isActive('bulletList') }">
                      <el-icon><List /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="引用" placement="top">
                    <el-button @click="editor?.chain().focus().toggleBlockquote().run()" :class="{ 'is-active': editor?.isActive('blockquote') }">
                      <el-icon><ICON_NAME /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="代码" placement="top">
                    <el-button @click="editor?.chain().focus().toggleCode().run()" :class="{ 'is-active': editor?.isActive('code') }">
                      <el-icon><ICON_NAME /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="插入链接" placement="top">
                    <el-button @click="setLink" :class="{ 'is-active': editor?.isActive('link') }">
                      <el-icon><Link /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="插入图片" placement="top">
                    <el-button @click="addImage">
                      <el-icon><Picture /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-divider direction="vertical" />
                  <el-tooltip content="清除格式" placement="top">
                    <el-button @click="editor?.chain().focus().unsetAllMarks().clearNodes().run()">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
                <editor-content :editor="editor" class="editor-content" />
              </div>
            </el-form-item>

            <!-- 关联需求 -->
            <el-form-item label="关联需求">
              <div class="relation-section">
                <div class="relation-header">
                  <span>已关联 {{ relatedRequirements.length }} 个需求</span>
                  <el-button type="primary" size="small" @click="showRelationDialog = true">
                    <el-icon><Plus /></el-icon>
                    添加关联
                  </el-button>
                </div>
                <el-table :data="relatedRequirements" size="small" class="relation-table">
                  <el-table-column prop="title" label="标题" min-width="200" />
                  <el-table-column prop="type" label="类型" width="100">
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
                <el-empty v-if="relatedRequirements.length === 0" description="暂无关联需求" :image-size="60" />
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </div>

      <!-- Right Panel: Info Cards -->
      <div class="right-panel">
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

            <el-form-item label="所属迭代">
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
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>时间</span>
            </div>
          </template>
          <el-form label-position="top">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="formData.startDate"
                type="date"
                placeholder="请选择"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="截止日期">
              <el-date-picker
                v-model="formData.dueDate"
                type="date"
                placeholder="请选择"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="估算工时(小时)">
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
      </div>
    </div>

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
          prefix-icon="Search"
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
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Delete, Search, Link, Picture,
  RefreshLeft, RefreshRight, Edit, Close, List
} from '@element-plus/icons-vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import TiptapLink from '@tiptap/extension-link'
import { requirementApi, projectApi, userApi, iterationApi } from '@/api'
import { requirementConfigApi } from '@/api/modules/requirementConfig'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const infoFormRef = ref<FormInstance>()
const submitting = ref(false)
const showRelationDialog = ref(false)
const relationSearchText = ref('')
const selectedRelations = ref<any[]>([])

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
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入需求标题', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
  type: [{ required: true, message: '请选择需求类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
}

// Tiptap Editor
const editor = useEditor({
  extensions: [
    StarterKit,
    Image.configure({
      inline: false,
      allowBase64: true,
    }),
    TiptapLink.configure({
      openOnClick: false,
    }),
  ],
  content: '',
  editorProps: {
    attributes: {
      class: 'prose',
    },
  },
  onUpdate: ({ editor }) => {
    formData.description = editor.getHTML()
  },
})

// Watch for edit mode to set initial content
watch(isEditMode, (isEdit) => {
  if (isEdit && formData.description) {
    editor.value?.commands.setContent(formData.description)
  }
}, { immediate: true })

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
    projects.value = res?.data?.list || []
  } catch {
    console.error('Failed to load projects')
  }
}

async function loadUsers() {
  try {
    const res = await userApi.getUserList({ pageNum: 1, pageSize: 100 }) as any
    users.value = res?.data?.list || []
  } catch {
    console.error('Failed to load users')
  }
}

async function loadIterations() {
  try {
    const res = await iterationApi.getIterationList(DEFAULT_PROJECT_ID) as any
    iterations.value = res?.data || []
  } catch {
    console.error('Failed to load iterations')
  }
}

async function loadRequirements() {
  try {
    const res = await requirementApi.getRequirementList({ pageNum: 1, pageSize: 100, projectId: DEFAULT_PROJECT_ID }) as any
    allRequirements.value = res?.data?.list || []
  } catch {
    console.error('Failed to load requirements')
  }
}

async function loadEditData() {
  if (!isEditMode.value) return
  try {
    const data = await requirementApi.getRequirementById(editId.value) as any
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

    // Set editor content
    if (editor.value && data.description) {
      editor.value.commands.setContent(data.description)
    }
  } catch {
    ElMessage.error('加载需求数据失败')
  }
}

// Editor functions
function setLink() {
  if (!editor.value) return

  if (editor.value.isActive('link')) {
    editor.value.chain().focus().unsetLink().run()
    return
  }

  const url = ''
  if (url) {
    editor.value.chain().focus().setLink({ href: url }).run()
  }
}

function addImage() {
  const url = window.prompt('请输入图片地址')
  if (url) {
    editor.value?.chain().focus().setImage({ src: url, alt: '图片' }).run()
  }
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
      iterationId: formData.iterationId,
      startDate: formData.startDate,
      dueDate: formData.dueDate,
      estimatedHours: formData.estimatedHours,
      ccUserIds: formData.ccUserIds,
      parentId: parentId.value,
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
    configTypes.value = typesRes.data || []
    configPriorities.value = prioritiesRes.data || []
  } catch {
    console.error('Failed to load requirement config')
  }
}

onMounted(async () => {
  await Promise.all([
    loadProjects(),
    loadUsers(),
    loadIterations(),
    loadRequirements(),
    loadEditData(),
    loadConfig(),
  ])
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<style scoped>
.create-page {
  padding: 20px;
  padding-bottom: 80px;
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 12px 0 0;
  font-size: 20px;
  font-weight: 600;
}

.form-container {
  display: flex;
  gap: 20px;
  flex: 1;
}

.left-panel {
  flex: 1;
  min-width: 0;
}

.right-panel {
  width: 320px;
  flex-shrink: 0;
}

.form-card {
  height: 100%;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
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
.editor-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}

.editor-toolbar .el-button {
  padding: 6px 8px;
}

.editor-toolbar .el-button.is-active {
  background: #409eff;
  color: #fff;
}

.editor-content {
  min-height: 280px;
  max-height: 450px;
  overflow-y: auto;
}

.editor-content :deep(.ProseMirror) {
  padding: 12px 16px;
  min-height: 260px;
  outline: none;
}

.editor-content :deep(.ProseMirror p) {
  margin: 0 0 8px 0;
  line-height: 1.6;
}

.editor-content :deep(.ProseMirror h1),
.editor-content :deep(.ProseMirror h2),
.editor-content :deep(.ProseMirror h3) {
  margin: 16px 0 8px 0;
  font-weight: 600;
}

.editor-content :deep(.ProseMirror img) {
  max-width: 100%;
  border-radius: 4px;
}

.editor-content :deep(.ProseMirror ul),
.editor-content :deep(.ProseMirror ol) {
  padding-left: 24px;
  margin: 8px 0;
}

.editor-content :deep(.ProseMirror blockquote) {
  border-left: 3px solid #409eff;
  padding-left: 12px;
  margin: 8px 0;
  color: #606266;
  background: #f5f7fa;
}

.editor-content :deep(.ProseMirror code) {
  background: #f0f0f0;
  padding: 2px 4px;
  border-radius: 2px;
  font-family: monospace;
}

.editor-content :deep(.ProseMirror pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 12px 16px;
  border-radius: 4px;
  overflow-x: auto;
}

.editor-content :deep(.ProseMirror a) {
  color: #409eff;
  text-decoration: underline;
}

.editor-content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  color: #aaa;
  content: attr(data-placeholder);
  float: left;
  height: 0;
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

/* Relation Section */
.relation-section {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

.relation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
}

.relation-header span {
  font-size: 14px;
  color: #606266;
}

.relation-table {
  border: none;
}

.relation-table :deep(.el-table__header-wrapper th) {
  background: #fafafa;
}

/* Action Bar */
.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 24px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
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
</style>
