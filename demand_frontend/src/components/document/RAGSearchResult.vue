<template>
  <div class="search-results">
    <!-- LLM 思维链展示 -->
    <div v-if="thinkingChain.length > 0" class="thinking-chain">
      <div class="chain-header">
        <el-icon><Cpu /></el-icon>
        <span>检索思维链</span>
        <el-button link size="small" @click="showChain = !showChain">
          {{ showChain ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-collapse-transition>
        <div v-show="showChain" class="chain-steps">
          <div v-for="(step, idx) in thinkingChain" :key="idx" class="chain-step">
            <div class="step-indicator">
              <span class="step-number">{{ idx + 1 }}</span>
              <span v-if="idx < thinkingChain.length - 1" class="step-line" />
            </div>
            <div class="step-content">
              <div class="step-label">{{ step.label }}</div>
              <div class="step-detail">{{ step.detail }}</div>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </div>

    <!-- LLM 回答 -->
    <div v-if="results?.answer" class="llm-answer">
      <div class="answer-header">
        <el-icon><ChatDotRound /></el-icon>
        <span>智能回答</span>
      </div>
      <div class="answer-content">{{ results.answer }}</div>
    </div>

    <!-- 文档检索结果 -->
    <div v-if="results?.documents?.length">
      <p class="result-count">找到 {{ results.documents.length }} 条相关文档</p>
      <el-card v-for="doc in results.documents" :key="doc.documentId" class="result-card" shadow="hover">
        <template #header>
          <div class="doc-header">
            <span class="doc-name">
              <el-icon><Document /></el-icon>
              {{ doc.fileName }}
            </span>
            <el-tag :type="scoreType(doc.avgScore)">{{ Math.round(doc.avgScore * 100) }}% 相关</el-tag>
          </div>
        </template>
        <div v-for="chunk in doc.chunks" :key="chunk.chunkId" class="chunk-item">
          <div v-if="chunk.sectionTitle" class="chunk-title">{{ chunk.sectionTitle }}</div>
          <div class="chunk-text" v-html="highlightText(chunk.matchedText)"></div>
        </div>
      </el-card>
    </div>
    <div v-else-if="results && !results.documents?.length && !results?.answer">
      <el-empty description="未找到相关文档" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Document, Cpu, ChatDotRound } from '@element-plus/icons-vue'
import type { DocumentSearchResult } from '@/api/modules/document'

interface ThinkingStep {
  label: string
  detail: string
}

const props = defineProps<{
  results: DocumentSearchResult | null
  query?: string
}>()

const showChain = ref(true)

const thinkingChain = ref<ThinkingStep[]>([])

function setThinkingChain(steps: ThinkingStep[]) {
  thinkingChain.value = steps
  showChain.value = true
}

function buildDefaultChain(query: string | undefined, docCount: number) {
  if (!query) return
  thinkingChain.value = [
    { label: '查询解析', detail: `解析用户查询: "${query}"，提取关键语义向量` },
    { label: '向量化', detail: '将查询文本转换为高维向量表示，用于语义匹配' },
    { label: '知识库检索', detail: `在知识库中进行混合检索（向量 + 关键词），命中 ${docCount} 个相关文档片段` },
    { label: '结果排序', detail: '基于语义相似度和关键词匹配度对结果进行综合排序' },
    { label: '生成回答', detail: docCount > 0 ? '基于检索到的文档内容，生成结构化回答' : '未检索到相关内容，无法生成回答' },
  ]
}

// 当 results 变化时自动构建思维链
import { watch } from 'vue'
watch(() => props.results, (val) => {
  if (val) {
    const docCount = val.documents?.length ?? 0
    buildDefaultChain(props.query, docCount)
  }
}, { immediate: true })

defineExpose({ setThinkingChain })

function scoreType(score: number) {
  if (score >= 0.8) return 'success'
  if (score >= 0.5) return 'warning'
  return 'info'
}

function highlightText(text: string) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
</script>

<style scoped>
.thinking-chain {
  margin-bottom: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}
.chain-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
}
.chain-header .el-button { color: rgba(255,255,255,0.8); }
.chain-steps {
  padding: 16px;
  background: #fafbfc;
}
.chain-step {
  display: flex;
  gap: 12px;
  margin-bottom: 0;
}
.step-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 28px;
}
.step-number {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #409EFF;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.step-line {
  width: 2px;
  height: 32px;
  background: #dcdfe6;
}
.step-content { flex: 1; padding-bottom: 12px; }
.step-label { font-weight: 600; font-size: 14px; color: #303133; margin-bottom: 2px; }
.step-detail { font-size: 13px; color: #606266; line-height: 1.6; }
.llm-answer {
  margin-bottom: 20px;
  padding: 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}
.answer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #409EFF;
}
.answer-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}
.result-count { margin: 16px 0; color: #606266; font-size: 14px; }
.result-card { margin-bottom: 16px; }
.doc-header { display: flex; justify-content: space-between; align-items: center; }
.doc-name { display: flex; align-items: center; gap: 4px; font-weight: 600; }
.chunk-item {
  margin: 12px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #409EFF;
}
.chunk-title { font-size: 12px; color: #909399; margin-bottom: 6px; font-weight: 500; }
.chunk-text { font-size: 13px; color: #606266; line-height: 1.8; }
</style>
