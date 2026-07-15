<template>
  <div
    ref="containerRef"
    class="markdown-content"
    v-html="renderedHtml"
    @click="handleContentClick"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { renderMarkdown, replaceCitationLinks } from '@/utils/markdownRender'

interface RagCitationLike {
  documentId?: number
  fileName?: string
  [key: string]: any
}

const props = defineProps<{
  content: string
  citations?: RagCitationLike[]
}>()

const emit = defineEmits<{
  (e: 'citation-click', index: number): void
}>()

const containerRef = ref<HTMLElement>()

const renderedHtml = computed(() => {
  if (!props.content) return ''
  const html = renderMarkdown(props.content)
  return replaceCitationLinks(html, props.citations)
})

function handleContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.classList.contains('citation-ref') && !target.classList.contains('citation-ref--invalid')) {
    const idx = target.dataset.citationIndex
    if (idx !== undefined) {
      e.preventDefault()
      e.stopPropagation()
      emit('citation-click', parseInt(idx, 10))
    }
  }
}
</script>

<style scoped>
.markdown-content {
  line-height: 1.75;
  color: var(--color-text-primary);
  overflow-wrap: break-word;
  margin-top: 6px;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.4;
}
.markdown-content :deep(h1) { font-size: 1.4em; }
.markdown-content :deep(h2) { font-size: 1.25em; }
.markdown-content :deep(h3) { font-size: 1.1em; }

.markdown-content :deep(p) {
  margin-top: 0.5em;
  margin-bottom: 0.5em;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.5em;
  margin-top: 0.5em;
  margin-bottom: 0.5em;
}

.markdown-content :deep(li) {
  margin-bottom: 0.25em;
}

.markdown-content :deep(code) {
  background: var(--color-fill-secondary, #f3f4f6);
  padding: 0.15em 0.4em;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'SFMono-Regular', Consolas, monospace;
}

.markdown-content :deep(pre) {
  background: var(--color-fill-secondary, #f3f4f6);
  padding: 1em;
  border-radius: 6px;
  overflow-x: auto;
  margin-top: 0.75em;
  margin-bottom: 0.75em;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-content :deep(blockquote) {
  border-left: 3px solid var(--color-primary, #409eff);
  margin-left: 0;
  padding-left: 1em;
  color: var(--color-text-secondary, #666);
  margin-top: 0.75em;
  margin-bottom: 0.75em;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-top: 0.75em;
  margin-bottom: 0.75em;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid var(--color-border-light, #e5e7eb);
  padding: 0.5em 0.75em;
  text-align: left;
}

.markdown-content :deep(th) {
  background: var(--color-fill-secondary, #f3f4f6);
  font-weight: 600;
}

.markdown-content :deep(strong) {
  font-weight: 600;
}

.markdown-content :deep(a) {
  color: var(--color-primary, #409eff);
  text-decoration: none;
}
.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(.citation-ref) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.4em;
  height: 1.4em;
  font-size: 0.75em;
  font-weight: 600;
  color: var(--color-primary, #409eff);
  background: var(--color-primary-light-9, #ecf5ff);
  border-radius: 3px;
  cursor: pointer;
  vertical-align: super;
  margin: 0 0.1em;
  padding: 0 0.2em;
  transition: background-color 0.15s, color 0.15s;
  user-select: none;
}

.markdown-content :deep(.citation-ref:hover) {
  background: var(--color-primary-light-7, #b3d8ff);
  color: var(--color-primary-dark-2, #337ecc);
}

.markdown-content :deep(.citation-ref--invalid) {
  color: var(--color-text-placeholder, #c0c4cc);
  background: var(--color-fill-light, #f5f7fa);
  cursor: default;
}
</style>