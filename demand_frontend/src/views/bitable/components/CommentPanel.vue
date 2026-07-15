<template>
  <el-drawer
    :model-value="visible"
    title="评论"
    direction="rtl"
    size="400px"
    @close="emit('close')"
  >
    <div class="comment-panel">
      <!-- 评论列表 -->
      <div class="comment-list">
        <div v-if="loading" class="comment-loading">
          <el-skeleton :rows="3" animated />
        </div>
        <div v-else-if="!comments.length" class="comment-empty">
          <el-empty description="暂无评论" :image-size="60" />
        </div>
        <div
          v-for="comment in comments"
          :key="comment.id"
          class="comment-item"
        >
          <div class="comment-avatar">
            <el-avatar :size="32" :src="comment.avatar">
              {{ (comment.userName || '?')[0] }}
            </el-avatar>
          </div>
          <div class="comment-body">
            <div class="comment-header">
              <span class="comment-user">{{ comment.userName || '未知用户' }}</span>
              <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            </div>
            <div v-if="comment.quoteFieldId" class="comment-quote">
              引用了字段
            </div>
            <div class="comment-content">{{ comment.content }}</div>
            <div class="comment-actions">
              <el-button
                link
                size="small"
                type="danger"
                @click="handleDelete(comment.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 评论输入 -->
      <div class="comment-input">
        <el-input
          v-model="newComment"
          type="textarea"
          placeholder="输入评论..."
          :rows="3"
          maxlength="2000"
          show-word-limit
        />
        <el-button
          class="comment-submit"
          type="primary"
          size="small"
          :loading="sending"
          :disabled="!newComment.trim()"
          @click="handleSubmit"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { listComments, createComment, deleteComment } from '@/api/modules/bitable'
import type { BitableComment } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  recordId: number | null
  tableId: number | null
}>()

const emit = defineEmits<{
  close: []
}>()

const comments = ref<BitableComment[]>([])
const loading = ref(false)
const sending = ref(false)
const newComment = ref('')

watch(
  () => [props.visible, props.recordId] as const,
  async ([visible, recordId]) => {
    if (visible && recordId != null) {
      await loadComments(recordId)
    } else {
      comments.value = []
    }
  }
)

async function loadComments(recordId: number) {
  loading.value = true
  try {
    const res = await listComments(recordId)
    if (Array.isArray(res)) {
      comments.value = res
    } else if (res && typeof res === 'object' && 'data' in res) {
      comments.value = (res as { data: BitableComment[] }).data ?? []
    } else {
      comments.value = []
    }
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '加载评论失败'))
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const content = newComment.value.trim()
  if (!content || props.recordId == null || props.tableId == null) return

  sending.value = true
  try {
    const res = await createComment(props.recordId, { content, tableId: props.tableId })
    if (res && typeof res === 'object' && 'id' in res && 'content' in res) {
      comments.value.push(res as BitableComment)
    } else {
      ElMessage.error('评论创建返回格式异常')
    }
    newComment.value = ''
    ElMessage.success('评论发送成功')
  } catch (e: any) {
    ElMessage.error(resolveErrorMessage(e, '发送评论失败'))
  } finally {
    sending.value = false
  }
}

async function handleDelete(commentId: number) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteComment(commentId)
    comments.value = comments.value.filter((c) => c.id !== commentId)
    ElMessage.success('删除成功')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(resolveErrorMessage(e, '删除失败'))
    }
  }
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  if (diffHour < 24) return `${diffHour}小时前`
  if (diffDay < 30) return `${diffDay}天前`

  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${mi}`
}
</script>

<style scoped lang="scss">
.comment-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}

.comment-loading {
  padding: 16px 0;
}

.comment-empty {
  margin-top: 40px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);

  &:last-child {
    border-bottom: none;
  }
}

.comment-avatar {
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-user {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.comment-quote {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
  padding: 2px 8px;
  background: var(--color-background);
  border-radius: var(--radius-sm);
}

.comment-content {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  line-height: 1.6;
  word-break: break-all;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.comment-item:hover .comment-actions {
  opacity: 1;
}

.comment-input {
  padding: 12px 0 0;
  border-top: 1px solid var(--color-border);

  .comment-submit {
    margin-top: 8px;
    width: 100%;
  }
}
</style>