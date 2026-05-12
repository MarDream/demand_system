<template>
  <div class="dashboard-container">
    <div class="dashboard-header">
      <h2>工作台</h2>
      <span class="dashboard-subtitle">欢迎回来，{{ userStore.userInfo?.realName || '用户' }}</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row" v-loading="statsLoading">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ backgroundColor: card.bgColor }">
              <el-icon :size="24" :color="card.iconColor">
                <component :is="card.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
              <div class="stat-tip">{{ card.tip }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 需求状态分布 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="section-header" @click="toggleSection('statusDist')">
          <span>需求状态分布</span>
          <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.statusDist }"><ArrowRight /></el-icon>
        </div>
      </template>
      <div v-show="expandedSections.statusDist" class="section-body">
        <div class="chart-box" v-loading="distLoading">
          <v-chart v-if="pieLoaded" :option="pieOption" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </div>
      </div>
    </el-card>

    <!-- 最近需求 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="section-header" @click="toggleSection('recent')">
          <span>最近需求</span>
          <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.recent }"><ArrowRight /></el-icon>
        </div>
      </template>
      <div v-show="expandedSections.recent" class="section-body">
        <div class="recent-list" v-loading="recentLoading">
          <el-empty v-if="recentRequirements.length === 0" description="暂无需求" />
          <div v-for="item in recentRequirements" :key="item.id" class="recent-item">
            <div class="recent-title">{{ item.title }}</div>
            <div class="recent-meta">
              <el-tag :type="getStatusType(item.status)" size="small">{{ item.status }}</el-tag>
              <el-tag :type="getPriorityType(item.priority)" size="small">{{ item.priority }}</el-tag>
              <span class="recent-date">{{ formatDate(item.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 需求类型分布 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="section-header" @click="toggleSection('typeDist')">
          <span>需求类型分布</span>
          <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.typeDist }"><ArrowRight /></el-icon>
        </div>
      </template>
      <div v-show="expandedSections.typeDist" class="section-body">
        <div class="chart-box" v-loading="distLoading">
          <v-chart v-if="barLoaded" :option="barOption" class="chart" autoresize />
          <el-empty v-else description="暂无数据" />
        </div>
      </div>
    </el-card>

    <!-- 需求时长统计 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="section-header" @click="toggleSection('duration')">
          <span>需求时长统计</span>
          <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.duration }"><ArrowRight /></el-icon>
        </div>
      </template>
      <div v-show="expandedSections.duration" class="section-body">
        <el-table :data="durationData" border v-loading="durationLoading">
          <el-table-column prop="stateName" label="状态" />
          <el-table-column label="平均天数" width="150">
            <template #default="{ row }">{{ (row.avgHours / 24).toFixed(1) }}</template>
          </el-table-column>
          <el-table-column label="最大天数" width="150">
            <template #default="{ row }">{{ (row.maxHours / 24).toFixed(1) }}</template>
          </el-table-column>
          <el-table-column label="最小天数" width="150">
            <template #default="{ row }">{{ (row.minHours / 24).toFixed(1) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 项目进度概览 -->
    <el-card shadow="hover" class="section-card">
      <template #header>
        <div class="section-header" @click="toggleSection('project')">
          <span>项目进度概览</span>
          <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.project }"><ArrowRight /></el-icon>
        </div>
      </template>
      <div v-show="expandedSections.project" class="section-body">
        <el-empty v-if="projectRates.length === 0" description="暂无项目数据" />
        <div v-else class="project-progress">
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
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Document, Loading, CircleCheck, Warning, ArrowRight } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getDashboardData, getDistributionData, getDurationData } from '@/api/modules/statistics'
import { getRequirementList } from '@/api/modules/requirement'
import { useUserStore } from '@/stores/modules/user'
import type { Requirement } from '@/types/requirement'

use([CanvasRenderer, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const userStore = useUserStore()

const expandedSections = reactive<Record<string, boolean>>({
  statusDist: true,
  recent: true,
  typeDist: true,
  duration: true,
  project: true,
})

function toggleSection(key: string) {
  expandedSections[key] = !expandedSections[key]
}

// 统计卡片
const statsLoading = ref(true)
const statsData = ref<any>(null)

const statCards = computed(() => [
  { icon: Document, label: '总需求数', value: statsData.value?.totalReqs ?? 0, tip: '全部需求', bgColor: '#ecf5ff', iconColor: '#409EFF' },
  { icon: Loading, label: '进行中需求', value: statsData.value?.inProgressReqs ?? 0, tip: '开发中', bgColor: '#fdf6ec', iconColor: '#E6A23C' },
  { icon: CircleCheck, label: '已完成', value: statsData.value?.completedReqs ?? 0, tip: '已交付', bgColor: '#f0f9eb', iconColor: '#67C23A' },
  { icon: Warning, label: '已逾期', value: statsData.value?.overdueReqs ?? 0, tip: '超过截止日期', bgColor: '#fef0f0', iconColor: '#F56C6C' },
])

// 状态分布饼图
const distLoading = ref(true)
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

// 类型分布柱状图
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

// 最近需求
const recentLoading = ref(true)
const recentRequirements = ref<Requirement[]>([])

// 时长统计
const durationLoading = ref(true)
const durationData = ref<{ stateName: string; avgHours: number; maxHours: number; minHours: number }[]>([])

// 项目进度
const projectRates = ref<{ name: string; rate: number; completed: number; total: number }[]>([])

function getProgressColor(rate: number) {
  if (rate >= 80) return '#67C23A'
  if (rate >= 50) return '#409EFF'
  if (rate >= 30) return '#E6A23C'
  return '#F56C6C'
}

function getStatusType(status: string) {
  const map: Record<string, string> = { '待处理': 'info', '进行中': 'warning', '已完成': 'success', '已关闭': '', '已逾期': 'danger' }
  return (map[status] || 'info') as any
}

function getPriorityType(priority: string) {
  const map: Record<string, string> = { '紧急': 'danger', '高': 'warning', '中': '', '低': 'info' }
  return (map[priority] || 'info') as any
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

async function loadDashboardData() {
  statsLoading.value = true
  try {
    const res = await getDashboardData(1)
    const data = (res as any)?.data ?? (res as any)
    statsData.value = data || {}
  } catch {
    statsData.value = { totalReqs: 0, inProgressReqs: 0, completedReqs: 0, overdueReqs: 0 }
  } finally {
    statsLoading.value = false
  }
}

async function loadDistributionData() {
  distLoading.value = true
  try {
    const res = await getDistributionData(1)
    const raw = (res as any)?.data ?? (res as any)
    const statusDist: Record<string, number> = raw?.statusDist || raw?.statusDistribution || {}
    const typeDist: Record<string, number> = raw?.typeDist || raw?.typeDistribution || {}

    pieOption.value.series[0].data = Object.entries(statusDist).map(([name, value]) => ({ name, value }))
    pieLoaded.value = true

    barOption.value.xAxis.data = Object.keys(typeDist)
    barOption.value.series[0].data = Object.values(typeDist)
    barLoaded.value = true
  } catch {
    pieLoaded.value = false
    barLoaded.value = false
  } finally {
    distLoading.value = false
  }
}

async function loadRecentRequirements() {
  recentLoading.value = true
  try {
    const res = await getRequirementList({ pageNum: 1, pageSize: 5, sortField: 'createdAt', sortOrder: 'desc' })
    const data = (res as any)?.data ?? (res as any)
    recentRequirements.value = data?.list || []
  } catch {
    recentRequirements.value = []
  } finally {
    recentLoading.value = false
  }
}

async function loadDurationData() {
  durationLoading.value = true
  try {
    const res = await getDurationData(1)
    durationData.value = res as unknown as typeof durationData.value
  } catch {
    durationData.value = []
  } finally {
    durationLoading.value = false
  }
}

function loadProjectRates() {
  const total = statsData.value?.totalReqs ?? 0
  const completed = statsData.value?.completedReqs ?? 0
  const rate = total > 0 ? Math.round((completed / total) * 100) : 0
  if (total > 0) {
    projectRates.value = [{ name: '综合运营管理平台 v1.0', total, completed, rate }]
  } else {
    projectRates.value = []
  }
}

onMounted(async () => {
  await loadDashboardData()
  loadDistributionData()
  loadRecentRequirements()
  loadDurationData()
  loadProjectRates()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 4px;
}

.dashboard-header {
  margin-bottom: 20px;

  h2 {
    font-size: 22px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 4px;
  }

  .dashboard-subtitle {
    font-size: 14px;
    color: #909399;
  }
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  margin-bottom: 16px;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #606266;
  margin-top: 2px;
}

.stat-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.section-card {
  margin-bottom: 16px;

  :deep(.el-card__header) {
    padding: 14px 20px;
    cursor: pointer;
    user-select: none;
  }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.collapse-icon {
  font-size: 14px;
  color: #909399;
  transition: transform 0.2s;

  &.is-expanded {
    transform: rotate(90deg);
  }
}

.section-body {
  padding: 0;
}

.chart-box {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart {
  height: 320px;
  width: 100%;
}

.recent-list {
  max-height: 320px;
  overflow-y: auto;
}

.recent-item {
  padding: 12px 0;

  &:not(:last-child) {
    border-bottom: 1px solid #f0f2f5;
  }
}

.recent-title {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recent-date {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.project-progress {
  .progress-item {
    display: flex;
    align-items: center;
    margin-bottom: 16px;

    &:last-child {
      margin-bottom: 0;
    }

    .project-name {
      width: 160px;
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
</style>
