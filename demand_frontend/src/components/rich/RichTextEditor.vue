<template>
  <div class="rich-text-editor" :class="{ 'is-readonly': readonly }">
    <IsleEditorToolbar v-if="editorInstance && !readonly" class="rich-text-editor__toolbar" :editor="editorInstance" />
    <IsleEditor
      :key="editorKey"
      :model-value="modelValue"
      :extensions="extensions"
      :editable="!readonly"
      locale="zh"
      class="rich-text-editor__surface"
      :style="{ minHeight }"
      @create="onEditorCreate"
      @update:model-value="onUpdate"
    />
  </div>
</template>

<script setup lang="ts">
import { shallowRef } from 'vue'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import '@isle-editor/vue3/dist/style.css'

/**
 * 多维表格富文本编辑器（ IsleEditor 封装）。
 *
 * IsleEditor 只在挂载时读取一次 modelValue，之后仅对外 emit，
 * 所以外部的值回填（如弹窗切到另一条记录）必须通过 editorKey 变化触发重挂载。
 */
const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    readonly?: boolean
    minHeight?: string
    /** 变化该值会强制重建编辑器（用于外部换值回填） */
    editorKey?: string | number
  }>(),
  {
    placeholder: '',
    readonly: false,
    minHeight: '160px',
    editorKey: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const editorInstance = shallowRef<any>(null)

const extensions = [
  RichTextKit.configure({
    placeholder: props.placeholder ? { placeholder: props.placeholder } : {},
  }),
]

function onEditorCreate({ editor }: { editor: any }) {
  editorInstance.value = editor
}

// IsleEditor 的 update:modelValue 直接回传 HTML 字符串（dist 源码 emit("update:modelValue", output)），
// 不是 tiptap 的 { editor } 事件对象；空文档输出 <p></p>，这里规整为空串
function onUpdate(output: string) {
  const html = output || ''
  const nowEmpty = editorInstance.value?.getText?.().trim() === ''
  emit('update:modelValue', nowEmpty ? '' : html)
}
</script>

<style scoped lang="scss">
.rich-text-editor {
  width: 100%;
  border: 1px solid var(--color-border, #dcdfe6);
  border-radius: 6px;
  overflow: hidden;
  background: var(--color-bg-container, #fff);
  transition: border-color 0.2s;

  &:focus-within {
    border-color: var(--color-primary, #3370ff);
  }

  &.is-readonly {
    border-color: transparent;
    background: transparent;
  }

  &__toolbar {
    border-bottom: 1px solid var(--color-border-light, #ebeef5);
    background: var(--color-bg-container, #fff);
  }

  &__surface {
    padding: 8px 12px;
  }

  :deep(.isle-editor) {
    outline: none;
  }

  :deep(p) {
    margin: 0 0 4px;
    line-height: 1.7;
  }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 4px;
    padding-left: 22px;
  }
}
</style>
