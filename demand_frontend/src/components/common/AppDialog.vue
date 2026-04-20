<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    @update:model-value="$emit('update:modelValue', $event)"
    @close="$emit('close')"
  >
    <slot />
    <template #footer>
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
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  confirm: []
}>()
</script>
