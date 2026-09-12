<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import {
  getSessions,
  createSession,
  getMessages,
  deleteSession,
  streamMessage,
  type AssistantSession,
  type AssistantMessage,
} from '@/api/assistant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

interface ChatItem {
  id: number | string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}

const sessions = ref<AssistantSession[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<ChatItem[]>([])
const inputText = ref('')
const sending = ref(false)
const showSessions = ref(false)
const messagesEl = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight
    }
  })
}

async function loadSessions() {
  try {
    sessions.value = (await getSessions()) || []
  } catch {
    sessions.value = []
  }
}

async function openSession(id: number) {
  if (sending.value) return
  currentSessionId.value = id
  showSessions.value = false
  try {
    const list = (await getMessages(id)) || []
    messages.value = list.map((m: AssistantMessage) => ({ id: m.id, role: m.role, content: m.content }))
    scrollToBottom()
  } catch {
    messages.value = []
  }
}

async function newChat() {
  if (sending.value) return
  currentSessionId.value = null
  messages.value = []
  showSessions.value = false
}

async function removeSession(id: number) {
  try {
    await showConfirmDialog({ title: '删除会话', message: '确定删除该会话吗？' })
  } catch {
    return
  }
  await deleteSession(id)
  if (currentSessionId.value === id) {
    currentSessionId.value = null
    messages.value = []
  }
  showToast('已删除')
  await loadSessions()
}

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value) return
  sending.value = true
  inputText.value = ''

  try {
    if (!currentSessionId.value) {
      const session = await createSession({ title: text.slice(0, 30) })
      currentSessionId.value = session.id
      await loadSessions()
    }

    messages.value.push({ id: `u-${Date.now()}`, role: 'user', content: text })
    const assistantItem: ChatItem = { id: `a-${Date.now()}`, role: 'assistant', content: '', streaming: true }
    messages.value.push(assistantItem)
    scrollToBottom()

    abortController = new AbortController()
    await streamMessage(
      currentSessionId.value,
      text,
      {
        onDelta: (delta) => {
          assistantItem.content += delta
          scrollToBottom()
        },
        onDone: () => {
          assistantItem.streaming = false
          scrollToBottom()
        },
        onError: (msg) => {
          assistantItem.streaming = false
          if (!assistantItem.content) {
            assistantItem.content = `⚠️ ${msg || '请求失败'}`
          }
          showToast(msg || '请求失败')
        },
      },
      abortController.signal,
    )
    assistantItem.streaming = false
    if (!assistantItem.content) {
      assistantItem.content = '⚠️ 未收到回复，请稍后重试'
    }
    await loadSessions()
  } catch (e) {
    const err = e as Error
    if (err.name !== 'AbortError') {
      showToast(err.message || '发送失败')
      // 回复占位失败时移除空泡
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant' && !last.content) {
        messages.value.pop()
      }
    }
  } finally {
    sending.value = false
    abortController = null
    scrollToBottom()
  }
}

function stopStream() {
  abortController?.abort()
}

onMounted(async () => {
  await userStore.fetchUserInfo().catch(() => undefined)
  await loadSessions()
})
</script>

<template>
  <div class="page chat-page">
    <van-nav-bar title="AI 助手" fixed placeholder safe-area-inset-top>
      <template #left>
        <van-icon name="bars" size="20" @click="showSessions = true" />
      </template>
      <template #right>
        <van-icon name="plus" size="22" @click="newChat" />
      </template>
    </van-nav-bar>

    <div ref="messagesEl" class="chat-body">
      <div v-if="!messages.length" class="chat-welcome">
        <div class="chat-welcome__icon">🤖</div>
        <div class="chat-welcome__title">你好，{{ userStore.userInfo?.realName || '朋友' }}</div>
        <div class="chat-welcome__desc">我是需求管理平台的 AI 助手，可以帮你查询需求、解答问题、检索知识库。</div>
      </div>

      <div
        v-for="item in messages"
        :key="item.id"
        class="bubble-row"
        :class="{ 'bubble-row--user': item.role === 'user' }"
      >
        <div class="bubble" :class="{ 'bubble--user': item.role === 'user' }">
          <span class="pre-wrap">{{ item.content }}</span>
          <span v-if="item.streaming" class="cursor">▌</span>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <van-field
        v-model="inputText"
        placeholder="输入你的问题…"
        rows="1"
        autosize
        type="textarea"
        class="chat-input__field"
        @keydown.enter.exact.prevent="send"
      />
      <van-button v-if="sending" size="small" round plain type="danger" @click="stopStream">
        停止
      </van-button>
      <van-button v-else type="primary" size="small" round :disabled="!inputText.trim()" @click="send">
        发送
      </van-button>
    </div>

    <van-popup v-model:show="showSessions" position="left" :style="{ width: '78%', height: '100%' }">
      <div class="session-panel">
        <van-button block type="primary" icon="plus" @click="newChat">新对话</van-button>
        <div class="session-panel__list">
          <van-swipe-cell v-for="s in sessions" :key="s.id">
            <div
              class="session-item"
              :class="{ 'session-item--active': s.id === currentSessionId }"
              @click="openSession(s.id)"
            >
              <div class="session-item__title">{{ s.title || '新对话' }}</div>
              <div class="session-item__preview">{{ s.lastMessagePreview || '' }}</div>
            </div>
            <template #right>
              <van-button square type="danger" text="删除" style="height: 100%" @click="removeSession(s.id)" />
            </template>
          </van-swipe-cell>
          <van-empty v-if="!sessions.length" image-size="60px" description="暂无历史会话" />
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  /* 为 App.vue 的固定底部 TabBar 预留空间，输入栏不能被遮挡 */
  height: calc(100vh - 50px - env(safe-area-inset-bottom));
  min-height: 0;
  padding-bottom: env(safe-area-inset-bottom);
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 12px 8px;
}

.chat-welcome {
  text-align: center;
  padding-top: 80px;
}

.chat-welcome__icon {
  font-size: 48px;
}

.chat-welcome__title {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}

.chat-welcome__desc {
  margin-top: 8px;
  font-size: 13px;
  color: #969799;
  padding: 0 40px;
  line-height: 1.6;
}

.bubble-row {
  display: flex;
  margin-bottom: 12px;
}

.bubble-row--user {
  justify-content: flex-end;
}

.bubble {
  max-width: 80%;
  padding: 9px 12px;
  border-radius: 12px;
  background: #fff;
  font-size: 14px;
  line-height: 1.55;
  color: #323233;
  word-break: break-word;
}

.bubble--user {
  background: #1989fa;
  color: #fff;
}

.cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}

.chat-input__field {
  flex: 1;
  background: #f7f8fa;
  border-radius: 18px;
  padding: 6px 12px;
}

.session-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px 12px;
}

.session-panel__list {
  flex: 1;
  overflow-y: auto;
  margin-top: 12px;
}

.session-item {
  padding: 12px;
  border-bottom: 1px solid #f2f3f5;
}

.session-item--active {
  background: #f0f7ff;
}

.session-item__title {
  font-size: 14px;
  font-weight: 600;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__preview {
  margin-top: 4px;
  font-size: 12px;
  color: #969799;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
