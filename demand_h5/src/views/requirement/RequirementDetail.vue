<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog, showLoadingToast, closeToast } from 'vant'
import {
  getById,
  getComments,
  createComment,
  getAvailableActions,
  transition,
  getTransitionHistory,
  followRequirement,
  unfollowRequirement,
  type Requirement,
  type RequirementComment,
  type WorkflowAvailableActions,
  type AvailableTransition,
  type TransitionHistoryItem,
} from '@/api/requirement'
import { statusColor, statusLabel, priorityInfo, typeLabel, formatTime, stripHtml } from '@/utils/format'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const requirementId = Number(route.params.id)

const detail = ref<Requirement | null>(null)
const comments = ref<RequirementComment[]>([])
const actions = ref<WorkflowAvailableActions | null>(null)
const history = ref<TransitionHistoryItem[]>([])
const loading = ref(true)

const activeCollapse = ref<string[]>(['desc', 'comments', 'flow'])

// 评论
const commentText = ref('')
const commentSubmitting = ref(false)

// 流转弹窗
const showTransitionPopup = ref(false)
const currentTransition = ref<AvailableTransition | null>(null)
const selectedAssigneeId = ref<number | null>(null)
const transitionComment = ref('')
const transitionSubmitting = ref(false)

const canFollow = computed(() => detail.value && detail.value.followed === false)
const canUnfollow = computed(() => detail.value && detail.value.followed === true)

async function loadAll() {
  loading.value = true
  try {
    const tasks = [
      getById(requirementId).then((d) => (detail.value = d)),
      getComments(requirementId).then((c) => (comments.value = c || [])),
      getAvailableActions(requirementId).then((a) => (actions.value = a)).catch(() => null),
      getTransitionHistory(requirementId).then((h) => (history.value = h || [])).catch(() => null),
    ]
    await Promise.all(tasks)
  } finally {
    loading.value = false
  }
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

onMounted(loadAll)
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
            <van-cell title="截止时间" :value="formatTime(detail.dueDate)" />
            <van-cell title="创建时间" :value="formatTime(detail.createdAt)" />
          </van-cell-group>
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
          <div v-for="c in comments" :key="c.id" class="comment-item">
            <div class="comment-item__head">
              <span class="comment-item__user">{{ c.userName || `用户${c.userId}` }}</span>
              <span class="comment-item__time">{{ formatTime(c.createdAt) }}</span>
            </div>
            <div class="comment-item__content pre-wrap">{{ c.content }}</div>
          </div>
          <van-empty v-if="!comments.length" image-size="56px" description="暂无评论" />
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
            :required="actions?.currentNodeRequireComment"
          />

          <div class="transition-popup__btns">
            <van-button block @click="showTransitionPopup = false">取消</van-button>
            <van-button block type="primary" :loading="transitionSubmitting" @click="confirmTransition">
              确认流转
            </van-button>
          </div>
        </div>
      </van-popup>
    </template>

    <van-empty v-else description="需求不存在或无权查看" />
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
</style>
