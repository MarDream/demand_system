<template>
  <el-dialog
    :model-value="modelValue"
    :width="width"
    class="app-dialog-pro"
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
withDefaults(defineProps<{
  modelValue: boolean
  title: string
  width?: string
  showFooter?: boolean
}>(), {
  showFooter: true,
})

defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  confirm: []
}>()
</script>

<style scoped lang="scss">
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
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  letter-spacing: -0.01em;
}

.app-dialog__header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}
</style>

<style lang="scss">
// 非scoped：覆盖 Element Plus 对话框样式
.app-dialog-pro {
  border-radius: var(--radius-xl) !important;
  overflow: hidden;
  box-shadow: var(--shadow-dialog) !important;

  .el-dialog__header {
    padding: 20px 24px;
    margin-right: 0;
    border-bottom: 1px solid var(--color-border);
    background: linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%);
  }

  .el-dialog__body {
    padding: 24px;
  }

  .el-dialog__footer {
    padding: 12px 24px 20px;
    border-top: 1px solid var(--color-border);
    background: var(--color-surface-alt);
  }

  // 关闭按钮
  .el-dialog__headerbtn {
    top: 16px;
    right: 16px;
    width: 32px;
    height: 32px;
    border-radius: var(--radius-md);
    transition: background-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);

    .el-dialog__close {
      color: var(--color-muted-text);
      font-size: 16px;
      font-weight: 700;
    }

    &:hover {
      background-color: var(--color-danger-light);
      transform: rotate(90deg);

      .el-dialog__close {
        color: var(--color-danger);
      }
    }
  }
}
</style>
