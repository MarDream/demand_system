<template>
  <el-drawer
    :model-value="visible"
    title="AI 对话查询"
    direction="rtl"
    size="520px"
    @close="handleClose"
  >
    <div class="ai-chat-panel">
      <!-- 对话区 -->
      <div class="chat-messages">
        <div v-if="!messages.length" class="chat-empty">
          <el-empty description="输入问题，AI 帮你查询数据" :image-size="80" />
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="['chat-message', msg.role]"
        >
          <div class="chat-avatar">
            <el-avatar :size="32">
              {{ msg.role === 'user' ? '我' : 'AI' }}
            </el-avatar>
          </div>
          <div class="chat-body">
            <div class="chat-content">{{ msg.content }}</div>
            <div v-if="msg.records && msg.records.length" class="chat-records">
              <div class="records-title">匹配记录 ({{ msg.records.length }})</div>
              <div
                v-for="record in msg.records"
                :key="record.recordId"
                class="record-item"
                @click="emit('selectRecord', record.recordId)"
              >
                <span class="record-id">#{{ record.recordId }}</span>
                <span class="record-text">{{ record.displayText || '(无文本)' }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="chat-message assistant">
          <div class="chat-avatar">
            <el-avatar :size="32">AI</el-avatar>
          </div>
          <div class="chat-body">
            <div class="chat-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              AI 思考中...
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-input
          v-model="question"
          type="textarea"
          placeholder="输入问题，例如：哪些需求优先级最高？"
          :rows="3"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!question.trim()"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { resolveErrorMessage } from '@/utils/error'
import { Loading } from '@element-plus/icons-vue'
import { query, type AiQueryResult, type AiRecordMatch } from '@/api/modules/bitableAi'

const props = defineProps<{
  visible: boolean
  baseId: number
  tableId?: number
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'selectRecord', recordId: number): void
}>()

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  records?: AiRecordMatch[]
}

const question = ref('')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])

async function handleSend() {
  const q = question.value.trim()
  if (!q || loading.value) return

  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true

  try {
    const result: AiQueryResult = await query(props.baseId, props.tableId, q)
    messages.value.push({
      role: 'assistant',
      content: result.answer,
      records: result.matchedRecords,
    })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 查询失败，请重试'))
    messages.value.push({
      role: 'assistant',
      content: '查询失败，请稍后重试。',
    })
  } finally {
    loading.value = false
  }
}

function handleClose() {
  messages.value = []
  question.value = ''
  emit('close')
}
</script>

<style scoped>
.ai-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.chat-message {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.chat-avatar {
  flex-shrink: 0;
}

.chat-body {
  max-width: 75%;
}

.chat-content {
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  word-break: break-word;
}

.chat-message.user .chat-content {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
}

.chat-records {
  margin-top: 8px;
}

.records-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.record-item {
  display: flex;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.record-item:hover {
  background: var(--el-fill-color-light);
}

.record-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.record-text {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-loading {
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}

.chat-input {
  border-top: 1px solid var(--el-border-color-lighter);
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
