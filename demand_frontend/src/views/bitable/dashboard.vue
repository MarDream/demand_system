<template>
  <div class="bitable-dashboard-page">
    <!-- 顶部：仪表盘选择与管理 -->
    <div class="bitable-dashboard-page__header">
      <el-button link @click="router.push('/bitable')">
        <el-icon><ArrowLeft /></el-icon> 返回列表
      </el-button>
      <el-select v-model="activeDashboardId" placeholder="选择仪表盘" style="width: 220px" @change="loadAll">
        <el-option v-for="d in dashboards" :key="d.id" :label="d.name" :value="d.id" />
      </el-select>
      <div class="bitable-dashboard-page__actions">
        <el-button size="small" @click="handleCreate">新建仪表盘</el-button>
        <el-button size="small" :disabled="!activeDashboardId" @click="showWidgetEditor = !showWidgetEditor">
          {{ showWidgetEditor ? '完成编辑' : '编辑组件' }}
        </el-button>
        <el-button size="small" type="danger" plain :disabled="!activeDashboardId" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <el-empty v-if="!activeDashboardId && !loading" description="尚无仪表盘，点击「新建仪表盘」开始" />

    <!-- 组件编辑面板 -->
    <div v-if="showWidgetEditor && activeDashboardId" class="widget-editor">
      <div v-for="(w, idx) in editingWidgets" :key="idx" class="widget-editor__row">
        <el-select v-model="w.type" size="small" style="width: 90px">
          <el-option label="指标卡" value="kpi" />
          <el-option label="柱状图" value="bar" />
          <el-option label="折线图" value="line" />
          <el-option label="饼图" value="pie" />
        </el-select>
        <el-input v-model="w.title" size="small" placeholder="标题" style="width: 140px" />
        <el-select v-model="w.tableId" size="small" placeholder="数据表" style="width: 150px" @change="w.fieldId = undefined; w.groupByFieldId = undefined">
          <el-option v-for="t in tables" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="w.aggregation" size="small" style="width: 100px">
          <el-option label="计数" value="count" />
          <el-option label="求和" value="sum" />
          <el-option label="平均" value="avg" />
          <el-option label="最小" value="min" />
          <el-option label="最大" value="max" />
        </el-select>
        <el-select v-model="w.fieldId" size="small" placeholder="数值字段" style="width: 150px" :disabled="w.aggregation === 'count'">
          <el-option v-for="f in tableFields(w.tableId)" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
        <el-select v-model="w.groupByFieldId" size="small" placeholder="分组字段（可空）" style="width: 160px" clearable>
          <el-option v-for="f in tableFields(w.tableId)" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
        <el-button link type="danger" size="small" @click="editingWidgets.splice(idx, 1)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <div class="widget-editor__footer">
        <el-button size="small" @click="addWidgetRow">
          <el-icon><Plus /></el-icon> 添加组件
        </el-button>
        <el-button size="small" type="primary" :loading="savingWidgets" @click="saveWidgets">保存组件</el-button>
      </div>
    </div>

    <!-- 组件展示区 -->
    <div v-if="activeDashboardId" class="widget-grid">
      <div v-for="(item, idx) in widgetData" :key="item.widgetId ?? idx" class="widget-card" :class="{ 'widget-card--wide': item.type !== 'kpi' }">
        <div class="widget-card__title">{{ item.title || widgetTitle(item.type) }}</div>
        <div v-if="item.data?.error" class="widget-card__error">{{ item.data.error }}</div>
        <template v-else-if="item.type === 'kpi'">
          <div class="widget-card__kpi">{{ formatValue(item.data?.value) }}</div>
          <div class="widget-card__sub">共 {{ item.data?.recordCount ?? 0 }} 条记录参与计算</div>
        </template>
        <template v-else>
          <v-chart v-if="chartOption(item)" :option="chartOption(item)" autoresize class="widget-card__chart" />
          <el-empty v-else description="暂无数据" :image-size="60" />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import {
  listDashboards, createDashboard, deleteDashboard,
  listDashboardWidgets, saveDashboardWidgets, getDashboardData,
  listTables, listFields,
} from '@/api/modules/bitable'

const route = useRoute()
const router = useRouter()
const baseId = Number(route.params.baseId)

const loading = ref(false)
const dashboards = ref<any[]>([])
const activeDashboardId = ref<number | null>(null)
const tables = ref<any[]>([])
const fieldsByTable = ref<Record<number, any[]>>({})
const widgetData = ref<any[]>([])

const showWidgetEditor = ref(false)
const savingWidgets = ref(false)
interface WidgetRow {
  type: 'kpi' | 'bar' | 'line' | 'pie'
  title: string
  tableId?: number
  fieldId?: number
  groupByFieldId?: number
  aggregation: string
}
const editingWidgets = ref<WidgetRow[]>([])
onMounted(async () => {
  loading.value = true
  try {
    dashboards.value = await listDashboards(baseId)
    tables.value = await listTables(baseId)
    if (dashboards.value.length) {
      activeDashboardId.value = dashboards.value[0].id
      await loadAll()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载仪表盘失败')
  } finally {
    loading.value = false
  }
})

async function loadAll() {
  if (!activeDashboardId.value) return
  try {
    const [data, widgetList] = await Promise.all([
      getDashboardData(activeDashboardId.value),
      listDashboardWidgets(activeDashboardId.value),
    ])
    widgetData.value = data
    // 编辑面板初始化：从组件实体的 dataSourceConfig 还原表单
    editingWidgets.value = widgetList.map((w: any) => {
      let ds: any = {}
      try {
        ds = typeof w.dataSourceConfig === 'string' ? JSON.parse(w.dataSourceConfig) : (w.dataSourceConfig || {})
      } catch {
        ds = {}
      }
      return {
        type: w.type as WidgetRow['type'],
        title: w.title || '',
        tableId: ds.tableId,
        fieldId: ds.fieldId,
        groupByFieldId: ds.groupByFieldId,
        aggregation: ds.aggregation || 'count',
      }
    })
    showWidgetEditor.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

function tableFields(tableId?: number) {
  if (!tableId) return []
  if (!fieldsByTable.value[tableId]) {
    fieldsByTable.value[tableId] = []
    listFields(tableId).then((fields) => {
      fieldsByTable.value[tableId] = fields
    })
  }
  return fieldsByTable.value[tableId]
}

function addWidgetRow() {
  editingWidgets.value.push({ type: 'kpi', title: '', aggregation: 'count' })
}

async function saveWidgets() {
  if (!activeDashboardId.value) return
  savingWidgets.value = true
  try {
    const payload = editingWidgets.value.map((w, idx) => ({
      type: w.type,
      title: w.title,
      sortNo: idx,
      dataSourceConfig: {
        tableId: w.tableId,
        fieldId: w.fieldId,
        groupByFieldId: w.groupByFieldId,
        aggregation: w.aggregation,
      },
    }))
    await saveDashboardWidgets(activeDashboardId.value, payload as any)
    ElMessage.success('组件已保存')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingWidgets.value = false
  }
}

async function handleCreate() {
  try {
    const { value } = await ElMessageBox.prompt('', '新建仪表盘', {
      inputValue: '新建仪表盘',
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
    })
    const id = await createDashboard(baseId, value.trim())
    dashboards.value = await listDashboards(baseId)
    activeDashboardId.value = id
    await loadAll()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

async function handleDelete() {
  if (!activeDashboardId.value) return
  try {
    await ElMessageBox.confirm('确定删除该仪表盘及其全部组件吗？', '删除确认', { type: 'warning' })
    await deleteDashboard(activeDashboardId.value)
    dashboards.value = await listDashboards(baseId)
    activeDashboardId.value = dashboards.value[0]?.id ?? null
    widgetData.value = []
    await loadAll()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

function widgetTitle(type: string) {
  return { kpi: '指标卡', bar: '柱状图', line: '折线图', pie: '饼图' }[type] || type
}

function formatValue(v: unknown): string {
  if (v == null) return '-'
  if (typeof v === 'number') {
    return Number.isInteger(v) ? String(v) : v.toFixed(2)
  }
  return String(v)
}

function chartOption(item: any): any | null {
  const data = item.data
  if (!data || !Array.isArray(data.labels)) return null
  const labels: string[] = data.labels
  const values: unknown[] = data.values

  if (item.type === 'pie') {
    return {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['35%', '70%'],
        data: labels.map((label, i) => ({ name: label, value: values[i] })),
        label: { formatter: '{b}: {c}' },
      }],
    }
  }

  // bar / line
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 20, bottom: 24 },
    xAxis: { type: 'category', data: labels, axisLabel: { interval: 0, rotate: labels.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [{
      type: item.type === 'line' ? 'line' : 'bar',
      data: values,
      smooth: true,
      itemStyle: { borderRadius: [4, 4, 0, 0] },
    }],
  }
}
</script>

<style scoped lang="scss">
.bitable-dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 24px;
  height: 100%;
  overflow-y: auto;
}

.bitable-dashboard-page__header {
  display: flex;
  align-items: center;
  gap: 12px;

  .bitable-dashboard-page__actions {
    display: flex;
    gap: 8px;
    margin-left: auto;
  }
}

.widget-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 8px;
  background: var(--color-surface, #fff);

  .widget-editor__row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .widget-editor__footer {
    display: flex;
    gap: 8px;
  }
}

.widget-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.widget-card {
  padding: 14px 16px;
  border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 10px;
  background: var(--color-surface, #fff);
  min-height: 120px;

  &--wide {
    grid-column: span 2;
  }
}

.widget-card__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary, #475569);
  margin-bottom: 8px;
}

.widget-card__kpi {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-primary, #2563eb);
  font-variant-numeric: tabular-nums;
}

.widget-card__sub {
  font-size: 12px;
  color: var(--color-text-secondary, #94a3b8);
  margin-top: 4px;
}

.widget-card__error {
  font-size: 12px;
  color: var(--el-color-danger, #ef4444);
}

.widget-card__chart {
  width: 100%;
  height: 220px;
}
</style>
