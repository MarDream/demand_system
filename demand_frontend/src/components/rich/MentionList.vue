<template>
  <div class="mention-popup">
    <div v-if="items.length" class="mention-popup__list">
      <div
        v-for="(item, index) in items"
        :key="item.id"
        class="mention-popup__item"
        :class="{ 'is-active': index === selectedIndex }"
        @mousedown.prevent="selectItem(index)"
        @mouseenter="selectedIndex = index"
      >
        <el-avatar :size="22" :src="item.avatar || undefined" class="mention-popup__avatar">
          {{ (item.realName || item.username || 'U').charAt(0) }}
        </el-avatar>
        <span class="mention-popup__name">{{ item.realName || item.username }}</span>
        <span v-if="item.realName && item.username" class="mention-popup__account">{{ item.username }}</span>
      </div>
    </div>
    <div v-else class="mention-popup__empty">未找到匹配的用户</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 评论 @ 提及的候选用户浮层。
 *
 * 由 tiptap Suggestion 插件驱动，外层通过 defineExpose 暴露 update / onKeyDown：
 * 键盘上下移动、回车选中，鼠标点击选中，均回调 command 完成插入。
 * 候选与命令通过 update() 注入（组件本身不接 props，避免只读 props 赋值）。
 */
import { ref } from 'vue'
import type { MentionCandidate, MentionCommand } from '@/types/mention'

const items = ref<MentionCandidate[]>([])
const command = ref<MentionCommand>(() => {})
const selectedIndex = ref(0)

function selectItem(index: number) {
  const item = items.value[index]
  if (!item) return
  command.value({ id: item.id, label: item.realName || item.username || String(item.id) })
}

/** 供外层 Suggestion render 调用：候选/命令更新时同步 */
function update(payload: { items: MentionCandidate[]; command: MentionCommand }) {
  items.value = payload.items || []
  command.value = payload.command
  selectedIndex.value = 0
}

/** 键盘事件由 tiptap 转发进来；返回 true 表示已消费该按键 */
function onKeyDown({ event }: { event: KeyboardEvent }): boolean {
  if (!items.value.length) return false
  if (event.key === 'ArrowUp') {
    selectedIndex.value = (selectedIndex.value + items.value.length - 1) % items.value.length
    return true
  }
  if (event.key === 'ArrowDown') {
    selectedIndex.value = (selectedIndex.value + 1) % items.value.length
    return true
  }
  if (event.key === 'Enter' || event.key === 'Tab') {
    selectItem(selectedIndex.value)
    return true
  }
  return false
}

defineExpose({ update, onKeyDown })
</script>

<style scoped>
.mention-popup {
  min-width: 200px;
  max-width: 320px;
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.16);
}

.mention-popup__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-primary, #303133);
}

.mention-popup__item.is-active {
  background: var(--el-fill-color-light, #f5f7fa);
}

.mention-popup__avatar {
  flex-shrink: 0;
}

.mention-popup__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mention-popup__account {
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
}

.mention-popup__empty {
  padding: 8px 10px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
</style>
