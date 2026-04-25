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
  border-radius: $card-radius;
  border-color: $border-color;
}

.app-table-card__toolbar {
  margin-bottom: $spacing-md;
}

.app-table-card__table :deep(.el-table) {
  width: 100%;
}

.app-table-card__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: $spacing-md;
}
</style>

