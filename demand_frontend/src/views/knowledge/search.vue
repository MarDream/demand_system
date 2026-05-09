<template>
  <PageContainer title="全局语义检索">
    <div class="search-page">
      <div class="search-header">
        <el-input
          v-model="query"
          placeholder="输入您想了解的内容..."
          size="large"
          clearable
          @keyup.enter="handleSearch"
          class="search-input"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
          <template #append>
            <el-button type="primary" @click="handleSearch" :loading="store.loading">检索</el-button>
          </template>
        </el-input>

        <div class="search-options">
          <el-select v-model="selectedKbId" placeholder="全部知识库" clearable class="kb-selector">
            <el-option label="全部知识库" value="" />
            <el-option v-for="kb in store.knowledgeBases" :key="kb.id" :label="kb.name" :value="kb.id" />
          </el-select>

          <el-radio-group v-model="mode" size="small">
            <el-radio-button value="hybrid">混合模式</el-radio-button>
            <el-radio-button value="semantic">语义检索</el-radio-button>
            <el-radio-button value="keyword">关键词搜索</el-radio-button>
          </el-radio-group>

          <el-select v-model="topK" size="small" style="width: 120px">
            <el-option :value="10" label="Top 10" />
            <el-option :value="20" label="Top 20" />
            <el-option :value="50" label="Top 50" />
          </el-select>
        </div>
      </div>

      <div v-if="store.searchResults?.results?.length" class="search-results">
        <p class="result-count">找到 {{ store.searchResults.total }} 条相关结果</p>

        <el-card v-for="(item, index) in store.searchResults.results" :key="item.chunkId" class="result-card" shadow="hover">
          <div class="result-rank">{{ index + 1 }}</div>
          <div class="result-body">
            <div class="result-meta">
              <span class="result-file">
                <el-icon><Document /></el-icon>
                {{ item.fileName }}
              </span>
              <el-tag size="small" :type="scoreType(item.score)">{{ (item.score * 100).toFixed(1) }}%</el-tag>
              <span v-if="item.knowledgeBaseId" class="result-kb">知识库 #{{ item.knowledgeBaseId }}</span>
              <span v-if="item.pageNum" class="result-page">第 {{ item.pageNum }} 页</span>
            </div>
            <div v-if="item.sectionTitle" class="result-title">{{ item.sectionTitle }}</div>
            <div class="result-content">
              <span v-html="highlightContent(item.content)"></span>
            </div>
            <div class="result-footer">
              <span class="result-doc-id">文档ID: {{ item.documentId }}</span>
              <span class="result-chunk-id">分块ID: {{ item.chunkId }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <el-empty v-else-if="searched && !store.loading" description="未找到相关结果，请尝试其他关键词" />

      <div v-if="!searched && !store.loading" class="search-tip">
        <el-icon :size="48" color="#C0C4CC"><Search /></el-icon>
        <p>输入关键词开始语义检索</p>
        <p class="tip-desc">支持混合检索、语义检索、关键词搜索三种模式</p>
      </div>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Search, Document } from '@element-plus/icons-vue'
import PageContainer from '@/components/common/PageContainer.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import type { SearchMode } from '@/api/modules/knowledge'

const store = useKnowledgeStore()
const query = ref('')
const mode = ref<SearchMode>('hybrid')
const selectedKbId = ref<number | ''>('')
const topK = ref(20)
const searched = ref(false)

onMounted(() => {
  store.fetchAllBases()
})

function handleSearch() {
  if (!query.value.trim()) return
  searched.value = true
  store.search(query.value, mode.value, selectedKbId.value === '' ? undefined : selectedKbId.value)
}

function scoreType(score: number) {
  if (score >= 0.8) return 'success'
  if (score >= 0.5) return 'warning'
  return 'info'
}

function highlightContent(content: string) {
  if (!query.value.trim()) return escapeHtml(content)
  const terms = query.value.trim().split(/\s+/)
  let result = escapeHtml(content)
  for (const term of terms) {
    const regex = new RegExp(`(${escapeRegex(term)})`, 'gi')
    result = result.replace(regex, '<mark>$1</mark>')
  }
  return result
}

function escapeHtml(text: string) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeRegex(str: string) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
</script>

<style scoped>
.search-page {
  max-width: 960px;
  margin: 0 auto;
}
.search-header {
  margin-bottom: 24px;
}
.search-input {
  margin-bottom: 12px;
}
.search-options {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.kb-selector {
  width: 200px;
}
.result-count {
  color: #909399;
  font-size: 13px;
  margin-bottom: 16px;
}
.result-card {
  margin-bottom: 16px;
  position: relative;
}
.result-card :deep(.el-card__body) {
  display: flex;
  gap: 16px;
}
.result-rank {
  font-size: 24px;
  font-weight: 700;
  color: #409EFF;
  min-width: 36px;
  text-align: center;
  padding-top: 4px;
}
.result-body {
  flex: 1;
  min-width: 0;
}
.result-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.result-file {
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.result-kb, .result-page {
  font-size: 12px;
  color: #909399;
}
.result-title {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  margin-bottom: 6px;
}
.result-content {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  max-height: 160px;
  overflow: hidden;
}
.result-content :deep(mark) {
  background: #fef08a;
  padding: 0 2px;
  border-radius: 2px;
}
.result-footer {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 11px;
  color: #c0c4cc;
}
.search-tip {
  text-align: center;
  padding: 80px 0;
  color: #909399;
}
.search-tip p {
  margin-top: 16px;
  font-size: 16px;
}
.tip-desc {
  font-size: 13px !important;
  color: #c0c4cc;
}
</style>
