<template>
  <el-card class="app-filter-card" shadow="never">
    <div class="app-filter-card__body">
      <div class="app-filter-card__form">
        <slot />
      </div>
      <div v-if="$slots.actions" class="app-filter-card__actions">
        <slot name="actions" />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.app-filter-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);

  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }
}

.app-filter-card__body {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.app-filter-card__form {
  flex: 1;
  min-width: 0;

  // 筛选表单内间距优化
  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  :deep(.el-select),
  :deep(.el-input) {
    .el-input__wrapper {
      border-radius: var(--radius-md);
    }
  }
}

.app-filter-card__actions {
  flex-shrink: 0;
  display: flex;
  gap: var(--spacing-sm);

  .el-button {
    border-radius: var(--radius-md);
  }
}

// 响应式
@media (max-width: 768px) {
  .app-filter-card__body {
    flex-direction: column;
    align-items: stretch;
  }

  .app-filter-card__actions {
    justify-content: flex-end;
  }
}
</style>
