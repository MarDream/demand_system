<template>
  <el-dialog
    v-model="internalVisible"
    title="编辑冲突"
    width="420px"
    @close="handleClose"
  >
    <p>{{ message }}</p>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleRefresh">刷新页面</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  visible: boolean
  message: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  refresh: []
  close: []
}>()

const internalVisible = ref(props.visible)

watch(
  () => props.visible,
  (value) => {
    internalVisible.value = value
  }
)

function handleClose() {
  emit('close')
}

function handleRefresh() {
  emit('refresh')
}
</script>
