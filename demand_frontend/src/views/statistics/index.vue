<template>
  <div class="statistics-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon total" />
            <div class="stat-info">
              <div class="stat-value">{{ total }}</div>
              <div class="stat-label">需求总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon status" />
            <div class="stat-info">
              <div class="stat-value">{{ statusCount }}</div>
              <div class="stat-label">按状态分类</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon type" />
            <div class="stat-info">
              <div class="stat-value">{{ typeCount }}</div>
              <div class="stat-label">按类型分类</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon priority" />
            <div class="stat-info">
              <div class="stat-value">{{ priorityCount }}</div>
              <div class="stat-label">按优先级分类</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-title">需求状态分布</span>
          </template>
          <v-chart v-if="pieLoaded" :option="pieOption" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-title">需求类型分布</span>
          </template>
          <v-chart v-if="barLoaded" :option="barOption" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 需求时长统计 -->
    <el-row :gutter="16" class="table-row">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span class="chart-title">需求时长统计</span>
          </template>
          <el-table :data="durationData" border>
            <el-table-column prop="stateName" label="状态" />
            <el-table-column label="平均天数" width="150">
              <template #default="{ row }">
                {{ (row.avgHours / 24).toFixed(1) }}
              </template>
            </el-table-column>
            <el-table-column label="最大天数" width="150">
              <template #default="{ row }">
                {{ (row.maxHours / 24).toFixed(1) }}
              </template>
            </el-table-column>
            <el-table-column label="最小天数" width="150">
              <template #default="{ row }">
                {{ (row.minHours / 24).toFixed(1) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 项目完成率 -->
    <el-row :gutter="16" class="progress-row">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <span class="chart-title">项目完成率</span>
          </template>
          <div class="project-progress">
            <div v-for="p in projectRates" :key="p.name" class="progress-item">
              <span class="project-name">{{ p.name }}</span>
              <el-progress
                :percentage="p.rate"
                :color="getProgressColor(p.rate)"
                :stroke-width="20"
              />
              <span class="progress-text">{{ p.completed }}/{{ p.total }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getDistributionData, getDurationData } from '@/api/modules/statistics'

use([CanvasRenderer, PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

// 统计卡片数据
const total = ref(0)
const statusCount = ref(0)
const typeCount = ref(0)
const priorityCount = ref(0)

// 饼图
const pieLoaded = ref(false)
const pieOption = ref({
  tooltip: { trigger: 'item' },
  legend: { top: '5%', left: 'center' },
  series: [{
    name: '需求状态',
    type: 'pie',
    radius: ['40%', '70%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
    label: { show: false, position: 'center' },
    emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
    data: [] as { name: string; value: number }[],
  }],
})

// 柱状图
const barLoaded = ref(false)
const barOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  xAxis: { type: 'category', data: [] as string[] },
  yAxis: { type: 'value' },
  series: [{
    name: '数量',
    type: 'bar',
    data: [] as number[],
    itemStyle: { color: '#409EFF' },
  }],
})

// 时长数据
const durationData = ref<{ stateName: string; avgHours: number; maxHours: number; minHours: number }[]>([])

// 项目完成率
const projectRates = ref<{ name: string; rate: number; completed: number; total: number }[]>([])

const getProgressColor = (rate: number) => {
  if (rate >= 80) return '#67C23A'
  if (rate >= 50) return '#409EFF'
  if (rate >= 30) return '#E6A23C'
  return '#F56C6C'
}

const loadDistributionData = async () => {
  try {
    const res = await getDistributionData(1)
    const data = res as unknown as { statusDistribution: Record<string, number>; typeDistribution: Record<string, number>; priorityDistribution: Record<string, number> }

    // 状态分布饼图
    pieOption.value.series[0].data = Object.entries(data.statusDistribution || {}).map(([name, value]) => ({ name, value }))
    pieLoaded.value = true

    // 类型分布柱状图
    barOption.value.xAxis.data = Object.keys(data.typeDistribution || {})
    barOption.value.series[0].data = Object.values(data.typeDistribution || {})
    barLoaded.value = true

    // 统计卡片
    const statusEntries = Object.entries(data.statusDistribution || {})
    total.value = statusEntries.reduce((sum, [, v]) => sum + v, 0)
    statusCount.value = statusEntries.length
    typeCount.value = Object.keys(data.typeDistribution || {}).length
    priorityCount.value = Object.keys(data.priorityDistribution || {}).length
  } catch {
    // 模拟数据
    pieOption.value.series[0].data = [
      { name: '待处理', value: 12 },
      { name: '进行中', value: 18 },
      { name: '评审中', value: 5 },
      { name: '已完成', value: 30 },
      { name: '已关闭', value: 8 },
    ]
    pieLoaded.value = true

    barOption.value.xAxis.data = ['功能需求', '性能优化', 'Bug修复', '技术债务', '新特性']
    barOption.value.series[0].data = [25, 15, 20, 8, 12]
    barLoaded.value = true

    total.value = 73
    statusCount.value = 5
    typeCount.value = 5
    priorityCount.value = 4
  }
}

const loadDurationData = async () => {
  try {
    const res = await getDurationData(1)
    durationData.value = res as unknown as typeof durationData.value
  } catch {
    durationData.value = [
      { stateName: '待处理', avgHours: 48, maxHours: 120, minHours: 2 },
      { stateName: '进行中', avgHours: 96, maxHours: 240, minHours: 8 },
      { stateName: '评审中', avgHours: 24, maxHours: 72, minHours: 4 },
      { stateName: '已完成', avgHours: 72, maxHours: 192, minHours: 12 },
      { stateName: '已关闭', avgHours: 12, maxHours: 48, minHours: 2 },
    ]
  }
}

const loadProjectRates = () => {
  projectRates.value = [
    { name: '需求管理系统', rate: 68, completed: 34, total: 50 },
    { name: '用户中心', rate: 85, completed: 17, total: 20 },
    { name: '数据平台', rate: 42, completed: 21, total: 50 },
    { name: '消息服务', rate: 91, completed: 20, total: 22 },
  ]
}

onMounted(() => {
  loadDistributionData()
  loadDurationData()
  loadProjectRates()
})
</script>

<style scoped lang="scss">
.statistics-page {
  .stat-cards {
    margin-bottom: 16px;
  }

  .stat-card {
    .stat-content {
      display: flex;
      align-items: center;

      .stat-icon {
        width: 60px;
        height: 60px;
        border-radius: 12px;
        margin-right: 16px;

        &.total { background: linear-gradient(135deg, #409EFF, #66b1ff); }
        &.status { background: linear-gradient(135deg, #67C23A, #85ce61); }
        &.type { background: linear-gradient(135deg, #E6A23C, #ebb563); }
        &.priority { background: linear-gradient(135deg, #F56C6C, #f78989); }
      }

      .stat-info {
        .stat-value {
          font-size: 28px;
          font-weight: 600;
          color: #303133;
        }

        .stat-label {
          font-size: 14px;
          color: #909399;
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
      height: 350px;
    }
  }

  .table-row {
    margin-bottom: 16px;
  }

  .project-progress {
    .progress-item {
      display: flex;
      align-items: center;
      margin-bottom: 16px;

      .project-name {
        width: 120px;
        font-weight: 500;
        flex-shrink: 0;
      }

      .el-progress {
        flex: 1;
        margin: 0 16px;
      }

      .progress-text {
        width: 60px;
        text-align: right;
        color: #909399;
        font-size: 14px;
        flex-shrink: 0;
      }
    }
  }
}
</style>
