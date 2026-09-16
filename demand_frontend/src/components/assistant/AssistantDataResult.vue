<template>
  <div class="assistant-data-result">
    <!-- 头部：标题 + 元信息 -->
    <div class="assistant-data-result__header">
      <div class="assistant-data-result__title">
        <el-icon class="assistant-data-result__title-icon"><DataAnalysis /></el-icon>
        <span>{{ title }}</span>
      </div>
      <div class="assistant-data-result__meta">
        <el-tag size="small" effect="plain" type="success">{{ result.rowCount }} 行</el-tag>
        <el-tag v-if="result.durationMs != null" size="small" effect="plain" type="info">{{ result.durationMs }} ms</el-tag>
        <el-tag v-if="result.scopeApplied" size="small" effect="plain" type="warning">已按数据权限过滤</el-tag>
        <el-tag v-if="result.truncated" size="small" effect="plain" type="warning">结果已截断</el-tag>
      </div>
    </div>

    <!-- 图表 -->
    <div v-if="chartOption" class="assistant-data-result__chart">
      <VChart :option="chartOption" autoresize class="assistant-data-result__chart-canvas" />
    </div>

    <!-- 结果表格 -->
    <div v-if="result.rows.length" class="assistant-data-result__table">
      <el-table
        :data="result.rows"
        size="small"
        border
        stripe
        max-height="320"
        class="assistant-data-result__table-inner"
      >
        <el-table-column
          v-for="column in result.columns"
          :key="column.field"
          :prop="column.field"
          :label="column.label || column.field"
          :min-width="columnWidth(column)"
          :align="column.type === 'number' ? 'right' : 'left'"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatCell(column, row) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div v-else class="assistant-data-result__empty">
      <el-icon><WarningFilled /></el-icon>
      <span>没有查询到符合条件的数据</span>
    </div>

    <!-- 底部：数据来源 + 操作 -->
    <div class="assistant-data-result__footer">
      <div class="assistant-data-result__tables">
        <span class="assistant-data-result__label">数据来源</span>
        <span
          v-for="table in result.sourceTables || []"
          :key="table"
          class="assistant-data-result__table-chip"
        >{{ table }}</span>
        <span v-if="!result.sourceTables?.length" class="assistant-data-result__label">—</span>
      </div>
      <div class="assistant-data-result__actions">
        <el-button text size="small" @click="sqlVisible = !sqlVisible">
          {{ sqlVisible ? '收起 SQL' : '查看 SQL' }}
        </el-button>
        <el-button text size="small" :disabled="!result.rows.length" @click="exportCsv">导出 CSV</el-button>
      </div>
    </div>

    <!-- 执行的 SQL（透明可审计） -->
    <div v-if="sqlVisible" class="assistant-data-result__sql">
      <pre class="assistant-data-result__sql-code">{{ result.sql }}</pre>
      <el-button text size="small" class="assistant-data-result__sql-copy" @click="copySql">复制</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { DataAnalysis, WarningFilled } from '@element-plus/icons-vue'
import type { AssistantDataColumn, AssistantDataResult } from '@/types/assistant'

const props = defineProps<{ result: AssistantDataResult }>()

const sqlVisible = ref(false)

const title = computed(() => props.result.chart?.title || '数据查询结果')

const chartOption = computed<Record<string, any> | null>(() => {
  const chart = props.result.chart
  if (!chart || chart.type === 'table') return null
  if (!chart.categoryField || !chart.valueField) return null
  const rows = props.result.rows || []
  if (!rows.length) return null

  const categoryField = chart.categoryField
  const valueField = chart.valueField
  const seriesField = chart.seriesField || null

  const toNumber = (value: unknown): number => {
    if (typeof value === 'number') return value
    if (value == null) return 0
    const parsed = Number(String(value).replace(/[,%\s]/g, ''))
    return Number.isFinite(parsed) ? parsed : 0
  }

  if (chart.type === 'pie') {
    return {
      tooltip: { trigger: 'item' },
      legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 11 } },
      series: [
        {
          type: 'pie',
          radius: ['38%', '66%'],
          center: ['50%', '46%'],
          data: rows.slice(0, 30).map((row) => ({
            name: String(row[categoryField] ?? '-'),
            value: toNumber(row[valueField]),
          })),
          label: { fontSize: 11 },
        },
      ],
    }
  }

  const categories = Array.from(new Set(rows.map((row) => String(row[categoryField] ?? '-'))))
  const base = {
    tooltip: { trigger: 'axis' },
    legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 11 } },
    grid: { left: 8, right: 16, top: 24, bottom: 36, containLabel: true },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: { fontSize: 11, interval: 0, rotate: categories.length > 6 ? 30 : 0 },
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
  }

  if (seriesField) {
    const seriesNames = Array.from(new Set(rows.map((row) => String(row[seriesField] ?? '-'))))
    const series = seriesNames.map((name) => ({
      name,
      type: chart.type === 'line' ? 'line' : 'bar',
      smooth: chart.type === 'line',
      data: categories.map((category) => {
        const matched = rows.find(
          (row) => String(row[categoryField] ?? '-') === category && String(row[seriesField] ?? '-') === name,
        )
        return matched ? toNumber(matched[valueField]) : 0
      }),
    }))
    return { ...base, series }
  }

  return {
    ...base,
    series: [
      {
        name: chart.valueField || '数值',
        type: chart.type === 'line' ? 'line' : 'bar',
        smooth: chart.type === 'line',
        barMaxWidth: 36,
        data: categories.map((category) => {
          const matched = rows.find((row) => String(row[categoryField] ?? '-') === category)
          return matched ? toNumber(matched[valueField]) : 0
        }),
      },
    ],
  }
})

function columnWidth(column: AssistantDataColumn) {
  if (column.type === 'number') return 110
  if (column.type === 'date') return 160
  const label = column.label || column.field
  return Math.min(240, Math.max(120, label.length * 16 + 40))
}

function formatCell(column: AssistantDataColumn, row: Record<string, any>) {
  const value = row[column.field]
  if (value == null || value === '') return '-'
  if (column.type === 'number') {
    const num = typeof value === 'number' ? value : Number(value)
    if (Number.isFinite(num)) {
      return Number.isInteger(num) ? num.toLocaleString('zh-CN') : num.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
    }
  }
  return String(value)
}

async function copySql() {
  try {
    await navigator.clipboard.writeText(props.result.sql || '')
    ElMessage.success('SQL 已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

function exportCsv() {
  const columns = props.result.columns || []
  const rows = props.result.rows || []
  if (!columns.length || !rows.length) return
  const escape = (value: unknown) => {
    const text = value == null ? '' : String(value)
    return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
  }
  const lines = [
    columns.map((column) => escape(column.label || column.field)).join(','),
    ...rows.map((row) => columns.map((column) => escape(row[column.field])).join(',')),
  ]
  // 加 BOM，避免 Excel 打开中文乱码
  const blob = new Blob([`\uFEFF${lines.join('\n')}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${title.value || '数据查询结果'}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.assistant-data-result {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.assistant-data-result__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.assistant-data-result__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.assistant-data-result__title-icon {
  color: var(--el-color-primary);
}

.assistant-data-result__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.assistant-data-result__chart {
  padding: 8px 8px 0;
}

.assistant-data-result__chart-canvas {
  height: 260px;
  width: 100%;
}

.assistant-data-result__table {
  padding: 8px;
}

.assistant-data-result__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.assistant-data-result__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.assistant-data-result__tables {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.assistant-data-result__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.assistant-data-result__table-chip {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.assistant-data-result__actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.assistant-data-result__sql {
  position: relative;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  padding: 8px 12px;
}

.assistant-data-result__sql-code {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'JetBrains Mono', Consolas, monospace;
  max-height: 200px;
  overflow: auto;
}

.assistant-data-result__sql-copy {
  position: absolute;
  top: 6px;
  right: 8px;
}
</style>
