<template>
  <span>
    <template v-for="(segment, index) in segments" :key="index">
      <mark v-if="segment.highlighted" class="search-highlight">{{ segment.text }}</mark>
      <span v-else>{{ segment.text }}</span>
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Segment {
  text: string
  highlighted: boolean
}

const props = defineProps<{
  content: string
  query: string
}>()

const segments = computed<Segment[]>(() => {
  if (!props.content) return []
  const trimmedQuery = props.query?.trim()
  if (!trimmedQuery) {
    return [{ text: props.content, highlighted: false }]
  }

  const terms = trimmedQuery.split(/\s+/).filter(t => t.length > 0)
  if (terms.length === 0) {
    return [{ text: props.content, highlighted: false }]
  }

  const escapedTerms = terms.map(escapeRegex)
  const pattern = new RegExp(`(${escapedTerms.join('|')})`, 'gi')

  const result: Segment[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null

  while ((match = pattern.exec(props.content)) !== null) {
    if (match.index > lastIndex) {
      result.push({ text: props.content.slice(lastIndex, match.index), highlighted: false })
    }
    result.push({ text: match[1], highlighted: true })
    lastIndex = pattern.lastIndex
  }

  if (lastIndex < props.content.length) {
    result.push({ text: props.content.slice(lastIndex), highlighted: false })
  }

  return result.length > 0 ? result : [{ text: props.content, highlighted: false }]
})

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<style scoped>
.search-highlight {
  background-color: #ffeb3b;
  padding: 0 2px;
  border-radius: 2px;
}
</style>
