<template>
  <div class="assistant-task-panel" :class="{ 'is-streaming': isStreaming }">
    <!-- 头部：标签 + 进度 + 折叠按钮 -->
    <button
      type="button"
      class="assistant-task-panel__header"
      :aria-expanded="!collapsed"
      @click="toggleCollapsed"
    >
      <div class="assistant-task-panel__header-left">
        <el-icon class="assistant-task-panel__header-icon"><List /></el-icon>
        <span class="assistant-task-panel__header-label">任务列表</span>
        <span class="assistant-task-panel__header-count">
          {{ completedCount }}/{{ tasks.length }}
        </span>
        <span v-if="runningTaskTitle" class="assistant-task-panel__running-pill">
          <span class="assistant-task-panel__pulse" />
          {{ runningTaskTitle }}
        </span>
      </div>
      <el-icon
        class="assistant-task-panel__header-arrow"
        :class="{ 'is-collapsed': collapsed }"
      >
        <ArrowDown />
      </el-icon>
    </button>

    <!-- 任务列表主体 -->
    <div v-show="!collapsed" class="assistant-task-panel__body">
      <ol class="assistant-task-list">
        <li
          v-for="task in tasks"
          :key="task.id"
          class="assistant-task-item"
          :class="`is-${task.status}`"
        >
          <!-- 状态指示器：左对齐的圆点 + 旋转环 -->
          <div class="assistant-task-item__indicator" aria-hidden="true">
            <span v-if="task.status === 'pending'" class="indicator-dot indicator-dot--pending" />
            <span v-else-if="task.status === 'running'" class="indicator-spinner">
              <span class="indicator-spinner__ring" />
              <span class="indicator-spinner__core" />
            </span>
            <span v-else-if="task.status === 'completed'" class="indicator-check">
              <el-icon><Check /></el-icon>
            </span>
            <span v-else-if="task.status === 'failed'" class="indicator-cross">
              <el-icon><Close /></el-icon>
            </span>
          </div>

          <!-- 任务正文 -->
          <div class="assistant-task-item__body">
            <div class="assistant-task-item__title-row">
              <span class="assistant-task-item__title">{{ task.title }}</span>
              <span
                v-if="task.status === 'running'"
                class="assistant-task-item__status is-running"
              >进行中</span>
              <span
                v-else-if="task.status === 'completed'"
                class="assistant-task-item__status is-done"
              >完成</span>
              <span
                v-else-if="task.status === 'failed'"
                class="assistant-task-item__status is-failed"
              >失败</span>
              <span
                v-else
                class="assistant-task-item__status is-pending"
              >等待</span>
              <span
                v-if="task.completedAt && task.startedAt"
                class="assistant-task-item__elapsed"
              >
                {{ formatTaskElapsed(task.startedAt, task.completedAt) }}
              </span>
            </div>

            <!-- 折叠日志按钮 + 日志内容 -->
            <div v-if="task.logs?.length" class="assistant-task-item__logs-wrap">
              <button
                type="button"
                class="assistant-task-item__logs-toggle"
                :class="{ 'is-open': isLogOpen(task.id) }"
                @click.stop="toggleLog(task.id)"
              >
                <el-icon class="assistant-task-item__logs-toggle-icon">
                  <ArrowRight />
                </el-icon>
                <span v-if="!isLogOpen(task.id)">
                  {{ task.logs.length }} 条过程日志
                </span>
                <span v-else>收起日志</span>
              </button>
              <Transition name="logs">
                <div v-show="isLogOpen(task.id)" class="assistant-task-item__logs">
                  <div
                    v-for="(log, li) in task.logs"
                    :key="li"
                    class="assistant-task-item__log"
                    :class="`is-${log.level}`"
                  >
                    <span class="assistant-task-item__log-time">{{ formatLogTime(log.timestamp) }}</span>
                    <span class="assistant-task-item__log-msg">{{ log.message }}</span>
                  </div>
                </div>
              </Transition>
            </div>
          </div>
        </li>
      </ol>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ArrowDown, ArrowRight, Check, Close, List } from '@element-plus/icons-vue'
import type { AssistantTask } from '@/types/assistant'

interface Props {
  /** 任务列表 */
  tasks: AssistantTask[]
  /** 是否处于流式状态（流式中默认展开，便于用户看到实时进度） */
  isStreaming?: boolean
  /** 初始折叠状态 */
  defaultCollapsed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false,
  defaultCollapsed: false,
})

const collapsed = ref(props.defaultCollapsed)

// 流式开始时自动展开；流式结束后保留当前折叠状态
watch(() => props.isStreaming, (val) => {
  if (val) collapsed.value = false
})

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

const completedCount = computed(() => props.tasks.filter(t => t.status === 'completed').length)

const runningTask = computed(() => props.tasks.find(t => t.status === 'running') || null)
const runningTaskTitle = computed(() => {
  if (!runningTask.value) return ''
  // 去掉「中…」后缀避免与徽章重复
  return runningTask.value.title.replace(/中…?$/, '').trim()
})

const logOpenState = reactive<Record<string, boolean>>({})

function isLogOpen(taskId: string) {
  return !!logOpenState[taskId]
}

function toggleLog(taskId: string) {
  logOpenState[taskId] = !logOpenState[taskId]
}

function formatTaskElapsed(startedAt: number, completedAt: number) {
  const ms = completedAt - startedAt
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.round(ms / 60000)}m${Math.round((ms % 60000) / 1000)}s`
}

function formatLogTime(timestamp: number) {
  const d = new Date(timestamp)
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  const ms = String(d.getMilliseconds()).padStart(3, '0')
  return `${h}:${m}:${s}.${ms}`
}
</script>

<style scoped lang="scss">
/* ===== 任务面板（对标 WorkBuddy 视觉）===== */
.assistant-task-panel {
  margin-top: 12px;
  width: 100%;
  border: 1px solid #e4e7eb;
  border-radius: 12px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.assistant-task-panel.is-streaming {
  border-color: rgba(16, 185, 129, 0.35);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03), 0 0 0 1px rgba(16, 185, 129, 0.08);
}

/* 头部 */
.assistant-task-panel__header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  background: linear-gradient(180deg, #fafbfc 0%, #f6f7f9 100%);
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease;
}

.assistant-task-panel__header:hover {
  background: linear-gradient(180deg, #f4f6f9 0%, #eef1f5 100%);
}

.assistant-task-panel__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.assistant-task-panel__header-icon {
  flex-shrink: 0;
  font-size: 14px;
  color: #475569;
}

.assistant-task-panel__header-label {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
  letter-spacing: 0.2px;
}

.assistant-task-panel__header-count {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #94a3b8;
  font-weight: 500;
  padding: 1px 6px;
  background: #f1f5f9;
  border-radius: 999px;
}

.assistant-task-panel__running-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 4px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: #047857;
  background: #ecfdf5;
  border-radius: 999px;
  white-space: nowrap;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-task-panel__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  flex-shrink: 0;
  animation: task-pulse 1.4s ease-in-out infinite;
}

@keyframes task-pulse {
  0%, 100% {
    transform: scale(0.85);
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4);
  }
  50% {
    transform: scale(1);
    box-shadow: 0 0 0 4px rgba(16, 185, 129, 0);
  }
}

.assistant-task-panel__header-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
  transition: transform 0.2s ease;
}

.assistant-task-panel__header-arrow.is-collapsed {
  transform: rotate(-90deg);
}

/* 任务列表主体 */
.assistant-task-panel__body {
  border-top: 1px solid #f1f5f9;
  padding: 4px 0;
}

.assistant-task-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* 任务项 */
.assistant-task-item {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
  position: relative;
  transition: background 0.18s ease;

  &:hover {
    background: #fafbfc;
  }

  &::after {
    content: '';
    position: absolute;
    left: 24px;
    right: 14px;
    bottom: 0;
    height: 1px;
    background: #f1f5f9;
  }

  &:last-child::after {
    display: none;
  }
}

.assistant-task-item.is-running {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.04) 0%, rgba(16, 185, 129, 0) 60%);
}

.assistant-task-item.is-failed {
  background: linear-gradient(90deg, rgba(244, 63, 94, 0.04) 0%, rgba(244, 63, 94, 0) 60%);
}

/* 状态指示器 */
.assistant-task-item__indicator {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.indicator-dot--pending {
  background: #cbd5e1;
  width: 6px;
  height: 6px;
}

/* 进行中：双层旋转环（外圈旋转，内核脉冲） */
.indicator-spinner {
  position: relative;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.indicator-spinner__ring {
  position: absolute;
  inset: 0;
  border: 1.5px solid rgba(16, 185, 129, 0.18);
  border-top-color: #10b981;
  border-radius: 50%;
  animation: task-spin 0.9s linear infinite;
}

.indicator-spinner__core {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  animation: task-core-pulse 1.2s ease-in-out infinite;
}

@keyframes task-spin {
  to { transform: rotate(360deg); }
}

@keyframes task-core-pulse {
  0%, 100% {
    transform: scale(0.8);
    opacity: 0.75;
  }
  50% {
    transform: scale(1.15);
    opacity: 1;
  }
}

/* 已完成：淡灰勾 */
.indicator-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 13px;

  :deep(svg) {
    width: 14px;
    height: 14px;
  }
}

/* 失败：红色 × */
.indicator-cross {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #f43f5e;
  font-size: 12px;
  font-weight: 700;
  background: #fef2f2;
  border-radius: 50%;
  width: 16px;
  height: 16px;

  :deep(svg) {
    width: 11px;
    height: 11px;
  }
}

/* 任务正文 */
.assistant-task-item__body {
  flex: 1;
  min-width: 0;
}

.assistant-task-item__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.assistant-task-item__title {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
  line-height: 1.5;
  word-break: break-word;
}

.assistant-task-item.is-pending .assistant-task-item__title {
  color: #94a3b8;
}

.assistant-task-item.is-running .assistant-task-item__title {
  color: #047857;
  font-weight: 600;
}

.assistant-task-item.is-completed .assistant-task-item__title {
  color: #475569;
}

.assistant-task-item.is-failed .assistant-task-item__title {
  color: #be123c;
}

/* 状态徽章（克制语义色） */
.assistant-task-item__status {
  font-size: 10.5px;
  font-weight: 500;
  padding: 1px 6px;
  border-radius: 999px;
  line-height: 1.5;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.assistant-task-item__status.is-running {
  background: #d1fae5;
  color: #047857;
}

.assistant-task-item__status.is-done {
  background: #f1f5f9;
  color: #64748b;
}

.assistant-task-item__status.is-failed {
  background: #fee2e2;
  color: #b91c1c;
}

.assistant-task-item__status.is-pending {
  background: #f1f5f9;
  color: #94a3b8;
}

.assistant-task-item__elapsed {
  margin-left: auto;
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 日志折叠 */
.assistant-task-item__logs-wrap {
  margin-top: 6px;
}

.assistant-task-item__logs-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  font-size: 11px;
  color: #64748b;
  cursor: pointer;
  transition: color 0.15s ease;

  &:hover {
    color: #2563eb;
  }
}

.assistant-task-item__logs-toggle.is-open {
  color: #2563eb;
}

.assistant-task-item__logs-toggle-icon {
  font-size: 10px;
  transition: transform 0.2s ease;
}

.assistant-task-item__logs-toggle.is-open .assistant-task-item__logs-toggle-icon {
  transform: rotate(90deg);
}

.assistant-task-item__logs {
  margin-top: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  max-height: 180px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', 'SF Mono', 'Consolas', 'Monaco', 'Courier New', monospace;
}

.assistant-task-item__log {
  display: flex;
  gap: 8px;
  font-size: 11px;
  line-height: 1.7;
  word-break: break-word;
}

.assistant-task-item__log-time {
  flex-shrink: 0;
  color: #94a3b8;
}

.assistant-task-item__log-msg {
  color: #475569;
}

.assistant-task-item__log.is-warn .assistant-task-item__log-msg {
  color: #c2410c;
}

.assistant-task-item__log.is-error .assistant-task-item__log-msg {
  color: #be123c;
}

/* 日志展开/收起动画 */
.logs-enter-active,
.logs-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.logs-enter-from,
.logs-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>