<template>
  <div
    class="collaboration-cursor"
    :style="{ left: position.left + 'px', top: position.top + 'px' }"
  >
    <span class="cursor-dot" :style="{ backgroundColor: color }">
      {{ userName.charAt(0) }}
    </span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  userName: string
  color: string
  position: { left: number; top: number }
}

defineProps<Props>()
</script>

<style scoped>
.collaboration-cursor {
  position: absolute;
  z-index: 100;
  pointer-events: none;
}

.cursor-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: #fff;
  font-size: 10px;
  font-weight: bold;
}
</style>
