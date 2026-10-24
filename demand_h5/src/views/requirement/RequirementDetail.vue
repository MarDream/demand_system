<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from 'vant'
import {
  getById,
  getComments,
  createComment,
  getAvailableActions,
  transition,
  getTransitionHistory,
  getHistory,
  getChildren,
  followRequirement,
  unfollowRequirement,
  type Requirement,
  type RequirementAttachment,
  type RequirementComment,
  type RequirementHistoryItem,
  type WorkflowAvailableActions,
  type AvailableTransition,
  type TransitionHistoryItem,
} from '@/api/requirement'
import { statusColor, statusLabel, priorityInfo, typeLabel, formatTime, formatDate, stripHtml } from '@/utils/format'
import { previewKindOf, formatFileSize } from '@/utils/file'
import FilePreviewPopup from '@/components/FilePreviewPopup.vue'

const route = useRoute()
const router = useRouter()

const requirementId = Number(route.params.id)

const detail = ref<Requirement | null>(null)
const comments = ref<RequirementComment[]>([])
const actions = ref<WorkflowAvailableActions | null>(null)
const history = ref<TransitionHistoryItem[]>([])
const opLogs = ref<RequirementHistoryItem[]>([])
const children = ref<Requirement[]>([])
const loading = ref(true)
const refreshing = ref(false)

// 通知跳转进入时（?tab=comments）默认展开评论折叠项
const activeCollapse = ref<string[]>(
  route.query.tab === 'comments'
    ? ['desc', 'attachments', 'flow', 'comments']
    : ['desc', 'attachments', 'flow'],
)
/** 通知跳转进入时短暂高亮的评论ID */
const highlightedCommentId = ref<number | null>(null)

// 评论
const commentText = ref('')
const commentSubmitting = ref(false)

// 流转弹窗
const showTransitionPopup = ref(false)
const currentTransition = ref<AvailableTransition | null>(null)
const selectedAssigneeId = ref<number | null>(null)
const transitionComment = ref('')
const transitionSubmitting = ref(false)

// 附件预览弹窗
const previewShow = ref(false)
const previewFile = ref<RequirementAttachment | null>(null)

const canFollow = computed(() => detail.value && detail.value.followed === false)
const canUnfollow = computed(() => detail.value && detail.value.followed === true)

const requirements = computed<RequirementAttachment[]>(() => detail.value?.attachments || [])
const transitionGroups = computed(() => detail.value?.transitionAttachments || [])
const attachmentCount = computed(() => requirements.value.length + transitionGroups.value.reduce((sum, g) => sum + (g.attachments?.length || 0), 0))

function fileKind(attachment: RequirementAttachment) {
  return previewKindOf(attachment.contentType || attachment.name, attachment.contentType)
}

function attachmentMeta(attachment: RequirementAttachment) {
  const parts: string[] = []
  const size = formatFileSize(attachment.size)
  if (size) parts.push(size)
  if (attachment.uploaderName) parts.push(attachment.uploaderName)
  if (attachment.uploadedAt) parts.push(formatTime(attachment.uploadedAt))
  return parts.join(' · ')
}

function openPreview(attachment: RequirementAttachment) {
  previewFile.value = attachment
  previewShow.value = true
}

async function loadAll() {
  loading.value = true
  try {
    const tasks = [
      getById(requirementId).then((d) => (detail.value = d)),
      getComments(requirementId).then((c) => (comments.value = c || [])),
      getAvailableActions(requirementId).then((a) => (actions.value = a)).catch(() => null),
      getTransitionHistory(requirementId).then((h) => (history.value = h || [])).catch(() => null),
      getHistory(requirementId).then((h) => (opLogs.value = h || [])).catch(() => null),
      getChildren(requirementId).then((c) => (children.value = c || [])).catch(() => null),
    ]
    await Promise.all(tasks)
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await loadAll()
  } finally {
    refreshing.value = false
  }
}

/**
 * 通知点击进入时定位到对应评论（?tab=comments&commentId=xxx）：
 * 等评论渲染出来后滚动到目标评论并短暂高亮，随后清掉 query 避免刷新重复触发。
 */
async function focusCommentFromQuery() {
  const commentIdRaw = route.query.commentId
  if (!commentIdRaw) return
  const targetId = Number(commentIdRaw)
  if (Number.isNaN(targetId)) {
    clearCommentQuery()
    return
  }

  for (let attempt = 0; attempt < 8; attempt += 1) {
    await nextTick()
    if (attempt > 0) {
      await new Promise((resolve) => window.setTimeout(resolve, attempt >= 4 ? 320 : 120 * attempt))
    }
    const target = document.querySelector<HTMLElement>(`.comment-item[data-comment-id="${targetId}"]`)
    if (!target) continue

    highlightedCommentId.value = targetId
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    window.setTimeout(() => {
      if (highlightedCommentId.value === targetId) {
        highlightedCommentId.value = null
      }
    }, 3000)
    clearCommentQuery()
    return
  }
  // 目标评论不存在（可能已被删除）：不留悬挂 query
  clearCommentQuery()
}

function clearCommentQuery() {
  if (route.query.tab === undefined && route.query.commentId === undefined) return
  const nextQuery = { ...route.query }
  delete nextQuery.tab
  delete nextQuery.commentId
  void router.replace({ query: nextQuery })
}

async function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    showToast('请输入评论内容')
    return
  }
  commentSubmitting.value = true
  try {
    await createComment(requirementId, content)
    commentText.value = ''
    showToast('评论成功')
    comments.value = await getComments(requirementId)
  } finally {
    commentSubmitting.value = false
  }
}

function openTransition(t: AvailableTransition) {
  currentTransition.value = t
  selectedAssigneeId.value = t.defaultAssigneeId ?? t.assigneeCandidates?.[0]?.id ?? null
  transitionComment.value = ''
  showTransitionPopup.value = true
}

async function confirmTransition() {
  const t = currentTransition.value
  if (!t) return
  if (actions.value?.currentNodeRequireComment && !transitionComment.value.trim()) {
    showToast('当前节点要求必填审批意见')
    return
  }
  if (t.assigneeCandidates?.length && !selectedAssigneeId.value) {
    showToast('请选择处理人')
    return
  }
  transitionSubmitting.value = true
  const loadingToast = showLoadingToast({ message: '提交中…', forbidClick: true, duration: 0 })
  try {
    await transition({
      requirementId,
      toNodeId: t.toNodeId,
      comment: transitionComment.value.trim() || undefined,
      lockVersion: actions.value?.lockVersion ?? null,
      selectedAssigneeId: selectedAssigneeId.value,
    })
    closeToast()
    showTransitionPopup.value = false
    showToast('流转成功')
    await loadAll()
  } catch {
    closeToast()
  } finally {
    transitionSubmitting.value = false
    loadingToast.close()
  }
}

async function toggleFollow() {
  try {
    if (canUnfollow.value) {
      await showConfirmDialog({ title: '取消关注', message: '确定取消关注该需求吗？' })
      await unfollowRequirement(requirementId)
      showToast('已取消关注')
    } else {
      await followRequirement(requirementId)
      showToast('已关注')
    }
    detail.value = await getById(requirementId)
  } catch {
    // 用户取消
  }
}

onMounted(async () => {
  await loadAll()
  // 通知跳转进入时定位到对应评论
  void focusCommentFromQuery()
})
</script>

<template>
  <div class="page">
    <van-nav-bar
      title="需求详情"
      left-arrow
      fixed
      placeholder
      safe-area-inset-top
      @click-left="router.back()"
    >
      <template #right>
        <van-icon
          :name="canUnfollow ? 'star' : 'star-o'"
          :color="canUnfollow ? '#FFCD32' : undefined"
          size="20"
          @click="toggleFollow"
        />
      </template>
    </van-nav-bar>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-skeleton v-if="loading" title :row="6" style="padding: 20px" />

      <template v-else-if="detail">
        <!-- 基本信息 -->
        <div class="detail-card">
          <div class="detail-card__tags">
            <span class="chip" :style="{ color: priorityInfo(detail.priority).color, borderColor: priorityInfo(detail.priority).color }">
              {{ priorityInfo(detail.priority).label }}
            </span>
            <span class="chip">{{ typeLabel(detail.type) }}</span>
            <span class="chip chip--status" :style="{ background: statusColor(detail.status) + '1a', color: statusColor(detail.status) }">
              {{ statusLabel(detail.status) }}
            </span>
          </div>
          <h2 class="detail-card__title">{{ detail.title }}</h2>
          <div class="detail-card__no" v-if="detail.requirementNo">编号：{{ detail.requirementNo }}</div>
        </div>

        <van-collapse v-model="activeCollapse">
          <van-collapse-item title="需求描述" name="desc">
            <div class="pre-wrap detail-desc">{{ stripHtml(detail.description) || '（无描述）' }}</div>
            <van-cell-group inset style="margin-top: 8px">
              <van-cell title="提出人" :value="detail.creatorName || '-'" />
              <van-cell title="当前处理人" :value="detail.currentHandlerName || detail.assigneeName || '-'" />
              <van-cell title="期望上线日期" :value="formatDate(detail.dueDate)" />
              <van-cell title="创建时间" :value="formatTime(detail.createdAt)" />
            </van-cell-group>
          </van-collapse-item>

          <!-- 附件 -->
          <van-collapse-item :title="attachmentCount ? `附件 (${attachmentCount})` : '附件'" name="attachments">
            <template v-if="attachmentCount">
              <div class="attach-group__title" v-if="requirements.length">需求附件</div>
              <div
                v-for="(att, idx) in requirements"
                :key="att.fileId ?? att.url ?? idx"
                class="attach-item"
                @click="openPreview(att)"
              >
                <van-icon name="description" class="attach-item__icon" :class="`attach-item__icon--${fileKind(att)}`" />
                <div class="attach-item__main">
                  <div class="attach-item__name">{{ att.name || '未命名文件' }}</div>
                  <div class="attach-item__meta">{{ attachmentMeta(att) }}</div>
                </div>
                <van-icon name="eye-o" class="attach-item__action" />
              </div>

              <template v-if="transitionGroups.length">
                <div class="attach-group__title">流转附件</div>
                <div v-for="(group, gIdx) in transitionGroups" :key="group.transitionId ?? gIdx" class="attach-group">
                  <div class="attach-group__header">
                    <span class="attach-group__node">{{ group.nodeName || '流转节点' }}</span>
                    <span class="attach-group__meta">
                      {{ [group.operatorName, formatTime(group.operatedAt)].filter(Boolean).join(' · ') }}
                    </span>
                  </div>
                  <div
                    v-for="(att, idx) in group.attachments || []"
                    :key="att.fileId ?? att.url ?? idx"
                    class="attach-item"
                    @click="openPreview(att)"
                  >
                    <van-icon name="description" class="attach-item__icon" :class="`attach-item__icon--${fileKind(att)}`" />
                    <div class="attach-item__main">
                      <div class="attach-item__name">{{ att.name || '未命名文件' }}</div>
                      <div class="attach-item__meta">{{ attachmentMeta(att) }}</div>
                    </div>
                    <van-icon name="eye-o" class="attach-item__action" />
                  </div>
                </div>
              </template>
            </template>
            <van-empty v-else image-size="56px" description="暂无附件" />
          </van-collapse-item>

        <!-- 审批流转 -->
        <van-collapse-item title="审批 / 流转" name="flow">
          <template v-if="actions && actions.canTransition && actions.transitions?.length">
            <div class="flow-tip" v-if="actions.currentNodeName">
              当前节点：{{ actions.currentNodeName }}
              <span v-if="actions.currentNodeStatusName">（{{ actions.currentNodeStatusName }}）</span>
            </div>
            <van-button
              v-for="t in actions.transitions"
              :key="t.toNodeId"
              block
              type="primary"
              plain
              style="margin-top: 8px"
              @click="openTransition(t)"
            >
              {{ t.label || `流转到「${t.toNodeName}」` }}
            </van-button>
          </template>
          <van-empty
            v-else
            image-size="56px"
            :description="actions?.workflowActive === false ? '工作流已停用' : '当前无可用流转操作'"
          />

          <!-- 流转历史 -->
          <div class="flow-history" v-if="history.length">
            <div class="flow-history__title">流转记录</div>
            <div v-for="h in history" :key="h.id" class="flow-history__item">
              <div class="flow-history__line">
                <b>{{ h.operatorName || '-' }}</b>
                <span v-if="h.operatorRoleName" class="flow-history__role">{{ h.operatorRoleName }}</span>
                <span v-if="h.fromNodeName && h.toNodeName">{{ h.fromNodeName }} → {{ h.toNodeName }}</span>
                <span v-else-if="h.toNodeName">→ {{ h.toNodeName }}</span>
              </div>
              <div class="flow-history__sub" v-if="h.comment">意见：{{ h.comment }}</div>
              <div class="flow-history__time">{{ formatTime(h.completedAt || h.createdAt) }}</div>
            </div>
          </div>
        </van-collapse-item>

        <!-- 评论 -->
        <van-collapse-item :title="`评论 (${comments.length})`" name="comments">
          <div
            v-for="c in comments"
            :key="c.id"
            class="comment-item"
            :class="{ 'comment-item--highlight': highlightedCommentId === c.id }"
            :data-comment-id="c.id"
          >
            <div class="comment-item__head">
              <span class="comment-item__user">{{ c.userName || `用户${c.userId}` }}</span>
              <span class="comment-item__time">{{ formatTime(c.createdAt) }}</span>
            </div>
            <div class="comment-item__content pre-wrap">{{ stripHtml(c.content) }}</div>
          </div>
          <van-empty v-if="!comments.length" image-size="56px" description="暂无评论" />
        </van-collapse-item>

        <!-- 子需求 -->
        <van-collapse-item v-if="children.length" :title="`子需求 (${children.length})`" name="children">
          <div
            v-for="child in children"
            :key="child.id"
            class="child-item"
            @click="router.push(`/requirement/${child.id}`)"
          >
            <div class="child-item__title">{{ child.title }}</div>
            <div class="child-item__meta">
              <span
                class="child-item__status"
                :style="{ background: statusColor(child.status) + '1a', color: statusColor(child.status) }"
              >
                {{ statusLabel(child.status) }}
              </span>
              <span v-if="child.requirementNo">#{{ child.requirementNo }}</span>
            </div>
          </div>
        </van-collapse-item>

        <!-- 操作记录 -->
        <van-collapse-item v-if="opLogs.length" title="操作记录" name="logs">
          <div v-for="log in opLogs" :key="log.id" class="oplog-item">
            <div class="oplog-item__line">
              <span class="oplog-item__operator">{{ log.operatorName || '-' }}</span>
              <span class="oplog-item__action">{{ log.action || '操作' }}</span>
            </div>
            <div class="oplog-item__detail" v-if="log.detail">{{ log.detail }}</div>
            <div class="oplog-item__time">{{ formatTime(log.createdAt) }}</div>
          </div>
        </van-collapse-item>
      </van-collapse>

      <!-- 底部评论输入 -->
      <div class="comment-bar">
        <van-field
          v-model="commentText"
          placeholder="写下你的评论…"
          rows="1"
          autosize
          type="textarea"
          class="comment-bar__input"
        />
        <van-button type="primary" size="small" round :loading="commentSubmitting" @click="submitComment">
          发送
        </van-button>
      </div>

      <!-- 流转弹窗 -->
      <van-popup v-model:show="showTransitionPopup" round position="bottom" safe-area-inset-bottom>
        <div class="transition-popup">
          <div class="transition-popup__title" v-if="currentTransition">
            {{ currentTransition.label || `流转到「${currentTransition.toNodeName}」` }}
          </div>
          <div class="transition-popup__scope" v-if="currentTransition?.assigneeScopeName">
            处理范围：{{ currentTransition.assigneeScopeName }}
          </div>

          <van-field
            v-if="currentTransition?.assigneeCandidates?.length"
            label="选择处理人"
            required
          >
            <template #input>
              <van-radio-group v-model="selectedAssigneeId" direction="horizontal">
                <van-radio
                  v-for="c in currentTransition.assigneeCandidates"
                  :key="c.id"
                  :name="c.id"
                >
                  {{ c.name }}
                </van-radio>
              </van-radio-group>
            </template>
          </van-field>

          <van-field
            v-model="transitionComment"
            rows="2"
            autosize
            type="textarea"
            label="审批意见"
            :placeholder="actions?.currentNodeRequireComment ? '必填审批意见' : '选填审批意见'"
            :required="actions?.currentNodeRequireComment === true"
          />

          <div class="transition-popup__btns">
            <van-button block @click="showTransitionPopup = false">取消</van-button>
            <van-button block type="primary" :loading="transitionSubmitting" @click="confirmTransition">
              确认流转
            </van-button>
          </div>
        </div>
      </van-popup>

      <!-- 附件预览弹窗 -->
      <FilePreviewPopup
        v-model:show="previewShow"
        :file-id="previewFile?.fileId"
        :file-name="previewFile?.name || '文件'"
        :file-type="previewFile?.contentType || previewFile?.name"
        :url="previewFile?.url"
      />
    </template>

    <van-empty v-else description="需求不存在或无权查看" />
    </van-pull-refresh>
  </div>
</template>

<style scoped>
.detail-card {
  margin: 10px 12px 0;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
}

.detail-card__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.chip {
  font-size: 11px;
  padding: 1px 6px;
  border: 1px solid #dcdee0;
  border-radius: 4px;
  color: #969799;
}

.chip--status {
  border: none;
}

.detail-card__title {
  margin: 10px 0 4px;
  font-size: 17px;
  line-height: 1.4;
  color: #323233;
}

.detail-card__no {
  font-size: 12px;
  color: #969799;
  font-family: monospace;
}

.detail-desc {
  font-size: 14px;
  color: #646566;
  line-height: 1.6;
}

.flow-tip {
  font-size: 13px;
  color: #1989fa;
  margin-bottom: 4px;
}

.flow-history {
  margin-top: 16px;
  border-top: 1px solid #f2f3f5;
  padding-top: 12px;
}

.flow-history__title {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
  margin-bottom: 8px;
}

.flow-history__item {
  padding: 6px 0;
  border-bottom: 1px dashed #f2f3f5;
}

.flow-history__line {
  font-size: 13px;
  color: #323233;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.flow-history__role {
  color: #969799;
}

.flow-history__sub {
  font-size: 12px;
  color: #646566;
  margin-top: 2px;
}

.flow-history__time {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 2px;
}

.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

/* 从通知跳转定位到某条评论时的高亮 */
.comment-item--highlight {
  background: #fdf6ec;
  border-radius: 8px;
  box-shadow: 0 0 0 2px #f5dab1;
  transition: background 0.3s ease, box-shadow 0.3s ease;
}

.comment-item__head {
  display: flex;
  justify-content: space-between;
}

.comment-item__user {
  font-size: 13px;
  font-weight: 600;
  color: #1989fa;
}

.comment-item__time {
  font-size: 11px;
  color: #c8c9cc;
}

.comment-item__content {
  font-size: 14px;
  color: #323233;
  margin-top: 4px;
  line-height: 1.5;
}

.comment-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}

.comment-bar__input {
  flex: 1;
  background: #f7f8fa;
  border-radius: 18px;
  padding: 6px 12px;
}

.comment-bar :deep(.van-field__body) {
  min-height: 24px;
}

.page :deep(.van-collapse) {
  margin: 10px 12px 90px;
  border-radius: 10px;
  overflow: hidden;
}

.transition-popup {
  padding: 20px 16px calc(16px + env(safe-area-inset-bottom));
}

.transition-popup__title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
  text-align: center;
}

.transition-popup__scope {
  font-size: 12px;
  color: #969799;
  text-align: center;
  margin: 4px 0 8px;
}

.transition-popup__btns {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

/* 附件 */
.attach-group__title {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
  margin: 10px 0 4px;
}

.attach-group__header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.attach-group__node {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
}

.attach-group__meta {
  font-size: 11px;
  color: #969799;
}

.attach-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

.attach-item:active {
  background: #f7f8fa;
}

.attach-item__icon {
  font-size: 22px;
  color: #969799;
  flex-shrink: 0;
}

.attach-item__icon--image {
  color: #67c23a;
}

.attach-item__icon--office {
  color: #1989fa;
}

.attach-item__icon--text {
  color: #e6a23c;
}

.attach-item__main {
  flex: 1;
  min-width: 0;
}

.attach-item__name {
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attach-item__meta {
  font-size: 11px;
  color: #969799;
  margin-top: 2px;
}

.attach-item__action {
  font-size: 18px;
  color: #1989fa;
  flex-shrink: 0;
}

/* 子需求 */
.child-item {
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}

.child-item:active {
  background: #f7f8fa;
}

.child-item__title {
  font-size: 14px;
  color: #323233;
  line-height: 1.4;
}

.child-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 11px;
  color: #969799;
  font-family: monospace;
}

.child-item__status {
  font-family: sans-serif;
  padding: 1px 8px;
  border-radius: 10px;
}

/* 操作记录 */
.oplog-item {
  padding: 8px 0;
  border-bottom: 1px dashed #f2f3f5;
}

.oplog-item__line {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.oplog-item__operator {
  font-weight: 600;
  color: #323233;
}

.oplog-item__action {
  color: #969799;
}

.oplog-item__detail {
  font-size: 12px;
  color: #646566;
  margin-top: 2px;
}

.oplog-item__time {
  font-size: 11px;
  color: #c8c9cc;
  margin-top: 2px;
}
</style>
