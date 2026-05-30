<template>
  <div class="comment-section-block">
    <div class="section-header">
      <h3>评论</h3>
    </div>
    <div class="comment-editor-wrapper">
      <IsleEditorToolbar v-if="editorInstance" :editor="editorInstance" />
      <IsleEditor v-model="localCommentRichText" :extensions="editorExtensions" locale="zh" @create="onEditorCreate" />
    </div>
    <div class="comment-editor-actions">
      <AppButton type="primary" permission="button:requirement:comment" :loading="submitting" @click="handleSubmitComment">
        提交评论
      </AppButton>
    </div>
    <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="40" />
    <div v-for="comment in comments" :key="comment.id" class="comment-item">
      <el-avatar :size="32">{{ comment.userName?.charAt(0) || 'U' }}</el-avatar>
      <div class="comment-content">
        <div class="comment-header">
          <strong>{{ comment.userName || '用户' }}</strong>
          <span class="comment-time">{{ formatDate(comment.createdAt) }}</span>
        </div>
        <div class="rich-content comment-body" v-html="hydrateRichTextImageHtml(comment.content || '')"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { IsleEditor, IsleEditorToolbar, RichTextKit } from '@isle-editor/vue3'
import Image from '@tiptap/extension-image'
import { addLocale } from '@isle-editor/core'
import type { RequirementComment } from '@/types/requirement'
import { uploadRequirementAttachment } from '@/api/modules/file'
import { buildRichTextImagePreviewUrl } from '@/utils/richTextFileImage'
import { formatDate } from '@/utils/format'
import { hydrateRichTextImageHtml } from '@/utils/richTextFileImage'

addLocale('zh', {
  isleEditor: '岛屿编辑器',
  placeholder: '写点什么 ...',
})

const props = defineProps<{
  comments: RequirementComment[]
  submitting: boolean
}>()

const emit = defineEmits<{
  'update:commentRichText': [value: string]
  submit: []
}>()

const editorInstance = ref<any>(null)
const commentRichText = ref('')
const localCommentRichText = computed({
  get: () => commentRichText.value,
  set: (val) => {
    commentRichText.value = val
    emit('update:commentRichText', val)
  },
})

const editorExtensions = [
  RichTextKit.configure({
    placeholder: { placeholder: '输入评论内容...' },
  }),
  Image.configure({
    inline: false,
    allowBase64: true,
    HTMLAttributes: { class: 'comment-editor-image' },
  }),
]

function onEditorCreate({ editor }: { editor: any }) {
  editorInstance.value = editor
  const editorEl = editor.view.dom as HTMLElement
  editorEl.addEventListener('paste', handleCommentImagePaste as unknown as EventListener)
}

const commentImageUploading = ref(false)

async function handleCommentImagePaste(event: ClipboardEvent) {
  const files = event.clipboardData?.files
  if (!files || files.length === 0) return

  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    event.preventDefault()

    let processedFile = file
    if (!file.name || file.name === 'image' || !file.name.includes('.')) {
      const ext = file.type.split('/')[1] || 'png'
      processedFile = new File([file], 'clipboard_' + Date.now() + '.' + ext, { type: file.type })
    }

    try {
      commentImageUploading.value = true
      ElMessage.info('上传图片中: ' + processedFile.name)
      const attachment = await uploadRequirementAttachment(processedFile)
      const src = attachment.fileId ? buildRichTextImagePreviewUrl(attachment.fileId) : attachment.url
      if (src && editorInstance.value) {
        editorInstance.value.chain().focus().setImage({ src, alt: processedFile.name }).run()
        ElMessage.success('图片 ' + processedFile.name + ' 已插入')
      }
    } catch {
      ElMessage.error('图片 ' + processedFile.name + ' 插入失败')
    } finally {
      commentImageUploading.value = false
    }
  }
}

function handleSubmitComment() {
  emit('submit')
}

function clearEditorContent() {
  editorInstance.value?.commands?.clearContent?.()
}

defineExpose({
  clearEditorContent,
})
</script>

<script lang="ts">
import { computed } from 'vue'
export default {
  computed: {
    commentRichText: {
      get() { return commentRichText.value },
      set(val: string) { commentRichText.value = val }
    }
  }
}
</script>

<style scoped>
.comment-section-block {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

.comment-editor-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 12px;
  min-height: 160px;
}

.comment-editor-wrapper :deep(.comment-editor-image) {
  display: block;
  max-width: 100%;
  border-radius: 6px;
}

.comment-editor-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #ebeef5;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.comment-body :deep(img) {
  max-width: 100%;
  height: auto;
}

.comment-body :deep(p) {
  margin: 0 0 4px 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
</style>
