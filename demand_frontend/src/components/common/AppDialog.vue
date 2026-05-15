<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    @update:model-value="$emit('update:modelValue', $event)"
    @close="$emit('close')"
  >
    <template v-if="$slots.header || $slots['header-actions']" #header>
      <slot name="header">
        <div class="app-dialog__header">
          <span class="app-dialog__title">{{ title }}</span>
          <div v-if="$slots['header-actions']" class="app-dialog__header-actions">
            <slot name="header-actions" />
          </div>
        </div>
      </slot>
    </template>
    <slot />
    <template v-if="showFooter" #footer>
      <slot name="footer">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" @click="$emit('confirm')">确定</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  title: string
  width?: string
  showFooter?: boolean
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  confirm: []
}>()
</script>

<style scoped>
.app-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 28px;
}

.app-dialog__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.app-dialog__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
