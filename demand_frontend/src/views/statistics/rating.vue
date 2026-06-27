<template>
  <div class="rating-dashboard">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button :icon="ArrowLeft" link @click="goBack">返回统计报表</el-button>
      <div class="filters">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="true"
          style="width: 260px"
        />
        <el-radio-group v-model="granularity" size="small">
          <el-radio-button label="WEEK">按周</el-radio-button>
          <el-radio-button label="MONTH">按月</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon overall" />
            <div class="stat-info">
              <div class="stat-value">{{ formatScore(statistics?.overallAverage) }}</div>
              <div class="stat-label">总体平均分</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon count" />
            <div class="stat-info">
              <div class="stat-value">{{ statistics?.totalEvaluations ?? 0 }}</div>
              <div class="stat-label">评价总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon low" />
            <div class="stat-info">
              <div class="stat-value">{{ statistics?.topLowRated?.length ?? 0 }}</div>
              <div class="stat-label">低分需求数（&lt;3星）</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon dim" />
            <div class="stat-info">
              <div class="stat-value">{{ dimensionCount }}</div>
              <div class="stat-label">评分维度数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 + 分布图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="14">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="chart-title">评分趋势</span></template>
          <v-chart v-if="trendLoaded" :option="trendOption" :init-options="chartInitOptions" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="chart-title">评分分布</span></template>
          <v-chart v-if="distributionLoaded" :option="distributionOption" :init-options="chartInitOptions" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 维度平均 + 节点平均 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="chart-title">各维度平均分</span></template>
          <v-chart v-if="dimensionLoaded" :option="dimensionOption" :init-options="chartInitOptions" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="chart-title">各节点平均分（识别流程瓶颈）</span></template>
          <v-chart v-if="nodeLoaded" :option="nodeOption" :init-options="chartInitOptions" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 低分需求列表 -->
    <el-card shadow="never" class="low-rated-card">
      <template #header><span class="chart-title">待改进需求（低分列表）</span></template>
      <el-table :data="statistics?.topLowRated ?? []" border stripe empty-text="暂无低分需求">
        <el-table-column prop="requirementNo" label="需求编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="nodeName" label="评价节点" width="130" />
        <el-table-column label="评分" width="170">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="evaluatorName" label="评价人" width="110" />
        <el-table-column prop="comment" label="评价说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="评价时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row.requirementId)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import {
  getRatingDistribution,
  getRatingDimensionAverages,
  getRatingStatistics,
  getRatingTrend,
  getNodeAverageRatings,
  type RatingQueryParams,
  type RatingStatistics,
  type RatingTrendPoint,
} from '@/api/modules/statistics'

use([SVGRenderer, BarChart, LineChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const chartInitOptions = { renderer: 'svg' as const }

const dateRange = ref<[string, string] | null>(null)
const granularity = ref<'WEEK' | 'MONTH'>('WEEK')

const statistics = ref<RatingStatistics | null>(null)
const trend = ref<RatingTrendPoint[]>([])
const distribution = ref<Record<number, number>>({})
const dimensionAverages = ref<Record<string, number>>({})
const nodeAverages = ref<Record<string, number>>({})

const trendLoaded = ref(false)
const distributionLoaded = ref(false)
const dimensionLoaded = ref(false)
const nodeLoaded = ref(false)

const dimensionCount = computed(() => Object.keys(dimensionAverages.value).length)

const queryParams = computed<RatingQueryParams>(() => ({
  startDate: dateRange.value?.[0],
  endDate: dateRange.value?.[1],
  granularity: granularity.value,
}))

function unwrap<T>(res: any): T {
  return (res?.data ?? res) as T
}

function formatScore(v?: number) {
  return v == null ? '-' : Number(v).toFixed(2)
}

function formatTime(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

function goBack() {
  router.push('/statistics')
}

function goDetail(id: number) {
  router.push(`/requirements/${id}`)
}

// 趋势图配置
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['平均分', '评价数'] },
  grid: { left: 40, right: 40, bottom: 30, top: 40 },
  xAxis: { type: 'category', data: trend.value.map(p => p.label) },
  yAxis: [
    { type: 'value', name: '平均分', min: 0, max: 5 },
    { type: 'value', name: '评价数' },
  ],
  series: [
    {
      name: '平均分',
      type: 'line',
      smooth: true,
      data: trend.value.map(p => p.average),
      itemStyle: { color: '#6a9bcc' },
      areaStyle: { opacity: 0.1 },
    },
    {
      name: '评价数',
      type: 'bar',
      yAxisIndex: 1,
      data: trend.value.map(p => p.count),
      itemStyle: { color: '#b0aea5' },
    },
  ],
}))

// 分布图配置
const distributionOption = computed(() => {
  const labels = Object.keys(distribution.value).sort((a, b) => Number(a) - Number(b))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: { formatter: '{b}星: {c}' },
      data: labels.map(k => ({ name: `${k}星`, value: distribution.value[Number(k)] })),
    }],
  }
})

// 维度平均图配置
const dimensionOption = computed(() => {
  const entries = Object.entries(dimensionAverages.value)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 60, right: 30, bottom: 30, top: 30 },
    xAxis: { type: 'category', data: entries.map(([k]) => k), axisLabel: { interval: 0, rotate: entries.length > 4 ? 20 : 0 } },
    yAxis: { type: 'value', min: 0, max: 5 },
    series: [{
      type: 'bar',
      data: entries.map(([, v]) => v),
      itemStyle: { color: '#788c5d' },
      label: { show: true, position: 'top', formatter: (p: any) => p.value?.toFixed(2) },
    }],
  }
})

// 节点平均图配置
const nodeOption = computed(() => {
  const entries = Object.entries(nodeAverages.value)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 60, right: 30, bottom: 30, top: 30 },
    xAxis: { type: 'category', data: entries.map(([k]) => k), axisLabel: { interval: 0, rotate: entries.length > 4 ? 20 : 0 } },
    yAxis: { type: 'value', min: 0, max: 5 },
    series: [{
      type: 'bar',
      data: entries.map(([, v]) => v),
      itemStyle: {
        color: (p: any) => (p.value == null ? '#b0aea5' : p.value < 3 ? '#d97757' : '#6a9bcc'),
      },
      label: { show: true, position: 'top', formatter: (p: any) => p.value?.toFixed(2) },
    }],
  }
})

async function loadStatistics() {
  try {
    const res = await getRatingStatistics(queryParams.value)
    statistics.value = unwrap<RatingStatistics>(res)
    // 综合接口已含分布、维度、节点、低分，直接复用
    if (statistics.value) {
      distribution.value = statistics.value.distribution ?? {}
      distributionLoaded.value = Object.keys(distribution.value).length > 0
      dimensionAverages.value = statistics.value.dimensionAverages ?? {}
      dimensionLoaded.value = Object.keys(dimensionAverages.value).length > 0
      nodeAverages.value = statistics.value.nodeAverages ?? {}
      nodeLoaded.value = Object.keys(nodeAverages.value).length > 0
    }
  } catch {
    statistics.value = null
  }
}

async function loadTrend() {
  try {
    const res = await getRatingTrend(queryParams.value)
    trend.value = unwrap<RatingTrendPoint[]>(res) ?? []
    trendLoaded.value = trend.value.length > 0
  } catch {
    trend.value = []
    trendLoaded.value = false
  }
}

// 分布/维度/节点在综合接口未覆盖时单独兜底
async function loadDetailCharts() {
  if (!dimensionLoaded.value) {
    try {
      dimensionAverages.value = unwrap<Record<string, number>>(await getRatingDimensionAverages(queryParams.value)) ?? {}
      dimensionLoaded.value = Object.keys(dimensionAverages.value).length > 0
    } catch { /* ignore */ }
  }
  if (!nodeLoaded.value) {
    try {
      nodeAverages.value = unwrap<Record<string, number>>(await getNodeAverageRatings(queryParams.value)) ?? {}
      nodeLoaded.value = Object.keys(nodeAverages.value).length > 0
    } catch { /* ignore */ }
  }
  if (!distributionLoaded.value) {
    try {
      distribution.value = unwrap<Record<number, number>>(await getRatingDistribution(queryParams.value)) ?? {}
      distributionLoaded.value = Object.keys(distribution.value).length > 0
    } catch { /* ignore */ }
  }
}

async function loadAll() {
  await Promise.all([loadStatistics(), loadTrend()])
  await loadDetailCharts()
}

watch([dateRange, granularity], () => loadAll())

onMounted(() => loadAll())
</script>

<style scoped lang="scss">
.rating-dashboard {
  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .filters {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }

  .stat-cards {
    margin-bottom: 16px;
  }

  .stat-card {
    .stat-content {
      display: flex;
      align-items: center;

      .stat-icon {
        width: 56px;
        height: 56px;
        border-radius: 12px;
        margin-right: 16px;

        &.overall { background: linear-gradient(135deg, #6a9bcc, #4a7ba8); }
        &.count { background: linear-gradient(135deg, #788c5d, #5a6e44); }
        &.low { background: linear-gradient(135deg, #d97757, #b85a3a); }
        &.dim { background: linear-gradient(135deg, #b0aea5, #8a887f); }
      }

      .stat-info {
        .stat-value {
          font-size: 26px;
          font-weight: 600;
        }
        .stat-label {
          font-size: 13px;
          color: var(--color-muted-text);
          margin-top: 4px;
        }
      }
    }
  }

  .chart-row {
    margin-bottom: 16px;
  }

  .chart-card {
    .chart-title {
      font-weight: 600;
    }
    .chart {
      height: 320px;
    }
  }

  .low-rated-card {
    .chart-title {
      font-weight: 600;
    }
  }
}
</style>
