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
        <div class="results-toolbar">
          <div class="results-toolbar__left">
            <el-checkbox
              v-model="selectAll"
              :indeterminate="isIndeterminate"
              @change="handleSelectAll"
            >
              全选
            </el-checkbox>
            <span class="selected-count">已选择 {{ selectedDocIds.size }} 项</span>
          </div>
          <div class="results-toolbar__right">
            <el-button
              v-if="selectedDocIds.size > 0"
              type="primary"
              size="small"
              @click="handleBatchDownload"
              :loading="downloading"
            >
              {{ selectedDocIds.size >= 2 ? '打包下载' : '下载' }} ({{ selectedDocIds.size }})
            </el-button>
          </div>
        </div>

        <p class="result-count">找到 {{ store.searchResults.total }} 条相关结果</p>

        <el-alert
          v-if="store.searchResults.processSummary"
          type="info"
          :closable="false"
          show-icon
          class="process-summary"
          :title="store.searchResults.processSummary"
        />

        <el-card v-if="store.searchResults.answer" class="answer-card" shadow="never">
          <template #header>检索结果摘要</template>
          <div class="answer-content">{{ store.searchResults.answer }}</div>
        </el-card>

        <el-card v-for="(item, index) in store.searchResults.results" :key="item.chunkId" class="result-card" shadow="hover">
          <div class="result-checkbox">
            <el-checkbox
              :model-value="selectedDocIds.has(item.documentId)"
              @change="(val: boolean) => handleSelectChange(item.documentId, val)"
            />
          </div>
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
              <HighlightText :content="item.content" :query="query" />
            </div>
            <div v-if="item.requirement" class="requirement-ref">
              <div class="requirement-ref__title">关联需求</div>
              <div class="requirement-ref__meta">
                <span>#{{ item.requirement.id }}</span>
                <span>{{ item.requirement.title }}</span>
                <el-tag size="small">{{ item.requirement.status }}</el-tag>
                <el-tag size="small" type="info">{{ item.requirement.type }}</el-tag>
              </div>
              <div v-if="item.requirement.summary" class="requirement-ref__summary">
                {{ item.requirement.summary }}
              </div>
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
import { onMounted, ref, computed } from 'vue'
import { Search, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import HighlightText from '@/components/common/HighlightText.vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import type { SearchMode } from '@/api/modules/knowledge'
import { downloadDocumentBlob, batchDownloadDocumentsZip } from '@/api/modules/knowledge'

const store = useKnowledgeStore()
const query = ref('')
const mode = ref<SearchMode>('hybrid')
const selectedKbId = ref<number | ''>('')
const topK = ref(20)
const searched = ref(false)
const selectedDocIds = ref<Set<number>>(new Set())
const downloading = ref(false)

const allDocIds = computed(() => {
  if (!store.searchResults?.results) return []
  return [...new Set(store.searchResults.results.map((item) => item.documentId))]
})

const selectAll = computed({
  get: () => allDocIds.value.length > 0 && selectedDocIds.value.size === allDocIds.value.length,
  set: (val: boolean) => {
    if (val) {
      selectedDocIds.value = new Set(allDocIds.value)
    } else {
      selectedDocIds.value.clear()
    }
  },
})

const isIndeterminate = computed(() => {
  return selectedDocIds.value.size > 0 && selectedDocIds.value.size < allDocIds.value.length
})

onMounted(() => {
  store.fetchAllBases()
})

function handleSearch() {
  selectedDocIds.value.clear()
  if (!query.value.trim()) return
  searched.value = true
  store.search(query.value, mode.value, selectedKbId.value === '' ? undefined : selectedKbId.value)
}

function handleSelectAll(val: boolean) {
  if (val) {
    selectedDocIds.value = new Set(allDocIds.value)
  } else {
    selectedDocIds.value.clear()
  }
}

function handleSelectChange(docId: number, selected: boolean) {
  if (selected) {
    selectedDocIds.value.add(docId)
  } else {
    selectedDocIds.value.delete(docId)
  }
}

async function handleBatchDownload() {
  if (selectedDocIds.value.size === 0) {
    ElMessage.warning('请先选择要下载的文档')
    return
  }

  const docIds = [...selectedDocIds.value]
  downloading.value = true

  try {
    let blob: Blob
    if (docIds.length === 1) {
      const docId = docIds[0]
      const kbId = store.searchResults?.results.find((r) => r.documentId === docId)?.knowledgeBaseId
      if (!kbId) throw new Error('未找到文档所属知识库')
      blob = await downloadDocumentBlob(Number(kbId), docId)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const item = store.searchResults?.results.find((r) => r.documentId === docId)
      a.download = item?.fileName || 'document'
      a.click()
      URL.revokeObjectURL(url)
    } else {
      const kbId = store.searchResults?.results[0]?.knowledgeBaseId
      if (!kbId) throw new Error('未找到文档所属知识库')
      blob = await batchDownloadDocumentsZip(Number(kbId), docIds)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'download.zip'
      a.click()
      URL.revokeObjectURL(url)
    }
    ElMessage.success('下载成功')
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '下载失败'
    ElMessage.error(msg)
  } finally {
    downloading.value = false
  }
}

function scoreType(score: number) {
  if (score >= 0.8) return 'success'
  if (score >= 0.5) return 'warning'
  return 'info'
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
.results-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}
.results-toolbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.selected-count {
  font-size: 13px;
  color: #606266;
}
.results-toolbar__right {
  display: flex;
  gap: 8px;
}
.process-summary {
  margin-bottom: 16px;
}
.answer-card {
  margin-bottom: 16px;
}
.answer-content {
  line-height: 1.8;
  color: #606266;
}
.result-card {
  margin-bottom: 16px;
  position: relative;
}
.result-card :deep(.el-card__body) {
  display: flex;
  gap: 16px;
}
.result-checkbox {
  display: flex;
  align-items: flex-start;
  padding-top: 2px;
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
.requirement-ref {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f0f9eb;
}
.requirement-ref__title {
  font-size: 12px;
  color: #67c23a;
  font-weight: 600;
  margin-bottom: 6px;
}
.requirement-ref__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
  color: #303133;
}
.requirement-ref__summary {
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
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
