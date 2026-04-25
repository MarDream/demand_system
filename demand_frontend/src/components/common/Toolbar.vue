<template>
  <div class="app-toolbar" :class="{ 'app-toolbar--wrap': wrap }">
    <div class="app-toolbar__left">
      <slot name="left" />
    </div>
    <div class="app-toolbar__right">
      <slot name="right" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  wrap?: boolean
}>(), {
  wrap: true,
})
</script>

<style scoped lang="scss">
.app-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
}

.app-toolbar--wrap {
  flex-wrap: wrap;
  align-items: flex-start;
}

.app-toolbar__left,
.app-toolbar__right {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.app-toolbar__right {
  justify-content: flex-end;
}
</style>

