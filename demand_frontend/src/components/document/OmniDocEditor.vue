<template>
  <div></div>
</template>

<script setup lang="ts">
const props = defineProps<{
  modelValue: boolean
  knowledgeBaseId: number
  documentId: number
  documentName: string
  mode?: 'edit' | 'view'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'saved': []
  'error': [error: string]
}>()
</script>
