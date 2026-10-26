<template>
  <div class="assistant-agent-panel" :class="{ 'is-streaming': isStreaming }">
    <button
      type="button"
      class="agent-panel__header"
      :aria-expanded="!collapsed"
      @click="toggleCollapsed"
    >
      <div class="agent-panel__header-left">
        <el-icon class="agent-panel__header-icon"><MagicStick /></el-icon>
        <span class="agent-panel__header-label">智能体执行</span>
        <span v-if="planSubtasks.length > 1" class="agent-panel__header-chip">
          {{ planSubtasks.length }} 个子任务
        </span>
        <span v-if="activeLabel" class="agent-panel__running-pill">
          <span class="agent-panel__pulse" />
          {{ activeLabel }}
        </span>
      </div>
      <el-icon class="agent-panel__header-arrow" :class="{ 'is-collapsed': collapsed }">
        <ArrowDown />
      </el-icon>
    </button>

    <div v-show="!collapsed" class="agent-panel__body">
      <!-- 任务拆分计划（多子任务时展示） -->
      <div v-if="planSubtasks.length > 1" class="agent-plan">
        <div class="agent-plan__title">任务拆分</div>
        <ol class="agent-plan__list">
          <li v-for="(subtask, index) in planSubtasks" :key="index" class="agent-plan__item">
            <span class="agent-plan__index">{{ index + 1 }}</span>
            <span class="agent-plan__name">{{ subtask.title }}</span>
            <span class="agent-plan__type">{{ typeLabel(subtask.type) }}</span>
          </li>
        </ol>
      </div>

      <!-- 子 Agent 运行卡片 -->
      <div v-for="run in subAgents" :key="run.id" class="agent-run" :class="`is-${run.status}`">
        <div class="agent-run__head">
          <span class="agent-run__indicator" aria-hidden="true">
            <span v-if="run.status === 'running'" class="indicator-spinner">
              <span class="indicator-spinner__ring" />
            </span>
            <el-icon v-else-if="run.status === 'completed'" class="is-done"><Check /></el-icon>
            <el-icon v-else-if="run.status === 'failed'" class="is-failed"><Close /></el-icon>
            <span v-else class="indicator-dot--pending" />
          </span>
          <span class="agent-run__title">{{ run.title }}</span>
          <span class="agent-run__type">{{ typeLabel(run.type) }}</span>
          <span v-if="run.modelLabel" class="agent-run__model" :title="`执行模型：${run.modelLabel}`">
            <el-icon><Cpu /></el-icon>
            {{ run.modelLabel }}
          </span>
          <span class="agent-run__status" :class="`is-${run.status}`">{{ statusLabel(run.status) }}</span>
        </div>

        <!-- 该子 Agent 的工具调用 -->
        <div v-if="run.toolCalls?.length" class="agent-run__tools">
          <div
            v-for="call in run.toolCalls"
            :key="call.id"
            class="tool-call"
            :class="`is-${call.status}`"
          >
            <div class="tool-call__head">
              <span class="tool-call__name">{{ call.title }}</span>
              <span class="tool-call__status" :class="`is-${call.status}`">
                {{ toolStatusLabel(call.status) }}
              </span>
            </div>
            <div v-if="call.argsPreview" class="tool-call__args">{{ call.argsPreview }}</div>
            <div v-if="call.resultPreview" class="tool-call__result">{{ call.resultPreview }}</div>
          </div>
        </div>
      </div>

      <!-- 主循环工具调用（单 Agent 路径） -->
      <div v-if="mainToolCalls.length" class="agent-run is-main">
        <div class="agent-run__head">
          <span class="agent-run__title">工具调用</span>
          <span class="agent-run__status">{{ mainToolCalls.length }} 次</span>
        </div>
        <div class="agent-run__tools">
          <div
            v-for="call in mainToolCalls"
            :key="call.id"
            class="tool-call"
            :class="`is-${call.status}`"
          >
            <div class="tool-call__head">
              <span class="tool-call__name">{{ call.title }}</span>
              <span class="tool-call__status" :class="`is-${call.status}`">
                {{ toolStatusLabel(call.status) }}
              </span>
            </div>
            <div v-if="call.argsPreview" class="tool-call__args">{{ call.argsPreview }}</div>
            <div v-if="call.resultPreview" class="tool-call__result">{{ call.resultPreview }}</div>
          </div>
        </div>
      </div>

      <div v-if="!subAgents.length && !mainToolCalls.length && !planSubtasks.length" class="agent-panel__empty">
        智能体准备中…
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, Check, Close, Cpu, MagicStick } from '@element-plus/icons-vue'
import type { AgentPlanInfo, AgentTrace, ToolCallRecord } from '@/types/assistant'

interface Props {
  plan?: AgentPlanInfo | null
  trace?: AgentTrace | null
  isStreaming?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  plan: null,
  trace: null,
  isStreaming: false,
})

const collapsed = ref(false)

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

const planSubtasks = computed(() => props.plan?.subtasks || props.trace?.plan?.subtasks || [])
const subAgents = computed(() => props.trace?.subAgents || [])
const mainToolCalls = computed<ToolCallRecord[]>(() => props.trace?.toolCalls || [])

const activeLabel = computed(() => {
  const runningAgent = subAgents.value.find(run => run.status === 'running')
  if (runningAgent) return runningAgent.title
  const runningTool = [...mainToolCalls.value, ...subAgents.value.flatMap(run => run.toolCalls || [])]
    .find(call => call.status === 'running' || call.status === 'awaiting_confirm')
  if (runningTool) return runningTool.status === 'awaiting_confirm' ? '等待确认' : runningTool.title
  return ''
})

function typeLabel(type: string) {
  const labels: Record<string, string> = {
    research: '检索调研',
    data_analysis: '数据分析',
    navigation: '操作导航',
    general: '通用执行',
  }
  return labels[type] || type
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '等待',
    running: '执行中',
    completed: '完成',
    failed: '失败',
  }
  return labels[status] || status
}

function toolStatusLabel(status: string) {
  const labels: Record<string, string> = {
    pending: '排队',
    running: '执行中',
    awaiting_confirm: '待确认',
    confirmed: '已确认',
    denied: '已拒绝',
    completed: '完成',
    failed: '失败',
  }
  return labels[status] || status
}
</script>

<style scoped lang="scss">
/* --- 智能体执行面板（视觉密度与任务面板一致） --- */
.assistant-agent-panel {
  margin-top: 12px;
  width: 100%;
  border: 1px solid #e4e7eb;
  border-radius: 12px;
  background: var(--color-surface);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}

.assistant-agent-panel.is-streaming {
  border-color: rgba(59, 130, 246, 0.35);
}

.agent-panel__header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px;
  border: none;
  background: linear-gradient(180deg, var(--color-surface-alt) 0%, #f6f7f9 100%);
  cursor: pointer;
  text-align: left;
}

.agent-panel__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.agent-panel__header-icon {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--color-primary);
}

.agent-panel__header-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: 0.2px;
}

.agent-panel__header-chip {
  font-size: 11px;
  color: var(--color-text-tertiary);
  padding: 1px 6px;
  background: var(--color-surface-alt);
  border-radius: 999px;
}

.agent-panel__running-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-success-text);
  background: #ecfdf5;
  border-radius: 999px;
  white-space: nowrap;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-panel__pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  flex-shrink: 0;
  animation: agent-pulse 1.4s ease-in-out infinite;
}

@keyframes agent-pulse {
  0%, 100% { opacity: 0.5; transform: scale(0.85); }
  50% { opacity: 1; transform: scale(1); }
}

.agent-panel__header-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-tertiary);
  transition: transform 0.2s ease;
}

.agent-panel__header-arrow.is-collapsed {
  transform: rotate(-90deg);
}

.agent-panel__body {
  border-top: 1px solid #f1f5f9;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.agent-panel__empty {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 任务拆分计划 */
.agent-plan__title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-muted-text);
  margin-bottom: 6px;
}

.agent-plan__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-plan__item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.agent-plan__index {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-surface-alt);
  color: var(--color-muted-text);
  font-size: 10px;
  font-weight: 600;
}

.agent-plan__name {
  color: var(--color-text-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-plan__type {
  margin-left: auto;
  flex-shrink: 0;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
  background: var(--color-surface-alt);
  padding: 1px 6px;
  border-radius: 999px;
}

/* 子 Agent 卡片 */
.agent-run {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: var(--color-surface);
}

.agent-run.is-running {
  border-color: rgba(16, 185, 129, 0.35);
}

.agent-run.is-failed {
  border-color: rgba(244, 63, 94, 0.35);
}

.agent-run__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.agent-run__indicator {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;

  .is-done { color: var(--color-text-tertiary); font-size: 12px; }
  .is-failed { color: #f43f5e; font-size: 11px; font-weight: 700; }
}

.agent-run__title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-run__type,
.agent-run__status {
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 999px;
  white-space: nowrap;
}

.agent-run__type {
  color: var(--color-muted-text);
  background: var(--color-surface-alt);
}

.agent-run__status {
  background: var(--color-surface-alt);
  color: var(--color-text-tertiary);
}

.agent-run__status.is-running { background: #d1fae5; color: var(--color-success-text); }
.agent-run__status.is-failed { background: var(--color-danger-bg); color: var(--color-danger-text); }

.agent-run__model {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: var(--color-primary);
  background: rgba(59, 130, 246, 0.08);
  padding: 1px 6px;
  border-radius: 999px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 工具调用条目 */
.agent-run__tools {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 6px 8px;
  background: var(--color-background);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-call.is-running { border-color: rgba(16, 185, 129, 0.4); }
.tool-call.is-awaiting_confirm,
.tool-call.is-confirmed { border-color: rgba(245, 158, 11, 0.4); }
.tool-call.is-failed,
.tool-call.is-denied { border-color: rgba(244, 63, 94, 0.35); }

.tool-call__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-call__name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.tool-call__status {
  margin-left: auto;
  font-size: 10.5px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--color-surface-alt);
  color: var(--color-text-tertiary);
  white-space: nowrap;
}

.tool-call__status.is-running { background: #d1fae5; color: var(--color-success-text); }
.tool-call__status.is-awaiting_confirm,
.tool-call__status.is-confirmed { background: #fef3c7; color: #92400e; }
.tool-call__status.is-completed { background: var(--color-surface-alt); color: var(--color-muted-text); }
.tool-call__status.is-denied,
.tool-call__status.is-failed { background: var(--color-danger-bg); color: var(--color-danger-text); }

.tool-call__args,
.tool-call__result {
  font-size: 11px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  word-break: break-word;
}

.tool-call__args {
  color: var(--color-text-tertiary);
}

/* 与任务面板一致的指示器 */
.indicator-spinner {
  position: relative;
  width: 14px;
  height: 14px;
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
  animation: agent-spin 0.9s linear infinite;
}

@keyframes agent-spin {
  to { transform: rotate(360deg); }
}

.indicator-dot--pending {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #cbd5e1;
}
</style>
