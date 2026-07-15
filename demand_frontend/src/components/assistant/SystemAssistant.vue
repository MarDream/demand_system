<template>
  <div class="system-assistant">
    <button
      ref="fabRef"
      class="assistant-fab"
      type="button"
      title="AI助手（Ctrl/⌘ + K）"
      :style="fabStyle"
      @click="handleFabClick"
      @mousedown.prevent="onFabDragStart"
      @touchstart.prevent="onFabDragStart"
    >
      <AssistantAvatar :variant="avatarVariant" />
    </button>

    <el-dialog
      v-model="visible"
      width="920px"
      class="assistant-dialog"
      :fullscreen="assistantFullscreen"
      :draggable="!assistantFullscreen"
      :show-close="false"
      :close-on-click-modal="false"
      align-center
      destroy-on-close
    >
      <template #header>
        <div class="assistant-panel__header">
          <div>
            <div class="assistant-panel__title">AI 操作助手</div>
            <div class="assistant-panel__subtitle">结合当前页面、系统菜单和模型能力，为你提供入口导航与操作建议</div>
          </div>
          <el-space class="assistant-panel__tools" alignment="start" wrap>
            <el-tag v-if="currentPageContext.pageTitle" type="info" effect="plain">当前页面：{{ currentPageContext.pageTitle }}</el-tag>
            <el-tag type="success" effect="plain">Ctrl/⌘ + K</el-tag>
            <el-dropdown trigger="click" @command="handleAvatarCommand">
              <el-tag class="assistant-avatar-tag" type="warning" effect="plain">
                头像：{{ avatarVariantLabel }}
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-tag>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="girl" :disabled="avatarVariant === 'girl'">圆脸少女 · 双马尾</el-dropdown-item>
                  <el-dropdown-item command="fox" :disabled="avatarVariant === 'fox'">小狐狸 · 戴眼镜</el-dropdown-item>
                  <el-dropdown-item command="cat" :disabled="avatarVariant === 'cat'">小猫咪 · 竖瞳</el-dropdown-item>
                  <el-dropdown-item command="elf" :disabled="avatarVariant === 'elf'">魔法少女 · 尖耳</el-dropdown-item>
                  <el-dropdown-item command="default" :disabled="avatarVariant === 'default'">经典数字人</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button text @click="toggleAssistantFullscreen">{{ assistantFullscreen ? '退出全屏' : '全屏' }}</el-button>
            <el-button text @click="handleCreateSession">新会话</el-button>
            <el-button text @click="assistantStore.close()">关闭</el-button>
          </el-space>
        </div>
      </template>

      <div class="assistant-panel" :class="{ 'assistant-panel--fullscreen': assistantFullscreen }">
        <div class="assistant-panel__body">
          <aside class="assistant-session-list">
            <div class="assistant-session-list__header">会话</div>
            <div v-if="sessions.length === 0" class="assistant-session-list__empty">暂无会话</div>
            <button
              v-for="session in sessions"
              :key="session.id"
              type="button"
              class="assistant-session-item"
              :class="{ 'is-active': session.id === activeSessionId }"
              @click="handleSelectSession(session.id)"
            >
              <div class="assistant-session-item__title">{{ session.title || '新会话' }}</div>
              <div class="assistant-session-item__preview">{{ session.lastMessagePreview || '等待提问…' }}</div>
              <div class="assistant-session-item__footer">
                <span>{{ formatTime(session.lastMessageAt || session.updatedAt) }}</span>
                <el-icon class="assistant-session-item__delete" @click.stop="handleDeleteSession(session.id)">
                  <Delete />
                </el-icon>
              </div>
            </button>
          </aside>

          <section class="assistant-chat">
            <div ref="messageListRef" class="assistant-message-list">
              <div v-if="messages.length === 0" class="assistant-empty">
                <el-icon class="assistant-empty__icon"><ChatDotRound /></el-icon>
                <div class="assistant-empty__title">告诉我你想完成什么操作</div>
                <div class="assistant-empty__desc">例如：如何新建需求、去哪里配置工作流、在哪里维护知识库</div>

                <div v-if="recommendedQuestions.length" class="assistant-empty__quick-asks">
                  <div class="assistant-section-title">你可以这样问我</div>
                  <div class="assistant-chip-list">
                    <button
                      v-for="question in recommendedQuestions"
                      :key="question"
                      type="button"
                      class="assistant-chip"
                      :disabled="sending"
                      @click="handleQuickAsk(question)"
                    >
                      {{ question }}
                    </button>
                  </div>
                </div>
              </div>

              <div
                v-for="message in messages"
                :key="String(message.id)"
                class="assistant-message"
                :class="`assistant-message--${message.role}`"
              >
                <div class="assistant-message__meta">
                  <span>{{ message.role === 'assistant' ? 'AI助手' : '我' }}</span>
                  <span v-if="message.createdAt">{{ formatTime(message.createdAt) }}</span>
                </div>
                <div class="assistant-message__bubble">
                  <MarkdownContent
                    v-if="message.role === 'assistant'"
                    class="assistant-message__content assistant-message__content--assistant"
                    :content="message.content || (message.status === 'streaming' ? '正在思考中…' : '')"
                  />
                  <div v-else class="assistant-message__content assistant-message__content--user">
                    {{ message.content }}
                  </div>

                  <div
                    v-if="message.role === 'assistant' && message.actions?.length"
                    class="assistant-message__actions"
                  >
                    <div class="assistant-section-title">建议入口</div>
                    <div class="assistant-action-list">
                      <button
                        v-for="action in message.actions"
                        :key="`${action.type}-${action.targetPath}-${action.label}`"
                        type="button"
                        class="assistant-action-card"
                        :class="[`is-${resolvePathMatch(action.targetPath)}`, { 'is-disabled': !action.targetPath }]"
                        :disabled="!action.targetPath"
                        @click="handleNavigate(action.targetPath)"
                      >
                        <div class="assistant-action-card__header">
                          <span class="assistant-action-card__label">{{ action.label }}</span>
                          <span class="assistant-action-card__badge">{{ resolvePathMatchLabel(action.targetPath) }}</span>
                        </div>
                        <div class="assistant-action-card__desc">{{ action.description || action.targetPath }}</div>
                        <div v-if="action.targetPath" class="assistant-action-card__path">{{ action.targetPath }}</div>
                      </button>
                    </div>
                  </div>

                  <div
                    v-if="message.role === 'assistant' && message.sources?.length"
                    class="assistant-message__sources assistant-message__sources--compact"
                  >
                    <span class="assistant-sources-label">依据</span>
                    <div class="assistant-source-list assistant-source-list--compact">
                      <div
                        v-for="source in message.sources"
                        :key="`${source.code}-${source.path}-${source.title}`"
                        class="assistant-source-chip"
                        :class="[`is-${resolveSourceMatch(source)}`, { 'is-clickable': source.path }]"
                        :title="buildSourceTooltip(source)"
                        :role="source.path ? 'button' : undefined"
                        :tabindex="source.path ? 0 : undefined"
                        @click="handleSourceNavigate(source)"
                        @keydown.enter.prevent="handleSourceNavigate(source)"
                        @keydown.space.prevent="handleSourceNavigate(source)"
                      >
                        <span class="assistant-source-chip__title">{{ source.title || source.path || source.code || '系统匹配结果' }}</span>
                        <span class="assistant-source-chip__badge">{{ resolveSourceMatchLabel(source) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="assistant-composer">
              <div v-if="composerQuestions.length" class="assistant-composer__quick-asks">
                <div class="assistant-section-title">快捷提问</div>
                <div class="assistant-chip-list assistant-chip-list--compact">
                  <button
                    v-for="question in composerQuestions"
                    :key="question"
                    type="button"
                    class="assistant-chip assistant-chip--compact"
                    :disabled="sending"
                    @click="handleQuickAsk(question)"
                  >
                    {{ question }}
                  </button>
                </div>
              </div>

              <el-input
                v-model="draft"
                type="textarea"
                :rows="4"
                resize="none"
                maxlength="4000"
                show-word-limit
                :placeholder="composerPlaceholder"
                @keydown.enter.exact.prevent="handleSend"
              />
              <div class="assistant-composer__footer">
                <span class="assistant-composer__tip">Enter 发送，Shift + Enter 换行，Esc 关闭面板</span>
                <el-button type="primary" :loading="sending" @click="handleSend">
                  发送
                  <el-icon class="el-icon--right"><Promotion /></el-icon>
                </el-button>
              </div>
            </div>
          </section>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Promotion, ArrowDown } from '@element-plus/icons-vue'
import MarkdownContent from '@/components/common/MarkdownContent.vue'
import AssistantAvatar from '@/components/assistant/avatars/AssistantAvatar.vue'
import { useAssistantStore } from '@/stores/assistant'
import { useAssistantContext } from '@/composables/useAssistantContext'
import type { AssistantPageContext, AssistantSource } from '@/types/assistant'

type PathMatchType = 'current-page' | 'current-menu' | 'related' | 'none'

const router = useRouter()
const assistantStore = useAssistantStore()
const { currentPageContext } = useAssistantContext()
const { visible, sessions, activeSessionId, messages, sending, initialized } = storeToRefs(assistantStore)

const draft = ref('')
const messageListRef = ref<HTMLDivElement | null>(null)
const assistantFullscreen = ref(false)

// ===== 浮标头像切换 =====
type AvatarVariant = 'default' | 'girl' | 'fox' | 'cat' | 'elf'
const AVATAR_STORAGE_KEY = 'assistantFabAvatar'

function loadAvatarVariant(): AvatarVariant {
  try {
    const raw = localStorage.getItem(AVATAR_STORAGE_KEY)
    if (raw && ['default', 'girl', 'fox', 'cat', 'elf'].includes(raw)) {
      return raw as AvatarVariant
    }
  } catch {
    // localStorage 不可用时忽略
  }
  return 'girl'
}

const avatarVariant = ref<AvatarVariant>(loadAvatarVariant())

const avatarVariantLabel = computed(() => {
  switch (avatarVariant.value) {
    case 'girl': return '圆脸少女'
    case 'fox': return '小狐狸'
    case 'cat': return '小猫咪'
    case 'elf': return '魔法少女'
    default: return '经典数字人'
  }
})

function handleAvatarCommand(command: string) {
  setAvatarVariant(command as AvatarVariant)
  ElMessage.success(`已切换为 ${avatarVariantLabel.value}`)
}

function setAvatarVariant(variant: AvatarVariant) {
  avatarVariant.value = variant
  try {
    localStorage.setItem(AVATAR_STORAGE_KEY, variant)
  } catch {
    // 忽略存储失败
  }
}

// ===== 拖拽浮标 =====
const fabRef = ref<HTMLButtonElement | null>(null)
const fabPos = ref({ x: 0, y: 0 })
const fabInitialized = ref(false)
const isDragging = ref(false)
const dragStartPos = ref({ x: 0, y: 0 })
const dragOffset = ref({ x: 0, y: 0 })
const hasMoved = ref(false)

const FAB_SIZE = 48
const FAB_MARGIN = 16

function clampPosition(x: number, y: number) {
  const maxX = window.innerWidth - FAB_SIZE - FAB_MARGIN
  const maxY = window.innerHeight - FAB_SIZE - FAB_MARGIN
  return {
    x: Math.max(FAB_MARGIN, Math.min(maxX, x)),
    y: Math.max(FAB_MARGIN, Math.min(maxY, y)),
  }
}

function initFabPosition() {
  if (fabInitialized.value) return
  fabPos.value = {
    x: window.innerWidth - FAB_SIZE - 28,
    y: window.innerHeight - FAB_SIZE - 28,
  }
  fabInitialized.value = true
}

const fabStyle = computed(() => ({
  left: `${fabPos.value.x}px`,
  top: `${fabPos.value.y}px`,
  right: 'auto',
  bottom: 'auto',
  transition: isDragging.value ? 'none' : 'left 0.3s ease, top 0.3s ease',
}))

function onFabDragStart(e: MouseEvent | TouchEvent) {
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

  isDragging.value = true
  hasMoved.value = false
  dragStartPos.value = { x: clientX, y: clientY }
  dragOffset.value = {
    x: clientX - fabPos.value.x,
    y: clientY - fabPos.value.y,
  }

  document.addEventListener('mousemove', onFabDragMove)
  document.addEventListener('mouseup', onFabDragEnd)
  document.addEventListener('touchmove', onFabDragMove, { passive: false })
  document.addEventListener('touchend', onFabDragEnd)
}

function onFabDragMove(e: MouseEvent | TouchEvent) {
  if (!isDragging.value) return
  e.preventDefault()
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY

  const dx = clientX - dragStartPos.value.x
  const dy = clientY - dragStartPos.value.y
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    hasMoved.value = true
  }

  fabPos.value = clampPosition(
    clientX - dragOffset.value.x,
    clientY - dragOffset.value.y,
  )
}

function onFabDragEnd() {
  isDragging.value = false
  document.removeEventListener('mousemove', onFabDragMove)
  document.removeEventListener('mouseup', onFabDragEnd)
  document.removeEventListener('touchmove', onFabDragMove)
  document.removeEventListener('touchend', onFabDragEnd)

  // 吸附到最近的边缘
  if (hasMoved.value) {
    const centerX = fabPos.value.x + FAB_SIZE / 2
    const snapX = centerX < window.innerWidth / 2 ? FAB_MARGIN : window.innerWidth - FAB_SIZE - FAB_MARGIN
    fabPos.value = { ...fabPos.value, x: snapX }
  }
}

function handleFabClick() {
  // 拖拽过程中不触发点击
  if (hasMoved.value) {
    hasMoved.value = false
    return
  }
  handleOpen()
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  initFabPosition()
  window.addEventListener('resize', () => {
    fabPos.value = clampPosition(fabPos.value.x, fabPos.value.y)
  })
})

const composerPlaceholder = computed(() => {
  const title = currentPageContext.value.pageTitle
  if (title) {
    return `例如：我在“${title}”页面想完成什么操作？`
  }
  return '请输入你的问题，例如：如何新建需求？去哪里配置工作流？'
})

const recommendedQuestions = computed(() => buildRecommendedQuestions(currentPageContext.value))
const composerQuestions = computed(() => recommendedQuestions.value.slice(0, 3))

function buildRecommendedQuestions(pageContext: AssistantPageContext) {
  const route = pageContext.route || pageContext.activeMenu || ''
  const routeName = pageContext.routeName || ''
  const entityType = pageContext.entityType || ''

  switch (routeName) {
    case 'RequirementCreate':
      return [
        '创建需求时哪些字段必须填写？',
        '如何保存草稿或提交需求？',
        '如何选择项目、优先级和需求类型？',
      ]
    case 'RequirementDetail':
      return [
        '如何查看这个需求的流转记录？',
        '如何在需求详情里查看评论、附件和评审信息？',
        '这个需求下一步应该如何处理？',
      ]
    case 'WorkflowConfigEditor':
      return [
        '如何新增工作流节点？',
        '如何配置节点之间的流转条件？',
        '工作流编辑后如何保存并发布？',
      ]
    case 'WorkflowMigration':
      return [
        '如何执行工作流迁移？',
        '迁移前需要确认哪些影响范围？',
        '工作流迁移失败后如何回滚？',
      ]
    case 'KnowledgeDetail':
      return [
        '如何上传和管理当前知识库文档？',
        '如何查看知识库文档解析状态？',
        '如何分享或维护这个知识库？',
      ]
    case 'KnowledgeSearch':
      return [
        '如何进行知识库语义检索？',
        '如何限定只检索某个知识库？',
        '检索结果不准确时应该怎么调整？',
      ]
    case 'RatingStatistics':
      return [
        '如何查看需求评分分析？',
        '评分统计里的指标分别代表什么？',
        '如何按项目或时间范围分析评分趋势？',
      ]
    case 'BitableList':
      return [
        '如何新建一张多维表格？',
        '如何找到已有的多维表格？',
        '多维表格适合管理哪些业务数据？',
      ]
    case 'BitableEditor':
      return [
        '如何在多维表格中新增字段和记录？',
        '如何切换或配置多维表格视图？',
        '如何把多维表格数据和业务需求关联起来？',
      ]
    case 'UserManage':
      return [
        '如何新增或停用用户？',
        '如何给用户分配角色？',
        '用户无法登录时应该检查哪些配置？',
      ]
    case 'RoleManage':
      return [
        '如何创建角色并分配权限？',
        '如何调整角色可访问的菜单？',
        '角色权限变更后多久生效？',
      ]
    case 'MenuManagement':
      return [
        '如何新增或调整系统菜单？',
        '如何配置菜单权限标识？',
        '菜单调整后前端看不到应该检查什么？',
      ]
    case 'RequirementConfig':
      return [
        '如何维护需求类型和字段配置？',
        '需求配置会影响哪些创建或流转环节？',
        '如何让配置变更对新需求生效？',
      ]
    case 'RequirementTemplates':
      return [
        '如何新增需求模板？',
        '如何维护模板字段和默认内容？',
        '创建需求时如何使用模板？',
      ]
    case 'Dashboard':
      return [
        '首页指标分别代表什么？',
        '如何从首页快速进入待办需求？',
        '如何查看我负责事项的整体进展？',
      ]
    case 'Notifications':
      return [
        '如何查看我的系统通知？',
        '如何处理未读通知？',
        '哪些操作会产生通知提醒？',
      ]
    default:
      break
  }

  if (route.startsWith('/requirements') || entityType === 'requirement') {
    return [
      '如何新建一个需求？',
      '如何筛选并快速找到我负责的需求？',
      '如何查看需求详情并继续跟进处理？',
    ]
  }

  if (route.startsWith('/iterations') || entityType === 'iteration') {
    return [
      '如何创建一个新的迭代？',
      '如何把需求安排进迭代？',
      '如何查看迭代排期和容量情况？',
    ]
  }

  if (route.startsWith('/settings/knowledge') || entityType === 'knowledge') {
    return [
      '如何新建或维护知识库？',
      '如何查看某个知识库的文档详情？',
      '如何缩小检索范围到指定知识库？',
    ]
  }

  if (route.startsWith('/settings/documents') || entityType === 'documents') {
    return [
      '如何上传文档到文档中心？',
      '如何让系统基于文档做智能检索？',
      '文档中心和知识库管理有什么区别？',
    ]
  }

  if (route.startsWith('/system/workflow') || entityType === 'workflow') {
    return [
      '如何配置一个新的工作流？',
      '如何编辑现有工作流的节点和流转规则？',
      '工作流迁移入口在哪里？',
    ]
  }

  if (route.startsWith('/settings/llm') || entityType === 'llm') {
    return [
      '如何配置系统使用的大模型？',
      '如何启用默认的聊天模型？',
      'Embedding 和 Rerank 模型在哪里设置？',
    ]
  }

  if (route.startsWith('/settings/projects') || entityType === 'project') {
    return [
      '如何新增一个项目？',
      '如何维护项目归属和关联关系？',
      '项目配置完成后会影响哪些功能？',
    ]
  }

  if (route.startsWith('/settings/users') || entityType === 'user') {
    return [
      '如何新增用户并分配角色？',
      '如何调整用户状态或基础信息？',
      '用户权限异常时应该从哪里排查？',
    ]
  }

  if (route.startsWith('/settings/roles') || entityType === 'role') {
    return [
      '如何创建角色并绑定权限？',
      '如何查看角色覆盖了哪些菜单？',
      '如何安全调整管理员角色权限？',
    ]
  }

  if (route.startsWith('/settings/menus') || entityType === 'menu') {
    return [
      '如何维护系统菜单？',
      '如何配置菜单权限编码？',
      '菜单新增后为什么页面没有显示？',
    ]
  }

  if (route.startsWith('/settings/requirement-templates') || entityType === 'requirement-template') {
    return [
      '如何新增需求模板？',
      '如何设置模板默认内容？',
      '模板会影响哪些需求创建入口？',
    ]
  }

  if (route.startsWith('/settings/requirements') || entityType === 'requirement-config') {
    return [
      '如何维护需求配置？',
      '需求字段或类型调整后会影响哪些页面？',
      '如何确认配置已经生效？',
    ]
  }

  if (route.startsWith('/bitable') || entityType === 'bitable') {
    return [
      '如何创建和维护多维表格？',
      '如何在表格中配置字段和视图？',
      '多维表格可以用来管理哪些业务数据？',
    ]
  }

  return [
    '如何新建需求？',
    '去哪里配置工作流？',
    '在哪里维护知识库和上传文档？',
    '系统里的模型配置入口在哪里？',
  ]
}

function normalizePath(path?: string) {
  if (!path) return ''
  const [purePath] = path.split(/[?#]/)
  if (!purePath) return ''
  if (purePath.length > 1) {
    return purePath.replace(/\/+$/, '')
  }
  return purePath
}

function resolvePathMatch(targetPath?: string): PathMatchType {
  const normalizedTarget = normalizePath(targetPath)
  if (!normalizedTarget) return 'none'

  const currentRoute = normalizePath(currentPageContext.value.route)
  if (normalizedTarget === currentRoute) {
    return 'current-page'
  }

  const activeMenu = normalizePath(currentPageContext.value.activeMenu)
  if (normalizedTarget === activeMenu) {
    return 'current-menu'
  }

  return 'related'
}

function resolveSourceMatch(source: AssistantSource): PathMatchType {
  if (source.code === 'current.page') {
    return 'current-page'
  }
  return resolvePathMatch(source.path)
}

function resolvePathMatchLabel(targetPath?: string) {
  const match = resolvePathMatch(targetPath)
  if (match === 'current-page') return '当前页'
  if (match === 'current-menu') return '当前菜单'
  if (match === 'related') return '推荐入口'
  return '待确认'
}

function resolveSourceMatchLabel(source: AssistantSource) {
  const match = resolveSourceMatch(source)
  if (match === 'current-page') return '当前页依据'
  if (match === 'current-menu') return '当前菜单依据'
  if (match === 'related') return '相关功能依据'
  return '系统依据'
}

function buildSourceTooltip(source: AssistantSource) {
  const segments = [
    source.title || source.path || source.code || '系统匹配结果',
    source.path,
    source.reason || '系统根据页面上下文与功能目录生成了这条建议。',
  ].filter(Boolean)
  return segments.join('\n')
}

function toggleAssistantFullscreen() {
  assistantFullscreen.value = !assistantFullscreen.value
}

async function ensureReady() {
  if (!initialized.value) {
    await assistantStore.loadSessions()
  }
  if (activeSessionId.value) {
    await assistantStore.loadMessages(activeSessionId.value)
  }
}

async function handleOpen() {
  assistantStore.open()
  await ensureReady()
}

async function handleCreateSession() {
  draft.value = ''
  assistantStore.startNewSession()
}

async function handleSelectSession(sessionId: number) {
  await assistantStore.selectSession(sessionId)
}

async function handleDeleteSession(sessionId: number) {
  try {
    await ElMessageBox.confirm('删除后将清空该会话中的问答记录，是否继续？', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await assistantStore.removeSession(sessionId)
    ElMessage.success('会话已删除')
  } catch {
    // 用户取消删除
  }
}

async function submitMessage(message: string, clearDraft = false) {
  const content = message.trim()
  if (!content) {
    ElMessage.warning('请输入问题后再发送')
    return
  }
  if (sending.value) {
    return
  }

  if (clearDraft) {
    draft.value = ''
  }

  await assistantStore.sendMessage({
    message: content,
    pageContext: currentPageContext.value,
  })
}

async function handleSend() {
  await submitMessage(draft.value, true)
}

async function handleQuickAsk(question: string) {
  draft.value = question
  await submitMessage(question, true)
}

async function handleNavigate(targetPath?: string) {
  if (!targetPath) return
  const match = resolvePathMatch(targetPath)
  if (match === 'current-page' || match === 'current-menu') {
    ElMessage.info('你已经位于这个页面或菜单入口')
    return
  }
  await router.push(targetPath)
  assistantStore.close()
}

async function handleSourceNavigate(source: AssistantSource) {
  if (!source.path) return
  await handleNavigate(source.path)
}

function handleGlobalKeydown(event: KeyboardEvent) {
  const isOpenShortcut = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k'
  if (isOpenShortcut) {
    event.preventDefault()
    void handleOpen()
    return
  }

  if (event.key === 'Escape' && visible.value) {
    event.preventDefault()
    assistantStore.close()
  }
}

function formatTime(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const messageLength = computed(() => messages.value.length)
const lastMessageSignature = computed(() => {
  const last = messages.value[messages.value.length - 1]
  return last ? `${last.id}-${last.content?.length || 0}-${last.status || ''}` : 'empty'
})

watch([visible, messageLength, lastMessageSignature], async ([opened]) => {
  if (!opened) return
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
})

watch(visible, async (opened) => {
  if (opened) {
    await ensureReady()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<style scoped lang="scss">
:deep(.assistant-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 16px;
  overflow: hidden;
}

:deep(.assistant-dialog .el-dialog__header) {
  margin: 0;
  padding: 0;
}

:deep(.assistant-dialog .el-dialog__body) {
  padding: 0;
  overflow: hidden;
}

:deep(.assistant-dialog.is-fullscreen) {
  max-width: 100vw;
  border-radius: 0;
}

.assistant-fab {
  position: fixed;
  z-index: 1200;
  border: none;
  border-radius: 50%;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #7c4dff);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.3);
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;

  &:active {
    cursor: grabbing;
    box-shadow: 0 8px 24px rgba(64, 158, 255, 0.4);
  }

  &:hover {
    box-shadow: 0 8px 24px rgba(64, 158, 255, 0.4);
    transform: scale(1.08);
  }
}

.assistant-fab__icon {
  width: 40px;
  height: 40px;
  color: #fff;
  overflow: visible;
}

.assistant-avatar-tag {
  cursor: pointer;
  user-select: none;
}

.assistant-panel {
  height: min(76vh, 720px);
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}

.assistant-panel--fullscreen {
  height: calc(100vh - 74px);
}

.assistant-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 16px;
  border-bottom: 1px solid #e8ebf0;
  background: #fff;
  cursor: move;
}

.assistant-panel__tools {
  cursor: default;
}

.assistant-panel__title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
}

.assistant-panel__subtitle {
  margin-top: 6px;
  color: #86909c;
  font-size: 13px;
}

.assistant-panel__body {
  flex: 1;
  min-height: 0;
  display: flex;
}

.assistant-session-list {
  width: 168px;
  padding: 12px;
  border-right: 1px solid #e8ebf0;
  background: #fff;
  overflow-y: auto;
}

.assistant-session-list__header {
  margin-bottom: 10px;
  font-size: 13px;
  color: #86909c;
}

.assistant-session-list__empty {
  font-size: 12px;
  color: #a9b1bc;
}

.assistant-session-item {
  width: 100%;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #e8ebf0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.assistant-session-item:hover,
.assistant-session-item.is-active {
  border-color: #409eff;
  background: #ecf5ff;
}

.assistant-session-item__title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.4;
}

.assistant-session-item__preview {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #86909c;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.assistant-session-item__footer {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #a9b1bc;
}

.assistant-session-item__delete {
  font-size: 14px;
  color: #c45656;
}

.assistant-chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.assistant-message-list {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.assistant-empty {
  height: 100%;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #86909c;
  text-align: center;
}

.assistant-empty__icon {
  font-size: 28px;
  margin-bottom: 12px;
  color: #409eff;
}

.assistant-empty__title {
  font-size: 16px;
  color: #1f2329;
  font-weight: 600;
}

.assistant-empty__desc {
  margin-top: 8px;
  font-size: 13px;
  max-width: 320px;
}

.assistant-empty__quick-asks {
  margin-top: 20px;
  width: 100%;
  max-width: 360px;
}

.assistant-section-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.assistant-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.assistant-chip-list--compact {
  justify-content: flex-start;
}

.assistant-chip {
  border: 1px solid #d9ecff;
  border-radius: 999px;
  padding: 8px 12px;
  background: #f5f9ff;
  color: #409eff;
  cursor: pointer;
  transition: all 0.2s ease;
}

.assistant-chip:hover:not(:disabled) {
  border-color: #409eff;
  background: #ecf5ff;
}

.assistant-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.assistant-chip--compact {
  padding: 6px 10px;
  font-size: 12px;
}

.assistant-message {
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
}

.assistant-message--user {
  align-items: flex-end;
}

.assistant-message--assistant {
  align-items: flex-start;
}

.assistant-message__meta {
  margin-bottom: 6px;
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #86909c;
}

.assistant-message__bubble {
  width: min(100%, 620px);
  padding: 12px 14px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(31, 35, 41, 0.06);
}

.assistant-message--user .assistant-message__bubble {
  background: #409eff;
  color: #fff;
}

.assistant-message__content {
  word-break: break-word;
  line-height: 1.7;
  font-size: 14px;
}

.assistant-message__content--user {
  white-space: pre-wrap;
}

.assistant-message__content--assistant {
  margin-top: 0;
}

.assistant-message__actions,
.assistant-message__sources {
  margin-top: 14px;
}

.assistant-action-list,
.assistant-source-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assistant-action-card {
  width: 100%;
  border: 1px solid #dfe5ef;
  border-radius: 12px;
  padding: 10px 12px;
  background: #f8fbff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.assistant-action-card:hover:not(:disabled) {
  border-color: #409eff;
  transform: translateY(-1px);
}

.assistant-action-card.is-current-page,
.assistant-action-card.is-current-menu {
  border-color: #67c23a;
  background: #f0f9eb;
}

.assistant-action-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.75;
}

.assistant-action-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.assistant-action-card__label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.assistant-action-card__badge {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  line-height: 18px;
  color: #409eff;
  background: #ecf5ff;
}

.assistant-action-card.is-current-page .assistant-action-card__badge,
.assistant-action-card.is-current-menu .assistant-action-card__badge {
  color: #67c23a;
  background: #e8f5e9;
}

.assistant-action-card__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #5f6b7a;
}

.assistant-action-card__path {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.assistant-message__sources--compact {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.assistant-sources-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: #86909c;
}

.assistant-source-list--compact {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 6px;
}

.assistant-source-chip {
  max-width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e7ebf3;
  border-radius: 999px;
  padding: 4px 8px;
  background: #fafbfc;
  color: #5f6b7a;
  font-size: 12px;
  line-height: 18px;
  transition: all 0.2s ease;
}

.assistant-source-chip.is-clickable {
  cursor: pointer;
}

.assistant-source-chip.is-clickable:hover,
.assistant-source-chip.is-clickable:focus-visible {
  border-color: #409eff;
  background: #f8fbff;
  color: #409eff;
  outline: none;
}

.assistant-source-chip.is-current-page,
.assistant-source-chip.is-current-menu {
  border-color: #b3e19d;
  background: #f0f9eb;
  color: #67c23a;
}

.assistant-source-chip__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-source-chip__badge {
  flex-shrink: 0;
  color: #909399;
}

.assistant-composer {
  padding: 16px 18px 18px;
  border-top: 1px solid #e8ebf0;
  background: #fff;
}

.assistant-composer__quick-asks {
  margin-bottom: 12px;
}

.assistant-composer__footer {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.assistant-composer__tip {
  font-size: 12px;
  color: #86909c;
}

@media (max-width: 768px) {
  :deep(.assistant-dialog) {
    width: calc(100vw - 16px) !important;
    max-width: calc(100vw - 16px);
  }

  .assistant-panel {
    height: 82vh;
  }

  .assistant-panel--fullscreen {
    height: calc(100vh - 92px);
  }

  .assistant-panel__header {
    flex-direction: column;
  }

  .assistant-panel__body {
    flex-direction: column;
  }

  .assistant-session-list {
    width: auto;
    max-height: 128px;
    border-right: none;
    border-bottom: 1px solid #e8ebf0;
  }

  .assistant-message__bubble {
    width: 100%;
  }
}
</style>
