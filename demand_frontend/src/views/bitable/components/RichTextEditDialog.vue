<template>
  <el-dialog
    :model-value="visible"
    :title="`编辑${fieldName || '富文本'}`"
    width="760px"
    append-to-body
    destroy-on-close
    @close="handleCancel"
  >
    <RichTextEditor
      :key="editorKey"
      v-model="draft"
      :placeholder="placeholder"
      min-height="320px"
    />
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import RichTextEditor from '@/components/rich/RichTextEditor.vue'

/**
 * 富文本单元格编辑弹窗（网格单元格点击 / 记录详情入口共用）。
 * 网格行高有限放不下工具栏，点击单元格后在此全功能编辑，保存走 cellChange 通道。
 */
const props = defineProps<{
  visible: boolean
  fieldName?: string
  initialValue?: string
  placeholder?: string
}>()

const emit = defineEmits<{
  cancel: []
  save: [html: string]
}>()

const draft = ref('')
/** 每次打开重挂载编辑器：IsleEditor 只在创建时读一次初始值 */
const editorKey = computed(() => `${props.visible}-${props.initialValue ?? ''}`)

function handleCancel() {
  emit('cancel')
}

function handleSave() {
  emit('save', draft.value)
}

watch(
  () => props.visible,
  (v) => {
    if (v) draft.value = props.initialValue || ''
  },
  { immediate: true },
)
</script>
