<template>
  <div class="smart-search">
    <el-input v-model="query" placeholder="输入您想了解的内容..." size="large" clearable @keyup.enter="handleSearch">
      <template #prefix><el-icon><Search /></el-icon></template>
      <template #append><el-button type="primary" @click="handleSearch">检索</el-button></template>
    </el-input>
    <div class="search-options">
      <el-select v-model="selectedKbId" placeholder="全部知识库" clearable size="small" class="kb-selector">
        <el-option label="全部知识库" value="" />
        <el-option v-for="kb in knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-radio-group v-model="mode" size="small">
        <el-radio-button value="hybrid">混合模式</el-radio-button>
        <el-radio-button value="semantic">语义检索</el-radio-button>
        <el-radio-button value="keyword">关键词搜索</el-radio-button>
      </el-radio-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getAllKnowledgeBases, type KnowledgeBase } from '@/api/modules/knowledge'

const query = ref('')
const mode = ref('hybrid')
const selectedKbId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])

const emit = defineEmits<{
  search: [query: string, mode: string, knowledgeBaseId?: number]
}>()

onMounted(async () => {
  try {
    const res = await getAllKnowledgeBases()
    knowledgeBases.value = (res as any)?.data || res || []
  } catch {}
})

const handleSearch = () => {
  if (!query.value.trim()) return
  emit('search', query.value, mode.value, selectedKbId.value ?? undefined)
}
</script>

<style scoped>
.smart-search { width: 100%; }
.search-options {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
  flex-wrap: wrap;
}
.kb-selector { width: 200px; }
</style>
