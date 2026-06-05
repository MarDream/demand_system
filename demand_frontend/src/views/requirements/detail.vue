<template>
  <PageContainer variant="card" :breadcrumb="false">
    <div v-loading="loading" class="detail-page">
      <template v-if="detail">
        <div class="detail-actions">
          <div class="header-actions">
            <div v-if="showCurrentNodeStatus" class="current-node-status">
              <span class="current-node-status__label">当前节点</span>
              <span class="current-node-status__value">{{ currentNodeDisplayName }}</span>
              <span class="current-node-status__divider">/</span>
              <span class="current-node-status__label">节点状态</span>
              <el-tag size="small" effect="plain" :type="statusTagType(currentNodeStatusName)">
                {{ currentNodeStatusName }}
              </el-tag>
            </div>
            <AppButton permission="button:requirement:update" @click="handleEdit">编辑</AppButton>
            <AppButton type="success" permission="button:requirement:split" @click="handleSplit">拆分子需求</AppButton>
            <AppButton type="danger" permission="button:requirement:delete">
              <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete">
                <template #reference>
                  <el-button type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </AppButton>
            <el-select
              v-model="selectedTransitionTargetId"
              :disabled="transitionLoading || transitionOptions.length === 0"
              :placeholder="transitionOptions.length > 0 ? '选择目标节点' : '当前无可执行操作'"
              style="width: 140px; margin-right: 8px"
            >
              <el-option
                v-for="transition in transitionOptions"
                :key="transitionOptionKey(transition)"
                :label="transitionOptionLabel(transition)"
                :value="transitionOptionValue(transition)"
              />
            </el-select>
            <el-select
              v-if="requiresProjectBinding"
              v-model="bindingProjectId"
              filterable
              clearable
              placeholder="流转前绑定项目"
              style="width: 180px; margin-right: 8px"
            >
              <el-option
                v-for="project in bindableProjects"
                :key="project.id"
                :label="projectOptionLabel(project)"
                :value="project.id"
              />
            </el-select>
            <AppButton
              v-if="usingUnifiedEngine && workflowRuntime.countersignEnabled"
              type="warning"
              permission="button:requirement:submit"
              @click="openCountersignDialog(workflowRuntime.currentNodeId || '')"
            >
              会签审批
            </AppButton>
            <el-select
              v-if="workflowRuntime.parallelActive && parallelBranches.length > 0"
              :model-value="workflowRuntime.activeParallelBranchId"
              placeholder="切换并行分支"
              style="width: 160px; margin-right: 8px"
              @change="handleSwitchParallelBranch"
            >
              <el-option
                v-for="branch in parallelBranches"
                :key="branch.id"
                :label="`${branch.branchName} (${parallelBranchStatusLabel(branch.status)})`"
                :value="branch.id"
                :disabled="branch.status === 'completed' || branch.status === 'skipped'"
              />
            </el-select>
            <AppButton
              type="primary"
              :loading="transitionLoading"
              :disabled="transitionOptions.length === 0 || (requiresProjectBinding && !bindingProjectId)"
              permission="button:requirement:submit"
              @click="handleStatusTransition"
            >
              提交审核
            </AppButton>
            <AppButton
              v-if="usingUnifiedEngine && workflowRuntime.canRollback"
              :loading="transitionLoading"
              permission="button:requirement:rollback"
              @click="handleRollback"
            >
              驳回
            </AppButton>
            <AppButton
              v-if="usingUnifiedEngine && workflowRuntime.canCancel"
              type="warning"
              :loading="transitionLoading"
              permission="button:requirement:cancel"
              @click="handleCancel"
            >
              取消
            </AppButton>
          </div>
        </div>

        <!-- Tabs -->
        <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="所属项目">{{ projectLabel(detail.projectId) }}</el-descriptions-item>
            <el-descriptions-item label="需求编号">{{ detail.requirementNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="需求类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :type="priorityTagType(detail.priority)">{{ priorityLabel(detail.priority) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(detail.status)">{{ detail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提出人">{{ detail.assigneeName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.creatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="所属迭代">{{ detail.iterationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="期望上线时间">{{ detail.dueDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="估算工时">{{ detail.estimatedHours ? detail.estimatedHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="实际工时">{{ detail.actualHours ? detail.actualHours + ' 小时' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="附件" :span="2">
              <div v-if="detail.attachments?.length" class="attachment-list">
                <div v-for="attachment in detail.attachments" :key="attachment.fileId || attachment.url" class="attachment-item">
                  <el-button link type="primary" @click="handleAttachmentDownload(attachment)">{{ attachment.name }}</el-button>
                  <span class="attachment-meta">
                    {{ formatAttachmentMeta(attachment) }}
                  </span>
                </div>
              </div>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <div v-if="detail.description" class="rich-content" v-html="richDescription"></div>
              <span v-else>-</span>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 子需求列表 -->
          <div v-if="children.length > 0" class="children-section">
            <div class="section-header">
              <h3>子需求（{{ children.length }} 个）</h3>
              <AppButton type="primary" size="small" permission="button:requirement:split" @click="handleSplit">+ 拆分子需求</AppButton>
            </div>
            <el-table :data="children" border size="small">
              <el-table-column label="ID" width="60" align="center">
                <template #default="{ row }">{{ row.id }}</template>
              </el-table-column>
              <el-table-column label="标题" min-width="200">
                <template #default="{ row }">
                  <el-link type="primary" @click="router.push({ name: 'RequirementDetail', params: { id: row.id } })">
                    {{ row.title }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column label="需求编号" min-width="180" align="center">
                <template #default="{ row }">{{ row.requirementNo || '-' }}</template>
              </el-table-column>
              <el-table-column label="类型" width="80" align="center">
                <template #default="{ row }">{{ typeLabel(row.type) }}</template>
              </el-table-column>
              <el-table-column label="优先级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="priorityTagType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        </el-tabs>

        <div class="approval-evaluations-section">
          <div class="section-header">
            <h3>审核记录</h3>
            <span class="section-hint">按时间倒序展示提交、通过、驳回与取消意见</span>
          </div>
          <el-empty v-if="sortedApprovalEvaluations.length === 0" description="暂无审核记录" :image-size="60" />
          <el-timeline v-else class="approval-evaluation-timeline">
            <el-timeline-item
              v-for="item in sortedApprovalEvaluations"
              :key="item.id"
              :timestamp="formatDate(item.createdAt)"
              placement="top"
              :type="approvalTimelineItemType(item.result)"
            >
              <el-card shadow="never" class="approval-evaluation-card">
                <div class="approval-evaluation-header">
                  <el-avatar :size="32">{{ item.evaluatorName?.charAt(0) || '审' }}</el-avatar>
                  <div class="approval-evaluation-meta">
                    <div class="approval-evaluation-title">
                      <strong>{{ item.evaluatorName || '处理人' }}</strong>
                      <el-tag size="small" effect="dark" :type="approvalResultTagType(item.result)">
                        {{ item.resultLabel || item.actionLabel || '审核' }}
                      </el-tag>
                      <el-tag size="small" effect="plain" type="info">{{ item.nodeName }}</el-tag>
                      <el-tag v-if="item.nodeStatusName" size="small" effect="plain">{{ item.nodeStatusName }}</el-tag>
                    </div>
                    <div v-if="item.rating" class="approval-evaluation-rating">
                      <span class="approval-evaluation-rating__label">评分</span>
                      <el-rate :model-value="item.rating" disabled />
                    </div>
                  </div>
                </div>
                <p v-if="item.content" class="approval-evaluation-content">{{ item.content }}</p>
                <p v-else class="approval-evaluation-content approval-evaluation-content--empty">未填写审核意见</p>
                <div v-if="item.canSupplement" class="approval-evaluation-actions">
                  <el-button link type="primary" @click="openSupplementDialog(item)">补充意见</el-button>
                </div>
                <div v-if="item.supplements?.length" class="approval-supplement-list">
                  <div v-for="supplement in item.supplements" :key="supplement.id" class="approval-supplement-item">
                    <div class="approval-supplement-item__header">
                      <span class="approval-supplement-item__tag">补充</span>
                      <strong>{{ supplement.evaluatorName || '处理人' }}</strong>
                      <span class="approval-supplement-item__time">{{ formatDate(supplement.createdAt) }}</span>
                    </div>
                    <p class="approval-supplement-item__content">{{ supplement.content || '未填写补充意见' }}</p>
                  </div>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 评论区 -->
        <div class="comment-section-block">
          <div class="section-header">
            <h3>评论</h3>
          </div>
          <div class="comment-editor-wrapper">
            <IsleEditorToolbar v-if="commentEditorInstance" :editor="commentEditorInstance" />
            <div class="comment-editor-toolbar">
              <el-button
                link
                type="primary"
                size="small"
                :loading="commentImageUploading"
                @click="triggerCommentFileInput"
              >
                <el-icon style="margin-right: 4px"><Picture /></el-icon>插入图片
              </el-button>
              <span class="comment-editor-hint">支持点击选图 / 拖拽 / Ctrl+V 粘贴</span>
              <input
                ref="commentFileInputRef"
                type="file"
                accept="image/*"
                multiple
                style="display: none"
                @change="handleCommentFileInput"
              />
            </div>
            <div
              class="comment-editor-dropzone"
              :class="{ 'is-dragover': commentIsDragOver }"
              @dragenter.prevent.stop="handleCommentDragEnter"
              @dragover.prevent.stop="handleCommentDragOver"
              @dragleave.prevent.stop="handleCommentDragLeave"
              @drop.prevent.stop="handleCommentDrop"
            >
              <IsleEditor v-model="commentRichText" :extensions="commentEditorExtensions" locale="zh" @create="onCommentEditorCreate" />
            </div>
          </div>
          <div class="comment-editor-actions">
            <AppButton type="primary" permission="button:requirement:comment" :loading="commentSubmitting" @click="submitCommentRich">
              提交评论
            </AppButton>
          </div>
          <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="40" />
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <el-avatar :size="32">{{ comment.userName?.charAt(0) || 'U' }}</el-avatar>
            <div class="comment-content">
              <div class="comment-header">
                <strong>{{ comment.userName || '用户' }}</strong>
                <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
              </div>
              <div class="rich-content comment-body" v-html="hydrateRichTextImageHtml(comment.content || '')"></div>
            </div>
          </div>
        </div>

        <el-dialog
          v-model="supplementDialogVisible"
          title="补充意见"
          width="480px"
          :close-on-click-modal="false"
          @closed="resetSupplementDialog"
        >
          <p class="approval-dialog-tip">补充内容会追加在原审核记录下方，不会覆盖原始意见。</p>
          <el-input
            v-model="supplementContent"
            type="textarea"
            :rows="4"
            placeholder="请输入补充意见"
            maxlength="1000"
            show-word-limit
          />
          <template #footer>
            <el-button @click="supplementDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="supplementSubmitting" @click="submitSupplement">
              提交补充
            </el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="approvalDialogVisible"
          title="审核操作"
          width="480px"
          :close-on-click-modal="false"
          @closed="resetApprovalDialog"
        >
          <p class="approval-dialog-tip">提交到下一节点前，请补充审核信息。</p>
          <div class="approval-dialog-rate">
            <span class="approval-dialog-label">评分</span>
            <el-rate v-model="approvalRating" :max="5" />
          </div>
          <el-input
            v-model="approvalComment"
            type="textarea"
            :rows="4"
            placeholder="请输入审核意见（选填）"
            maxlength="1000"
            show-word-limit
          />
          <template #footer>
            <el-button @click="approvalDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="transitionLoading" @click="confirmApprovalTransition">
              确认提交
            </el-button>
          </template>
        </el-dialog>

        <!-- 会签审批对话框 -->
        <el-dialog
          v-model="countersignDialogVisible"
          title="会签审批"
          width="500px"
          :close-on-click-modal="false"
        >
          <div v-if="currentCountersignRecords.length > 0" class="countersign-records">
            <div class="countersign-records-title">会签记录</div>
            <el-table :data="currentCountersignRecords" size="small" border>
              <el-table-column prop="approverName" label="会签人" />
              <el-table-column prop="status" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'approved' ? 'success' : row.status === 'rejected' ? 'danger' : 'info'" size="small">
                    {{ row.status === 'approved' ? '已通过' : row.status === 'rejected' ? '已驳回' : '待审批' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rating" label="评分" width="60">
                <template #default="{ row }">
                  {{ row.rating ? row.rating + '星' : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="comment" label="意见" min-width="120" show-overflow-tooltip />
            </el-table>
          </div>
          <div v-if="canCountersign" class="countersign-submit">
            <p class="countersign-tip">请对本次会签进行审批操作</p>
            <div class="countersign-rate">
              <span class="countersign-label">评分</span>
              <el-rate v-model="countersignRating" :max="5" allow-half />
            </div>
            <el-input
              v-model="countersignComment"
              type="textarea"
              :rows="3"
              placeholder="请输入审批意见（选填）"
              maxlength="500"
              show-word-limit
            />
          </div>
          <div v-else-if="!countersignDialogLoading" class="countersign-empty">
            <el-empty description="您不是当前节点的会签人，无需操作" />
          </div>
          <template #footer>
            <el-button @click="countersignDialogVisible = false">关闭</el-button>
            <el-button v-if="canCountersign" type="success" @click="handleCountersignApprove">通过</el-button>
            <el-button v-if="canCountersign" type="danger" @click="handleCountersignReject">驳回</el-button>
          </template>
        </el-dialog>
      </template>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { requirementApi, projectApi, relationApi } from '@/api'
import { downloadRequirementAttachment, uploadRequirementAttachment } from '@/api/modules/file'
import type { RelationItem } from '@/api/modules/relation'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { workflowEngineApi, type AvailableTransition, type WorkflowAvailableActions } from '@/api/modules/workflow-engine'
import { getCountersignRecords, canCurrentUserCountersign, submitCountersignApproval, switchParallelBranch, type CountersignRecord, type ParallelBranch } from '@/api/modules/workflow'
import type {
  Requirement,
  RequirementApprovalEvaluation,
  RequirementAttachment,
  RequirementComment,
  RequirementHistory,
  RequirementUpdate, RequirementDetailVO,
} from '@/types/requirement'
import { normalizeText, formatDate, stripPriorityPrefix } from '@/utils/format'
import AppButton from '@/components/common/AppButton.vue'
import { hydrateRichTextImageHtml, buildRichTextImagePreviewUrl } from '@/utils/richTextFileImage'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import { addLocale } from '@isle-editor/core'
import { Node, mergeAttributes } from '@tiptap/core'

const DEFAULT_IMAGE_WIDTH = 400
const MIN_IMAGE_WIDTH = 50
const MAX_IMAGE_WIDTH = 1600

const CommentImage = Node.create({
  name: 'commentImage',
  inline: false,
  group: 'block',
  atom: true,
  draggable: true,
  addAttributes() {
    return {
      src: { default: null },
      alt: { default: null },
      width: { default: null },
    }
  },
  parseHTML() {
    return [{ tag: 'img[src]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['img', mergeAttributes({ class: 'comment-editor-image' }, HTMLAttributes)]
  },
})
import '@isle-editor/vue3/dist/style.css'
import PageContainer from '@/components/common/PageContainer.vue'

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

const route = useRoute()
const router = useRouter()

const id = Number(route.params.id)
const loading = ref(false)
const detail = ref<Requirement | null>(null)
const history = ref<RequirementHistory[]>([])
const relatedRequirements = ref<any[]>([])
const comments = ref<RequirementComment[]>([])
const approvalEvaluations = ref<RequirementApprovalEvaluation[]>([])
const approvalDialogVisible = ref(false)
const approvalRating = ref(0)
const approvalComment = ref('')
const supplementDialogVisible = ref(false)
const supplementSubmitting = ref(false)
const supplementContent = ref('')
const supplementTarget = ref<RequirementApprovalEvaluation | null>(null)
const children = ref<any[]>([])
const activeTab = ref('basic')
const projectName = ref<string>('')
const projectOptions = ref<Array<{ id: number; name: string; status?: string | null; endDate?: string | null }>>([])
const typeMap = ref<Record<string, string>>({})
const priorityMap = ref<Record<string, string>>({})
const workflowRuntime = ref<WorkflowAvailableActions>({
  canTransition: false,
  canRollback: false,
  canCancel: false,
  transitions: [],
})
const usingUnifiedEngine = ref(false)
const selectedTransitionTargetId = ref<string | number | null>(null)
const bindingProjectId = ref<number | null>(null)
const transitionLoading = ref(false)
// 会签相关
const countersignDialogVisible = ref(false)
const countersignDialogLoading = ref(false)
const countersignRating = ref(0)
const countersignComment = ref('')
const currentCountersignRecords = ref<CountersignRecord[]>([])
const canCountersign = ref(false)
const currentCountersignNodeId = ref<string>('')
const parallelBranches = ref<ParallelBranch[]>([])
const richDescription = computed(() => hydrateRichTextImageHtml(detail.value?.description || ''))
const currentNodeStatusName = computed(() => {
  return workflowRuntime.value.currentNodeStatusName || detail.value?.status || ''
})
const currentNodeDisplayName = computed(() => {
  return workflowRuntime.value.currentNodeName || '当前节点'
})
const showCurrentNodeStatus = computed(() => {
  return usingUnifiedEngine.value
    && Boolean(detail.value?.workflowInstanceId)
    && workflowRuntime.value.currentNodeType !== 'start'
    && Boolean(currentNodeStatusName.value)
})

const sortedApprovalEvaluations = computed(() => {
  return [...approvalEvaluations.value].sort((a, b) => {
    const timeA = new Date(a.createdAt).getTime()
    const timeB = new Date(b.createdAt).getTime()
    if (timeA !== timeB) return timeB - timeA
    return b.id - a.id
  })
})

// Comment rich text editor
const commentRichText = ref('')
const commentSubmitting = ref(false)
const commentEditorInstance = ref<any>(null)
const commentEditorExtensions = [
  RichTextKit.configure({
    placeholder: { placeholder: '输入评论内容...' },
  }),
  CommentImage,
]

function onCommentEditorCreate({ editor }: { editor: any }) {
  commentEditorInstance.value = editor
  const editorEl = editor.view.dom as HTMLElement
  editorEl.addEventListener('paste', handleCommentImagePaste as unknown as EventListener)
  installImageHandleForElement = installImageResizeHandles(editor)
}

// 模块级引用，insertCommentImageFile 中用于主动为新图片安装 handle
let installImageHandleForElement: ((img: HTMLImageElement) => void) | null = null
// 模块级拖拽状态，供 inject 方式创建的 handle 也能使用
let resizeActiveImg: HTMLImageElement | null = null
let resizeStartX = 0
let resizeStartWidth = 0
let resizeActiveHandle: HTMLElement | null = null
let resizeEditorRef: any = null

function installImageResizeHandles(editor: any) {
  const editorEl = editor.view.dom as HTMLElement
  resizeEditorRef = editor

  function createHandle(img: HTMLImageElement) {
    removeAllHandles()
    if (!img.classList.contains('comment-editor-image')) return
    img.style.position = 'relative'
    const handle = document.createElement('div')
    handle.className = 'comment-image-resize-handle'
    handle.title = '拖拽调整图片大小'
    img.appendChild(handle)
    resizeActiveHandle = handle

    handle.addEventListener('mousedown', (e: MouseEvent) => {
      e.preventDefault()
      e.stopPropagation()
      resizeActiveImg = img
      resizeStartX = e.clientX
      resizeStartWidth = img.getBoundingClientRect().width
      document.addEventListener('mousemove', onResizeMove)
      document.addEventListener('mouseup', onResizeUp)
    })
  }

  function removeAllHandles() {
    if (resizeActiveHandle && resizeActiveHandle.parentElement) {
      resizeActiveHandle.parentElement.removeChild(resizeActiveHandle)
    }
    resizeActiveHandle = null
    if (!resizeActiveImg) return
    resizeActiveImg.style.cursor = ''
  }

  function onResizeMove(e: MouseEvent) {
    if (!resizeActiveImg) return
    const dx = e.clientX - resizeStartX
    const newWidth = Math.max(MIN_IMAGE_WIDTH, Math.min(MAX_IMAGE_WIDTH, Math.round(resizeStartWidth + dx)))
    resizeActiveImg.setAttribute('width', String(newWidth))
  }

  function onResizeUp() {
    document.removeEventListener('mousemove', onResizeMove)
    document.removeEventListener('mouseup', onResizeUp)
    if (resizeActiveImg) {
      const finalWidth = Number(resizeActiveImg.getAttribute('width')) || null
      try {
        const pos = resizeEditorRef.view.posAtDOM(resizeActiveImg, 0)
        resizeEditorRef.commands.setNodeSelection(pos)
        resizeEditorRef.commands.updateAttributes('commentImage', { width: finalWidth })
      } catch {}
      resizeActiveImg.style.cursor = ''
    }
    resizeActiveImg = null
  }

  editorEl.addEventListener('mouseover', (e: MouseEvent) => {
    const t = e.target as HTMLElement | null
    if (t && t.tagName === 'IMG' && t.classList.contains('comment-editor-image')) {
      createHandle(t as HTMLImageElement)
    }
  })
  editorEl.addEventListener('mouseout', (e: MouseEvent) => {
    const t = e.target as HTMLElement | null
    if (t && t.tagName === 'IMG' && t.classList.contains('comment-editor-image')) {
      const related = e.relatedTarget as Node | null
      if (!related || !(t as Node).contains(related)) {
        removeAllHandles()
      }
    }
  })

  return createHandle
}

const commentImageUploading = ref(false)
const commentIsDragOver = ref(false)
const commentFileInputRef = ref<HTMLInputElement | null>(null)
let commentDragDepth = 0

async function insertCommentImageFile(file: File) {
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件: ' + file.name)
    return
  }
  let processedFile = file
  if (!file.name || file.name === 'image' || !file.name.includes('.')) {
    const ext = file.type.split('/')[1] || 'png'
    processedFile = new File([file], `clipboard_${Date.now()}.${ext}`, { type: file.type })
  }
  try {
    commentImageUploading.value = true
    ElMessage.info(`上传图片中: ${processedFile.name}`)
    const attachment = await uploadRequirementAttachment(processedFile)
    const src = attachment.fileId ? buildRichTextImagePreviewUrl(attachment.fileId) : attachment.url
    if (src && commentEditorInstance.value) {
      const safeAlt = processedFile.name.replace(/"/g, '&quot;')
      const editor = commentEditorInstance.value
      editor.chain().focus().insertContent({
        type: 'commentImage',
        attrs: { src, alt: safeAlt, width: DEFAULT_IMAGE_WIDTH },
      }).run()
      // 直接在 DOM 中找到刚插入的图片并注入带完整拖拽功能的 resize handle，
      // 绕过 IsleEditor 复杂 DOM 结构下 mouseover 事件可能不触发的问题。
      try {
        const container = editor.view.dom
        const allImgs = container.querySelectorAll
          ? Array.from(container.querySelectorAll('img.comment-editor-image'))
          : []
        const lastImg = allImgs[allImgs.length - 1] as HTMLImageElement | undefined
        if (lastImg && !lastImg.querySelector('.comment-image-resize-handle')) {
          lastImg.style.position = 'relative'
          const handle = document.createElement('div')
          handle.className = 'comment-image-resize-handle'
          handle.title = '拖拽调整图片大小'
          lastImg.appendChild(handle)
          handle.addEventListener('mousedown', (me: MouseEvent) => {
            me.preventDefault()
            me.stopPropagation()
            let startX = me.clientX
            let startWidth = lastImg.getBoundingClientRect().width
            const onMove = (ev: MouseEvent) => {
              const dx = ev.clientX - startX
              const newWidth = Math.max(MIN_IMAGE_WIDTH, Math.min(MAX_IMAGE_WIDTH, Math.round(startWidth + dx)))
              lastImg.setAttribute('width', String(newWidth))
            }
            const onUp = () => {
              document.removeEventListener('mousemove', onMove)
              document.removeEventListener('mouseup', onUp)
              const finalWidth = Number(lastImg.getAttribute('width')) || null
              try {
                const pos = editor.view.posAtDOM(lastImg, 0)
                editor.commands.setNodeSelection(pos)
                editor.commands.updateAttributes('commentImage', { width: finalWidth })
              } catch {}
            }
            document.addEventListener('mousemove', onMove)
            document.addEventListener('mouseup', onUp)
          })
        }
      } catch {}
      // 块级原子节点 (inline:false, atom:true) 没有 inline content，
      // 不能直接在图片位置/之后 setTextSelection（会抛 TextSelection endpoint 错误）。
      // 在 doc 中找到刚插入的图片，将光标推到图片之后第一个 text block，
      // 避免后续 insertContent 替换图片。
      try {
        const doc = editor.state.doc
        let imagePos = -1
        doc.descendants((node: any, pos: number) => {
          if (node.type.name === 'commentImage' && pos > imagePos) {
            imagePos = pos
          }
        })
        if (imagePos >= 0) {
          // 原子节点 nodeSize = 1，atomEnd 即图片结束位置
          const atomEnd = imagePos + 1
          let targetPos = -1
          doc.descendants((node: any, pos: number) => {
            if (pos >= atomEnd && node.isTextblock) {
              targetPos = pos
              return false
            }
          })
          if (targetPos >= 0) {
            editor.commands.setTextSelection(targetPos)
          }
        }
      } catch {
        // 手动调整光标失败不影响图片插入
      }
      ElMessage.success(`图片 ${processedFile.name} 已插入`)
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, `图片 ${processedFile.name} 插入失败`))
  } finally {
    commentImageUploading.value = false
  }
}

async function handleCommentImagePaste(event: ClipboardEvent) {
  const files = event.clipboardData?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    event.preventDefault()
    await insertCommentImageFile(file)
  }
}

function triggerCommentFileInput() {
  commentFileInputRef.value?.click()
}

async function handleCommentFileInput(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return
  const files = Array.from(input.files)
  for (const file of files) {
    await insertCommentImageFile(file)
  }
  input.value = ''
}

function handleCommentDragEnter() {
  commentDragDepth += 1
  if (commentDragDepth === 1) commentIsDragOver.value = true
}
function handleCommentDragOver() {
  // 由 .prevent 阻止默认行为即可，必须 preventDefault 才能触发 drop
}
function handleCommentDragLeave() {
  commentDragDepth = Math.max(0, commentDragDepth - 1)
  if (commentDragDepth === 0) commentIsDragOver.value = false
}
async function handleCommentDrop(event: DragEvent) {
  commentDragDepth = 0
  commentIsDragOver.value = false
  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return
  for (const file of Array.from(files)) {
    if (file.type.startsWith('image/')) {
      await insertCommentImageFile(file)
    }
  }
}

function resetWorkflowMeta() {
  workflowRuntime.value = {
    canTransition: false,
    canRollback: false,
    canCancel: false,
    transitions: [],
  }
  usingUnifiedEngine.value = false
  selectedTransitionTargetId.value = null
  bindingProjectId.value = null
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const message = (error as any)?.response?.data?.message || (error as any)?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

// Fetch detail
async function fetchDetail() {
  loading.value = true
  try {
    const res = await requirementApi.getRequirementById(id)
    detail.value = res
    await Promise.all([loadProjectName(), loadWorkflowMeta()])
  } catch {
    ElMessage.error('获取需求详情失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectName() {
  if (!detail.value?.projectId) {
    projectName.value = ''
    return
  }
  try {
    const res = await projectApi.getProjectById(detail.value.projectId) as any
    projectName.value = res?.name || ''
  } catch {
    projectName.value = ''
  }
}

async function loadConfig() {
  try {
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any)?.data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any)?.data || []
    typeMap.value = Object.fromEntries(typeList.map((t: any) => [t.code, normalizeText(t.name)]))
    priorityMap.value = Object.fromEntries(priorityList.map((p: any) => [p.code, stripPriorityPrefix(normalizeText(p.name))]))
  } catch {
    typeMap.value = {}
    priorityMap.value = {}
  }
}

async function loadWorkflowMeta() {
  resetWorkflowMeta()
  bindingProjectId.value = detail.value?.projectId && detail.value.projectId > 0 ? detail.value.projectId : null
  parallelBranches.value = []

  if (detail.value?.workflowInstanceId) {
    try {
      const actions = await workflowEngineApi.getAvailableActions(id)
      workflowRuntime.value = actions
      usingUnifiedEngine.value = true
      parallelBranches.value = actions.parallelBranches || []
      selectedTransitionTargetId.value = actions.transitions[0]?.toNodeId ?? null
      return
    } catch {
      usingUnifiedEngine.value = false
    }
  }

  if (detail.value?.isDraft) {
    return
  }

  resetWorkflowMeta()
}

// Fetch history
async function fetchHistory() {
  try {
    if (detail.value?.workflowInstanceId) {
      const transitions = await workflowEngineApi.getTransitionHistory(id)
      history.value = Array.isArray(transitions)
        ? transitions.map((item: any) => ({
            id: item.id,
            requirementId: item.requirementId,
            operatorId: item.operatorId,
            operatorName: item.operatorName,
            fieldName: item.action === 'rollback' ? '流程驳回' : item.action === 'cancel' ? '流程取消' : '流程流转',
            oldValue: item.fromNodeName || item.fromNodeId || '开始',
            newValue: item.toNodeName || item.toNodeId || (item.durationDisplay ? `已处理（${item.durationDisplay}）` : '完成'),
            createdAt: item.createdAt,
          }))
        : []
      return
    }

    const res = await requirementApi.getRequirementHistory(id)
    history.value = Array.isArray(res) ? res : []
  } catch {
    history.value = []
  }
}

// Fetch children
async function fetchChildren() {
  try {
    const res = await requirementApi.getRequirementChildren(id)
    children.value = res
  } catch {
    // children fetch failure is non-critical
  }
}

async function loadProjectOptions() {
  try {
    const res = await projectApi.getProjectList({ pageNum: 1, pageSize: 100 }) as any
    const list = Array.isArray(res?.list) ? res.list : []
    projectOptions.value = list.filter((project: any) => !isProjectExpired(project))
  } catch {
    projectOptions.value = []
  }
}

async function fetchRelations() {
  try {
    const res = await relationApi.getRelationList(id)
    relatedRequirements.value = Array.isArray(res)
      ? res.map((item: RelationItem) => ({
          id: item.targetId,
          title: item.targetTitle,
          type: item.targetType,
          status: item.targetStatus,
          priority: item.targetPriority,
          relationType: item.relationType,
        }))
      : []
  } catch {
    relatedRequirements.value = []
  }
}

async function fetchComments() {
  try {
    const res = await requirementApi.getRequirementComments(id)
    comments.value = Array.isArray(res) ? res : []
  } catch {
    comments.value = []
  }
}

async function fetchApprovalEvaluations() {
  try {
    const res = await requirementApi.getApprovalEvaluations(id)
    approvalEvaluations.value = Array.isArray(res) ? res : []
  } catch {
    approvalEvaluations.value = []
  }
}

function resetApprovalDialog() {
  approvalRating.value = 0
  approvalComment.value = ''
}

function resetSupplementDialog() {
  supplementTarget.value = null
  supplementContent.value = ''
}

async function executeTransition(extra?: { rating?: number; comment?: string }) {
  transitionLoading.value = true
  try {
    await workflowEngineApi.transition({
      requirementId: id,
      toNodeId: String(selectedTransitionTargetId.value),
      projectId: requiresProjectBinding.value ? bindingProjectId.value : undefined,
      action: 'submit',
      comment: extra?.comment,
      rating: extra?.rating,
      lockVersion: workflowRuntime.value.lockVersion ?? undefined,
    })
    ElMessage.success('提交审核成功')
    selectedTransitionTargetId.value = null
    approvalDialogVisible.value = false
    resetApprovalDialog()
    await Promise.all([fetchDetail(), fetchHistory(), fetchApprovalEvaluations()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '状态流转失败'))
  } finally {
    transitionLoading.value = false
  }
}

// Tag type helpers
function priorityTagType(priority: string): string {
  const map: Record<string, string> = { P0: 'danger', P1: 'warning', P2: 'info', P3: 'success' }
  return map[priority] || 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待分析': 'warning', '待确认': 'warning', '待评审': 'warning',
    '评审中': 'warning', '已通过': 'success', '开发中': 'primary', '测试中': 'info',
    '已上线': 'success', '已验收': 'success', '已取消': 'info',
  }
  return map[status] || 'info'
}

function approvalResultTagType(result?: string | null): string {
  const map: Record<string, string> = {
    SUBMIT: 'primary',
    PASS: 'success',
    REJECT: 'danger',
    CANCEL: 'warning',
  }
  return result ? (map[result] || 'info') : 'info'
}

function approvalTimelineItemType(result?: string | null): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const type = approvalResultTagType(result)
  return ['primary', 'success', 'warning', 'danger', 'info'].includes(type) ? type as any : 'info'
}

function openSupplementDialog(item: RequirementApprovalEvaluation) {
  supplementTarget.value = item
  supplementContent.value = ''
  supplementDialogVisible.value = true
}

function typeLabel(code: string) {
  return typeMap.value[code] || code || '-'
}

function priorityLabel(code: string) {
  return stripPriorityPrefix(priorityMap.value[code] || code || '-')
}

function projectLabel(projectId: number) {
  if (!projectId) return '未绑定'
  return projectName.value || String(projectId)
}

function isProjectExpired(project: { status?: string | null; endDate?: string | null }) {
  if (project.status === 'expired') return true
  if (!project.endDate) return false
  return new Date(project.endDate).getTime() < Date.now() - 24 * 60 * 60 * 1000
}

function projectOptionLabel(project: { name: string }) {
  return project.name
}

function formatAttachmentMeta(attachment: RequirementAttachment) {
  const parts: string[] = []
  if (attachment.size) {
    if (attachment.size < 1024) {
      parts.push(`${attachment.size} B`)
    } else if (attachment.size < 1024 * 1024) {
      parts.push(`${(attachment.size / 1024).toFixed(1)} KB`)
    } else {
      parts.push(`${(attachment.size / 1024 / 1024).toFixed(1)} MB`)
    }
  }
  if (attachment.contentType) {
    parts.push(attachment.contentType)
  }
  return parts.join(' / ')
}

async function handleAttachmentDownload(attachment: RequirementAttachment) {
  try {
    await downloadRequirementAttachment(attachment)
  } catch {
    ElMessage.error('附件下载失败')
  }
}

type TransitionOption = AvailableTransition

const transitionOptions = computed<TransitionOption[]>(() => workflowRuntime.value.transitions)

const selectedUnifiedTransition = computed<AvailableTransition | null>(() => {
  if (!usingUnifiedEngine.value) return null
  return workflowRuntime.value.transitions.find(
    (transition) => transition.toNodeId === String(selectedTransitionTargetId.value ?? ''),
  ) || null
})

const requiresProjectBinding = computed(() => (
  usingUnifiedEngine.value
  && !detail.value?.projectId
  && Boolean(selectedUnifiedTransition.value?.projectRequired)
))

const bindableProjects = computed(() => projectOptions.value)

function transitionOptionKey(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionValue(transition: TransitionOption) {
  return transition.toNodeId
}

function transitionOptionLabel(transition: TransitionOption) {
  const baseLabel = transition.label || transition.toNodeName
  const statusLabel = transition.bindStatusName ? ` (${transition.bindStatusName})` : ''
  const projectLabel = transition.projectRequired ? ' [需绑定项目]' : ''
  return `${baseLabel}${statusLabel}${projectLabel}`
}

// Handlers
function handleEdit() {
  router.push({ name: 'RequirementCreate', query: { id } })
}

function handleSplit() {
  router.push({ name: 'RequirementCreate', query: { parentId: id } })
}

async function handleDelete() {
  try {
    await requirementApi.deleteRequirement(id)
    ElMessage.success('删除成功')
    router.push({ name: 'Requirements' })
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleStatusTransition() {
  if (!selectedTransitionTargetId.value) {
    ElMessage.warning('请选择目标节点')
    return
  }

  if (workflowRuntime.value.countersignPending) {
    ElMessage.warning('会签尚未完成，请先完成会签审批')
    if (workflowRuntime.value.currentNodeId) {
      await openCountersignDialog(workflowRuntime.value.currentNodeId)
    }
    return
  }

  if (workflowRuntime.value.evaluationRequired) {
    resetApprovalDialog()
    approvalDialogVisible.value = true
    return
  }

  await executeTransition()
}

// 会签审批方法
async function openCountersignDialog(nodeId: string) {
  currentCountersignNodeId.value = nodeId
  countersignDialogVisible.value = true
  countersignDialogLoading.value = true
  try {
    const [recordsRes, canRes] = await Promise.all([
      getCountersignRecords(id, nodeId),
      canCurrentUserCountersign(id, nodeId),
    ])
    currentCountersignRecords.value = recordsRes || []
    canCountersign.value = canRes || false
  } catch (error) {
    console.error('获取会签信息失败', error)
    ElMessage.error('获取会签信息失败')
  } finally {
    countersignDialogLoading.value = false
  }
}

async function handleCountersignApprove() {
  await submitCountersign('approved')
}

async function handleCountersignReject() {
  await submitCountersign('rejected')
}

async function submitCountersign(status: 'approved' | 'rejected') {
  try {
    await submitCountersignApproval({
      requirementId: id,
      nodeId: currentCountersignNodeId.value,
      status,
      rating: countersignRating.value || undefined,
      comment: countersignComment.value.trim() || undefined,
    })
    ElMessage.success(status === 'approved' ? '会签通过' : '会签已驳回')
    countersignDialogVisible.value = false
    countersignRating.value = 0
    countersignComment.value = ''
    await Promise.all([fetchDetail(), fetchHistory(), fetchApprovalEvaluations()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交会签审批失败'))
  }
}

function parallelBranchStatusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待处理',
    running: '进行中',
    completed: '已完成',
    skipped: '已跳过',
  }
  return map[status] || status
}

async function handleSwitchParallelBranch(branchId: number) {
  if (branchId === workflowRuntime.value.activeParallelBranchId) {
    return
  }
  try {
    await switchParallelBranch(id, branchId)
    ElMessage.success('已切换并行分支')
    await fetchDetail()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '切换并行分支失败'))
  }
}

async function confirmApprovalTransition() {
  if (!approvalRating.value || approvalRating.value < 1) {
    ElMessage.warning('请选择 1-5 星评价')
    return
  }
  await executeTransition({
    rating: approvalRating.value,
    comment: approvalComment.value.trim() || undefined,
  })
}

async function submitSupplement() {
  const content = supplementContent.value.trim()
  if (!supplementTarget.value?.id) {
    ElMessage.warning('未找到原审核记录')
    return
  }
  if (!content) {
    ElMessage.warning('请输入补充意见')
    return
  }

  supplementSubmitting.value = true
  try {
    await requirementApi.createApprovalEvaluationSupplement(id, supplementTarget.value.id, { content })
    ElMessage.success('补充意见已提交')
    supplementDialogVisible.value = false
    resetSupplementDialog()
    await fetchApprovalEvaluations()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交补充意见失败'))
  } finally {
    supplementSubmitting.value = false
  }
}

async function handleRollback() {
  await confirmAndExecute(
    '请输入驳回原因', '驳回需求', '确认驳回', '请输入驳回原因',
    (v) => workflowEngineApi.rollback(id, v || undefined),
    '驳回成功', '驳回失败',
    (input) => !!input?.trim() || '请输入驳回原因'
  )
}

async function handleCancel() {
  await confirmAndExecute(
    '请输入取消原因', '取消需求', '确认取消', '取消原因必填',
    (v) => workflowEngineApi.cancel(id, v),
    '需求已取消', '取消失败',
    (input) => !!input?.trim() || '请输入取消原因'
  )
}

async function confirmAndExecute(
  message: string, title: string, confirmText: string, placeholder: string,
  action: (value: string) => Promise<any>,
  successMsg: string, errorMsg: string,
  validator?: (input: string) => string | boolean
) {
  try {
    const opts: any = { confirmButtonText: confirmText, cancelButtonText: '取消', inputPlaceholder: placeholder }
    if (validator) opts.inputValidator = validator
    const { value } = await ElMessageBox.prompt(message, title, opts)
    transitionLoading.value = true
    await action(value)
    ElMessage.success(successMsg)
    await Promise.all([fetchDetail(), fetchHistory(), fetchApprovalEvaluations()])
  } catch (error: any) {
    if (error === 'cancel' || error?.action === 'cancel') return
    ElMessage.error(resolveErrorMessage(error, errorMsg))
  } finally {
    transitionLoading.value = false
  }
}

async function submitCommentRich() {
  const html = commentEditorInstance.value?.getHTML?.() || ''
  const content = html.trim()
  if (!content || content === '<p></p>') {
    ElMessage.warning('请输入评论内容')
    return
  }

  commentSubmitting.value = true
  try {
    await requirementApi.createRequirementComment(id, { content })
    ElMessage.success('评论已提交')
    commentEditorInstance.value?.commands?.clearContent?.()
    await fetchComments()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '评论提交失败'))
  } finally {
    commentSubmitting.value = false
  }
}

async function initializePage() {
  // Load config and project options in parallel with batch detail fetch
  const [batchData] = await Promise.all([
    requirementApi.getRequirementDetailBatch(id),
    loadConfig(),
    loadProjectOptions(),
  ])
  
  // Populate data from batch response
  if (batchData) {
    detail.value = batchData.requirement
    history.value = (batchData.history || []) as any
    children.value = (batchData.children || []) as any
    relatedRequirements.value = (batchData.relations || []) as any
    comments.value = (batchData.comments || []) as any
    approvalEvaluations.value = (batchData.approvalEvaluations || []) as any
    
    // Load project name and workflow meta after getting detail
    await Promise.all([loadProjectName(), loadWorkflowMeta()])
  }
}

onMounted(() => {
  void initializePage()
})
</script>

<style scoped>
.detail-page {
  min-height: 200px;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.current-node-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  background: #f4faff;
}

.current-node-status__label {
  color: #606266;
  font-size: 13px;
}

.current-node-status__value {
  color: #303133;
  font-size: 13px;
  font-weight: 500;
}

.current-node-status__divider {
  color: #c0c4cc;
}

.detail-tabs {
  margin-top: 16px;
}

.rich-content :deep(img) {
  max-width: 100%;
  height: auto;
}

.rich-content :deep(p) {
  margin: 0 0 8px 0;
}

.old-value {
  color: #909399;
  text-decoration: line-through;
}

.new-value {
  color: #409eff;
  font-weight: 500;
}

.comment-section {
  margin-bottom: 24px;
}

.comment-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.comment-section-block {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

.comment-editor-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 12px;
  min-height: 160px;
}

.comment-editor-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px;
  background: #fafbfc;
  border-bottom: 1px solid #ebeef5;
}

.comment-editor-hint {
  color: #909399;
  font-size: 12px;
}

.comment-editor-dropzone {
  min-height: 140px;
  transition: background-color 0.15s ease, box-shadow 0.15s ease;
}

.comment-editor-dropzone.is-dragover {
  background-color: #ecf5ff;
  box-shadow: inset 0 0 0 2px #409eff;
}

.comment-editor-dropzone :deep(.comment-editor-image) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  position: relative;
}

.comment-editor-dropzone :deep(.comment-image-resize-handle) {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 12px;
  height: 12px;
  background: #409eff;
  border: 2px solid #ffffff;
  border-radius: 2px;
  cursor: nwse-resize;
  z-index: 10;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.comment-editor-wrapper :deep(.comment-editor-image) {
  display: block;
  max-width: 100%;
  border-radius: 6px;
}

.comment-editor-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.comment-body :deep(p) {
  margin: 0 0 4px 0;
}

.children-section {
  margin-top: 24px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.attachment-meta {
  color: #909399;
  font-size: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.section-hint {
  color: #909399;
  font-size: 12px;
}

.approval-evaluations-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

.approval-evaluation-timeline {
  margin-top: 8px;
  padding-left: 4px;
}

.approval-evaluation-card {
  border: 1px solid #ebeef5;
  background: #fafafa;
}

.approval-evaluation-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.approval-evaluation-header {
  display: flex;
  gap: 12px;
}

.approval-evaluation-meta {
  flex: 1;
  min-width: 0;
}

.approval-evaluation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.approval-evaluation-rating {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.approval-evaluation-rating__label {
  color: #909399;
  font-size: 12px;
}

.approval-evaluation-content {
  margin: 12px 0 0 44px;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
}

.approval-evaluation-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.approval-supplement-list {
  margin: 12px 0 0 44px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.approval-supplement-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef6ff;
  border: 1px solid #d6e8ff;
}

.approval-supplement-item__header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.approval-supplement-item__tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 999px;
  background: #409eff;
  color: #fff;
  font-size: 12px;
}

.approval-supplement-item__time {
  color: #909399;
  font-size: 12px;
}

.approval-supplement-item__content {
  margin: 0;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
}

.approval-evaluation-content--empty {
  color: #c0c4cc;
  font-style: italic;
}

.approval-dialog-tip {
  margin: 0 0 16px;
  color: #606266;
  font-size: 13px;
}

.approval-dialog-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.approval-dialog-label {
  color: #606266;
  font-size: 14px;
}

/* 会签审批样式 */
.countersign-records {
  margin-bottom: 20px;
}

.countersign-records-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 12px;
}

.countersign-submit {
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.countersign-tip {
  color: #606266;
  font-size: 14px;
  margin-bottom: 16px;
}

.countersign-rate {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.countersign-label {
  color: #606266;
  font-size: 14px;
}

.countersign-empty {
  padding: 20px 0;
}
</style>
