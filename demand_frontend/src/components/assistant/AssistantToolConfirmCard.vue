<template>
  <div class="tool-confirm-card">
    <div class="tool-confirm-card__head">
      <el-icon class="tool-confirm-card__icon"><WarningFilled /></el-icon>
      <span class="tool-confirm-card__title">智能体请求执行写操作</span>
      <span class="tool-confirm-card__tool">{{ payload.title }}</span>
    </div>
    <div v-if="payload.argsPreview" class="tool-confirm-card__args">{{ payload.argsPreview }}</div>
    <div class="tool-confirm-card__hint">
      允许后智能体将以上述参数执行该操作；拒绝则跳过继续回答。
    </div>
    <div class="tool-confirm-card__actions">
      <el-button size="small" :disabled="submitting" @click="handleConfirm(false)">拒绝</el-button>
      <el-button size="small" type="primary" :loading="submitting" @click="handleConfirm(true)">
        允许执行
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import type { PermissionRequestPayload } from '@/types/assistant'

interface Props {
  payload: PermissionRequestPayload
}

interface Emits {
  (e: 'confirm', requestId: string, approved: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const submitting = ref(false)

async function handleConfirm(approved: boolean) {
  if (submitting.value) return
  submitting.value = true
  emit('confirm', props.payload.requestId, approved)
}
</script>

<style scoped lang="scss">
.tool-confirm-card {
  margin-top: 10px;
  width: 100%;
  border: 1px solid rgba(245, 158, 11, 0.5);
  border-radius: 12px;
  background: #fffbeb;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-confirm-card__head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tool-confirm-card__icon {
  color: #d97706;
  font-size: 14px;
}

.tool-confirm-card__title {
  font-size: 12.5px;
  font-weight: 600;
  color: #92400e;
}

.tool-confirm-card__tool {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(217, 119, 6, 0.12);
  color: #92400e;
}

.tool-confirm-card__args {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  word-break: break-word;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 6px 8px;
}

.tool-confirm-card__hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.tool-confirm-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
