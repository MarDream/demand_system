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
  border-radius: $card-radius;
  border-color: $border-color;
}

.app-filter-card__body {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.app-filter-card__form {
  flex: 1;
  min-width: 0;
}

.app-filter-card__actions {
  flex-shrink: 0;
  display: flex;
  gap: $spacing-sm;
}
</style>

