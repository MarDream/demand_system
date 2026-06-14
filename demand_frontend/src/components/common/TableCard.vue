<template>
  <el-card class="app-table-card" shadow="never">
    <div v-if="$slots.toolbar" class="app-table-card__toolbar">
      <slot name="toolbar" />
    </div>
    <div class="app-table-card__table">
      <slot name="table" />
    </div>
    <div v-if="$slots.pagination" class="app-table-card__pagination">
      <slot name="pagination" />
    </div>
  </el-card>
</template>

<script setup lang="ts">
</script>

<style scoped lang="scss">
.app-table-card {
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);

  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }
}

.app-table-card__toolbar {
  margin-bottom: var(--spacing-md);
}

.app-table-card__table :deep(.el-table) {
  width: 100%;
  border-radius: var(--radius-md);

  // 表头优化
  .el-table__header th {
    font-weight: var(--font-weight-semibold);
    font-size: var(--font-size-sm);
    color: var(--color-text-secondary);
    background: var(--color-surface-alt);
  }

  // 斑马纹
  .el-table__row:nth-child(even) td.el-table__cell {
    background: rgba(248, 250, 252, 0.5);
  }

  // 行 Hover
  .el-table__row:hover > td.el-table__cell {
    background: rgba(3, 105, 161, 0.04) !important;
  }

  // 行高优化
  .el-table__row td.el-table__cell {
    padding: 12px 0;
  }

  // 表格内无外边框
  &::before,
  .el-table__inner-wrapper::before {
    display: none;
  }

  // 表格底部边框
  .el-table__border-bottom-patch {
    border-bottom: 1px solid var(--color-border);
  }
}

.app-table-card__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);

  :deep(.el-pagination) {
    .el-pager li {
      border-radius: var(--radius-md);
      min-width: 32px;
      height: 32px;
      font-weight: var(--font-weight-medium);
    }

    .btn-prev,
    .btn-next {
      border-radius: var(--radius-md);
      min-width: 32px;
      height: 32px;
    }
  }
}

// 响应式：移动端表格横向滚动
@media (max-width: 768px) {
  .app-table-card__table {
    overflow-x: auto;
  }
}
</style>
