<template>
  <div v-if="questions.length" class="follow-up-chips">
    <span class="follow-up-chips__label">{{ label }}</span>
    <div class="follow-up-chips__list">
      <button
        v-for="(question, index) in questions"
        :key="`${index}-${question}`"
        type="button"
        class="follow-up-chips__item"
        :disabled="disabled"
        @click="emit('select', question)"
      >
        <el-icon class="follow-up-chips__icon"><ChatDotRound /></el-icon>
        <span class="follow-up-chips__text">{{ question }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  /** 推荐追问问题列表；空列表时整块不渲染 */
  questions?: string[]
  /** 点击后是否禁用（如发送中） */
  disabled?: boolean
  label?: string
}>(), {
  questions: () => [],
  disabled: false,
  label: '你可以继续问',
})

const emit = defineEmits<{
  select: [question: string]
}>()

const questions = computed(() => props.questions.filter((item) => !!item?.trim()))
</script>

<style scoped>
.follow-up-chips {
  margin-top: 10px;
}

.follow-up-chips__label {
  display: block;
  font-size: 12px;
  color: var(--color-text-secondary, #909399);
  margin-bottom: 6px;
}

.follow-up-chips__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.follow-up-chips__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--color-text-regular, #606266);
  background: var(--color-fill-light, #f5f7fa);
  border: 1px solid var(--color-border-light, #e4e7ed);
  border-radius: 12px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background-color 0.15s;
}

.follow-up-chips__item:hover:not(:disabled) {
  color: var(--color-primary, #409eff);
  border-color: var(--color-primary-light-5, #a0cfff);
  background: var(--color-primary-light-9, #ecf5ff);
}

.follow-up-chips__item:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.follow-up-chips__icon {
  flex-shrink: 0;
  font-size: 12px;
}

.follow-up-chips__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
