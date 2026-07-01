<template>
  <div class="dashboard-container">
    <!-- 欢迎区 + 快捷操作 -->
    <div class="dashboard-header">
      <div class="dashboard-header__left">
        <h2>工作台</h2>
        <span class="dashboard-subtitle">{{ getGreeting() }}，{{ userStore.userInfo?.realName || '用户' }}</span>
      </div>
      <div class="dashboard-header__actions">
        <AppButton type="primary" permission="button:requirement:create" @click="router.push('/requirements/create')">
          <el-icon><Document /></el-icon>新建需求
        </AppButton>
        <el-button @click="router.push('/requirements?view=pending')">
          <el-icon><List /></el-icon>我的待办
        </el-button>
      </div>
    </div>

    <!-- 统计卡片（骨架屏） -->
    <el-row :gutter="24" class="stat-row">
      <template v-if="statsLoading">
        <el-col :xs="24" :sm="12" :lg="6" v-for="i in 4" :key="i">
          <div class="skeleton-card">
            <div class="skeleton-card__icon shimmer" />
            <div class="skeleton-card__info">
              <div class="skeleton-card__value shimmer" />
              <div class="skeleton-card__label shimmer" />
            </div>
          </div>
        </el-col>
      </template>
      <template v-else>
        <el-col :xs="24" :sm="12" :lg="6" v-for="card in statCardsPro" :key="card.label">
          <StatCardPro
            :label="card.label"
            :value="card.value"
            :icon="card.icon"
            :tip="card.tip"
            :gradient-start="card.gradientStart"
            :gradient-end="card.gradientEnd"
            :trend="card.trend"
          />
        </el-col>
      </template>
    </el-row>

    <!-- 双栏布局：图表 + 最近动态 -->
    <el-row :gutter="24" class="content-row">
      <!-- 左栏：趋势图表 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>需求状态分布</span>
              <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.statusDist }" @click="toggleSection('statusDist')"><ArrowRight /></el-icon>
            </div>
          </template>
          <div v-show="expandedSections.statusDist" class="section-body">
            <template v-if="distLoading">
              <div class="skeleton-chart shimmer" style="height:260px" />
            </template>
            <template v-else-if="pieLoaded">
              <div class="status-dist-layout">
                <!-- 左侧：环形图 -->
                <div class="status-pie-wrap">
                  <v-chart :option="pieOption" :init-options="chartInitOptions" class="status-pie-chart" autoresize />
                </div>
                <!-- 右侧：图例列表 -->
                <div class="status-legend-list">
                  <div
                    v-for="(item, idx) in pieOption.series[0].data"
                    :key="item.name"
                    class="status-legend-item"
                  >
                    <span class="legend-dot" :style="{ background: statusPieColors[idx % statusPieColors.length] }" />
                    <span class="legend-name">{{ item.name }}</span>
                    <span class="legend-value">{{ item.value }}</span>
                    <span class="legend-percent">
                      {{ pieTotalCount > 0 ? ((item.value / pieTotalCount) * 100).toFixed(1) + '%' : '0%' }}
                    </span>
                  </div>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无数据" />
          </div>
        </el-card>

        <!-- 需求类型分布 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>需求类型分布</span>
              <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.typeDist }" @click="toggleSection('typeDist')"><ArrowRight /></el-icon>
            </div>
          </template>
          <div v-show="expandedSections.typeDist" class="section-body">
            <template v-if="distLoading">
              <div class="skeleton-chart shimmer" style="height:280px" />
            </template>
            <template v-else-if="barLoaded">
              <v-chart :option="barOption" :init-options="chartInitOptions" class="type-bar-chart" autoresize />
            </template>
            <el-empty v-else description="暂无数据" />
          </div>
        </el-card>

        <!-- 需求时长统计 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>需求时长统计</span>
              <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.duration }" @click="toggleSection('duration')"><ArrowRight /></el-icon>
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
      </el-col>

      <!-- 右栏：流程处理概览 + 最近需求 + 流转历史 + 项目进度 -->
      <el-col :xs="24" :lg="8">
        <!-- 流程处理概览 -->
        <el-card shadow="hover" class="section-card workflow-overview-card">
          <template #header>
            <div class="section-header">
              <span>流程处理概览</span>
            </div>
          </template>
          <div class="workflow-overview">
            <!-- 左侧：环形图 -->
            <div class="workflow-circle-wrap">
              <div class="workflow-circle" :style="{ '--progress': workflowProcessRate }">
                <svg class="circle-svg" viewBox="0 0 100 100">
                  <circle class="circle-bg" cx="50" cy="50" r="42" />
                  <circle
                    class="circle-fill"
                    cx="50" cy="50" r="42"
                    :stroke-dasharray="`${workflowProcessRate * 2.639} 263.9`"
                  />
                </svg>
                <div class="circle-inner">
                  <el-icon class="circle-icon"><Document /></el-icon>
                  <span class="circle-label">流程总览</span>
                </div>
              </div>
            </div>
            <!-- 右侧：统计项列表 -->
            <div class="workflow-stats-list">
              <div
                v-for="item in workflowStatItems"
                :key="item.key"
                class="workflow-stat-item"
                @click="item.route && router.push(item.route)"
                :class="{ 'is-clickable': !!item.route }"
              >
                <span class="workflow-stat-label">{{ item.label }}</span>
                <span class="workflow-stat-value" :class="item.cls">
                  <template v-if="workflowStatsLoading">—</template>
                  <template v-else>{{ workflowStats[item.key as keyof typeof workflowStats] }}</template>
                </span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 最近需求 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>最近需求</span>
              <el-button text size="small" @click="router.push('/requirements')">查看全部</el-button>
            </div>
          </template>
          <div class="recent-list">
            <template v-if="recentLoading">
              <div v-for="i in 4" :key="i" class="skeleton-recent-item">
                <div class="skeleton-recent-title shimmer" />
                <div class="skeleton-recent-meta shimmer" />
              </div>
            </template>
            <template v-else>
              <el-empty v-if="recentRequirements.length === 0" description="暂无需求" :image-size="60" />
              <div v-for="item in recentRequirements" :key="item.id" class="recent-item" @click="router.push(`/requirements/${item.id}`)">
                <div class="recent-title">{{ item.title }}</div>
                <div class="recent-meta">
                  <el-tag :type="getStatusType(item.status)" size="small">{{ item.status }}</el-tag>
                  <el-tag :type="getPriorityType(item.priority)" :style="getPriorityStyle(item.priority)" size="small">{{ getPriorityLabel(item.priority) }}</el-tag>
                  <span class="recent-date">{{ formatDate(item.createdAt) }}</span>
                </div>
              </div>
            </template>
          </div>
        </el-card>

        <!-- 流转历史 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>流转动态</span>
            </div>
          </template>
          <div class="activity-timeline">
            <template v-if="recentLoading">
              <div v-for="i in 3" :key="i" class="skeleton-timeline-item">
                <div class="skeleton-timeline-dot shimmer" />
                <div class="skeleton-timeline-content shimmer" />
              </div>
            </template>
            <template v-else>
              <el-empty v-if="recentActivities.length === 0" description="暂无动态" :image-size="60" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="activity in recentActivities"
                  :key="activity.id"
                  :timestamp="activity.time"
                  :type="getActivityType(activity.type)"
                  placement="top"
                >
                  <div class="activity-item">
                    <span class="activity-title">{{ activity.title }}</span>
                    <span class="activity-user">{{ activity.userName }}</span>
                  </div>
                </el-timeline-item>
              </el-timeline>
            </template>
          </div>
        </el-card>

        <!-- 项目进度 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-header">
              <span>项目进度</span>
            </div>
          </template>
          <el-empty v-if="projectRates.length === 0" description="暂无项目数据" :image-size="60" />
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
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Loading, CircleCheck, Warning, ArrowRight, List } from '@element-plus/icons-vue'
import StatCardPro from '@/components/common/StatCardPro.vue'
import AppButton from '@/components/common/AppButton.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getDashboardData, getDistributionData, getDurationData, getWorkflowProcessStats } from '@/api/modules/statistics'
import type { WorkflowProcessStats } from '@/api/modules/statistics'
import { getRequirementList } from '@/api/modules/requirement'
import { useUserStore } from '@/stores/modules/user'
import { formatDate, stripPriorityPrefix, normalizeText } from '@/utils/format'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { useRequirementTag } from '@/composables/useRequirementTag'
import type { Requirement } from '@/types/requirement'

use([SVGRenderer, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const chartInitOptions = { renderer: 'svg' as const }
const userStore = useUserStore()

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

const expandedSections = reactive<Record<string, boolean>>({
  statusDist: true,
  typeDist: true,
  duration: true,
})

function toggleSection(key: string) {
  expandedSections[key] = !expandedSections[key]
}

// 统计卡片
const statsLoading = ref(true)
const statsData = ref<any>(null)

const COLORS = {
  accent: '#0369A1',
  accentHover: '#0284C7',
  amber: '#D97706',
  amberHover: '#F59E0B',
  emerald: '#059669',
  emeraldHover: '#10B981',
  red: '#DC2626',
  redHover: '#EF4444',
}

const statCardsPro = computed(() => [
  { icon: Document, label: '总需求数', value: statsData.value?.totalReqs ?? 0, tip: '全部需求', gradientStart: COLORS.accent, gradientEnd: COLORS.accentHover, trend: null },
  { icon: Loading, label: '进行中需求', value: statsData.value?.inProgressReqs ?? 0, tip: '开发中', gradientStart: COLORS.amber, gradientEnd: COLORS.amberHover, trend: null },
  { icon: CircleCheck, label: '已完成', value: statsData.value?.completedReqs ?? 0, tip: '已交付', gradientStart: COLORS.emerald, gradientEnd: COLORS.emeraldHover, trend: null },
  { icon: Warning, label: '已逾期', value: statsData.value?.overdueReqs ?? 0, tip: '超过截止日期', gradientStart: COLORS.red, gradientEnd: COLORS.redHover, trend: null },
])

// 状态分布饼图
const distLoading = ref(true)
const pieLoaded = ref(false)

// 饼图配色（与截图参考风格一致）
const statusPieColors = ['#2563EB', '#60A5FA', '#93C5FD', '#F59E0B', '#10B981', '#EF4444', '#8B5CF6', '#6366F1']

const pieTotalCount = computed(() =>
  (pieOption.value.series[0].data as { name: string; value: number }[]).reduce((s, d) => s + d.value, 0)
)

const pieOption = ref<any>({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)',
  },
  color: statusPieColors,
  series: [{
    name: '需求状态',
    type: 'pie',
    radius: ['52%', '78%'],
    center: ['50%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 6, borderColor: '#1e293b', borderWidth: 2 },
    label: { show: false },
    emphasis: {
      itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.3)' },
    },
    // 中间总数标注（graphic 实现）
    data: [] as { name: string; value: number }[],
  }],
  graphic: [],
})

// 类型分布柱状图（渐变蓝色，顶部标注数值）
const barLoaded = ref(false)
const barOption = ref<any>({
  tooltip: {
    trigger: 'axis',
    axisPointer: { type: 'shadow' },
    formatter: (params: any[]) => {
      const p = params[0]
      return `${p.name}: <strong>${p.value}</strong>`
    },
  },
  grid: { top: 36, right: 16, bottom: 40, left: 40, containLabel: true },
  xAxis: {
    type: 'category',
    data: [] as string[],
    axisLine: { lineStyle: { color: '#334155' } },
    axisTick: { show: false },
    axisLabel: { color: '#94a3b8', fontSize: 12 },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: '#1e293b', type: 'dashed' } },
    axisLabel: { color: '#94a3b8', fontSize: 12 },
  },
  series: [{
    name: '数量',
    type: 'bar',
    barMaxWidth: 56,
    data: [] as number[],
    itemStyle: {
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#60A5FA' },
          { offset: 1, color: '#2563EB' },
        ],
      },
      borderRadius: [6, 6, 0, 0],
    },
    label: {
      show: true,
      position: 'top',
      color: '#94a3b8',
      fontSize: 12,
      fontWeight: 600,
    },
    emphasis: {
      itemStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#93C5FD' },
            { offset: 1, color: '#3B82F6' },
          ],
        },
      },
    },
  }],
})

// 最近需求
const recentLoading = ref(true)
const recentRequirements = ref<Requirement[]>([])

// 流转动态
interface Activity {
  id: string
  title: string
  userName: string
  time: string
  type: 'success' | 'warning' | 'danger' | 'primary' | 'info'
}
const recentActivities = ref<Activity[]>([])

function getActivityType(type: string): 'success' | 'warning' | 'danger' | 'primary' | 'info' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'primary' | 'info'> = {
    approve: 'success', reject: 'danger', submit: 'primary', rollback: 'warning',
  }
  return map[type] || 'info'
}

// 时长统计
const durationLoading = ref(true)
const durationData = ref<{ stateName: string; avgHours: number; maxHours: number; minHours: number }[]>([])

// 项目进度
const projectRates = ref<{ name: string; rate: number; completed: number; total: number }[]>([])

// 流程处理概览
const workflowStatsLoading = ref(true)
const workflowStats = ref<WorkflowProcessStats>({ pending: 0, processed: 0, initiated: 0, cc: 0 })

async function loadWorkflowStats() {
  workflowStatsLoading.value = true
  try {
    const res = await getWorkflowProcessStats()
    const data = (res as any)?.data ?? (res as any)
    workflowStats.value = {
      pending: data?.pending ?? 0,
      processed: data?.processed ?? 0,
      initiated: data?.initiated ?? 0,
      cc: data?.cc ?? 0,
    }
  } catch {
    workflowStats.value = { pending: 0, processed: 0, initiated: 0, cc: 0 }
  } finally {
    workflowStatsLoading.value = false
  }
}

const workflowStatItems = [
  { key: 'pending',   label: '待办流程', cls: 'is-pending',   route: '/requirements?view=pending' },
  { key: 'processed', label: '已办流程', cls: 'is-processed', route: '/requirements?view=processed' },
  { key: 'initiated', label: '我发起的', cls: 'is-initiated', route: '/requirements?view=initiated' },
  { key: 'cc',        label: '抄送我的', cls: 'is-cc',        route: '/requirements?view=cc' },
]

// 流程处理率（待办/总流程）
const workflowProcessRate = computed(() => {
  const total = workflowStats.value.pending + workflowStats.value.processed
  if (total === 0) return 0
  return Math.round((workflowStats.value.processed / total) * 100)
})

function getProgressColor(rate: number) {
  if (rate >= 80) return COLORS.emeraldHover
  if (rate >= 50) return COLORS.accent
  if (rate >= 30) return COLORS.amberHover
  return COLORS.red
}

function getStatusType(status: string) {
  const map: Record<string, string | undefined> = { '待处理': 'info', '进行中': 'warning', '已完成': 'success', '已关闭': undefined, '已逾期': 'danger' }
  return map[status] ?? 'info'
}

// 优先级配置（来自"需求配置-优先级"，动态加载）
const priorityMap = ref<Record<string, string>>({})
const priorityColorMap = ref<Record<string, string>>({})
const { priorityLabel: renderPriorityLabel, priorityTagStyle } = useRequirementTag()

async function loadPriorityConfig() {
  try {
    const res = await requirementConfigApi.listPriorities()
    const list = Array.isArray(res) ? res : (res as any)?.data || []
    priorityMap.value = Object.fromEntries(list.map((p: any) => [p.code, stripPriorityPrefix(normalizeText(p.name))]))
    priorityColorMap.value = Object.fromEntries(list.filter((p: any) => p.color).map((p: any) => [p.code, p.color]))
  } catch {
    priorityMap.value = {}
    priorityColorMap.value = {}
  }
}

// 优先级 code → 中文显示名（走配置，兜底英文映射）
function getPriorityLabel(priority: string) {
  if (!priority) return '未知'
  const mapped = priorityMap.value[priority] || priorityMap.value[priority.toUpperCase()]
  if (mapped) return mapped
  return renderPriorityLabel(priority, priorityMap.value)
}

function getPriorityType(priority: string) {
  // 配置了 color 时由 :style 接管，type 不传让 ElTag 用默认样式
  if (priorityColorMap.value[priority]) return undefined
  const label = getPriorityLabel(priority)
  const colorMap: Record<string, string | undefined> = {
    '紧急': 'danger',
    '高': 'warning',
    '中': undefined,
    '低': 'info'
  }
  return colorMap[label] ?? 'info'
}

function getPriorityStyle(priority: string) {
  return priorityTagStyle(priorityColorMap.value[priority])
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

    const pieData = Object.entries(statusDist).map(([name, value]) => ({ name, value }))
    const total = pieData.reduce((s, d) => s + d.value, 0)
    pieOption.value.series[0].data = pieData
    // 中间文字：总数 + 标签
    pieOption.value.graphic = [
      {
        type: 'text',
        left: 'center',
        top: '38%',
        style: {
          text: String(total),
          textAlign: 'center',
          fill: '#e2e8f0',
          fontSize: 28,
          fontWeight: 'bold',
          fontFamily: 'Inter, sans-serif',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '55%',
        style: {
          text: '需求总数',
          textAlign: 'center',
          fill: '#94a3b8',
          fontSize: 13,
        },
      },
    ]
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

    // 从最近需求构建流转动态
    recentActivities.value = recentRequirements.value.slice(0, 5).map((item: any) => ({
      id: String(item.id),
      title: `${item.status} · ${item.title?.slice(0, 20) || '需求'}`,
      userName: item.assigneeName || item.creatorName || '系统',
      time: formatDate(item.updatedAt || item.createdAt),
      type: (['已通过', '已完成', '已验收'].includes(item.status) ? 'success'
        : ['已拒绝', '打回'].includes(item.status) ? 'danger'
        : ['评审中', '待评审'].includes(item.status) ? 'primary'
        : ['已取消'].includes(item.status) ? 'warning' : 'info') as Activity['type'],
    }))
  } catch {
    recentRequirements.value = []
    recentActivities.value = []
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
  loadPriorityConfig()
  await loadDashboardData()
  loadDistributionData()
  loadRecentRequirements()
  loadDurationData()
  loadProjectRates()
  loadWorkflowStats()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: var(--spacing-lg);
}

// 欢迎区 + 快捷操作
.dashboard-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
  flex-wrap: wrap;
  gap: var(--spacing-md);

  &__left {
    h2 {
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-primary);
      margin: 0 0 var(--spacing-xs);
      letter-spacing: -0.025em;
    }
  }

  .dashboard-subtitle {
    font-size: var(--font-size-base);
    color: var(--color-muted-text);
  }

  &__actions {
    display: flex;
    gap: var(--spacing-sm);
    flex-shrink: 0;
  }
}

// 统计卡片
.stat-row {
  margin-bottom: var(--spacing-lg);

  .el-col {
    margin-bottom: var(--spacing-md);
  }
}

// 骨架屏
.shimmer {
  background: linear-gradient(90deg, var(--color-surface-alt) 25%, var(--color-border) 50%, var(--color-surface-alt) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

.skeleton-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);

  &__icon {
    width: 52px;
    height: 52px;
    border-radius: var(--radius-md);
  }

  &__info {
    flex: 1;
  }

  &__value {
    height: 28px;
    width: 60%;
    border-radius: var(--radius-sm);
    margin-bottom: var(--spacing-sm);
  }

  &__label {
    height: 14px;
    width: 40%;
    border-radius: var(--radius-sm);
  }
}

.skeleton-chart {
  height: 320px;
  border-radius: var(--radius-md);
}

.skeleton-recent-item {
  padding: var(--spacing-md) 0;

  &:not(:last-child) {
    border-bottom: 1px solid var(--color-border);
  }
}

.skeleton-recent-title {
  height: 16px;
  width: 70%;
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-sm);
}

.skeleton-recent-meta {
  height: 12px;
  width: 40%;
  border-radius: var(--radius-sm);
}

.skeleton-timeline-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
}

.skeleton-timeline-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 4px;
}

.skeleton-timeline-content {
  height: 14px;
  flex: 1;
  border-radius: var(--radius-sm);
}

// 双栏布局
.content-row {
  .el-col {
    margin-bottom: 0;
  }

  .section-card {
    margin-bottom: var(--spacing-lg);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);

    &:last-child {
      margin-bottom: 0;
    }
  }
}

// 区块卡片
.section-card {
  :deep(.el-card__header) {
    padding: var(--spacing-md) var(--spacing-lg);
    cursor: pointer;
    user-select: none;
    border-bottom: 1px solid var(--color-border);
  }

  :deep(.el-card__body) {
    padding: var(--spacing-lg);
  }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.collapse-icon {
  font-size: 14px;
  color: var(--color-muted-text);
  transition: transform var(--transition-fast);
  cursor: pointer;

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

// ── 状态分布：环形图 + 右侧图例 ──────────────────
.status-dist-layout {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding: var(--spacing-md) 0;
}

.status-pie-wrap {
  flex-shrink: 0;
  width: 260px;
  height: 260px;
}

.status-pie-chart {
  width: 100%;
  height: 100%;
}

.status-legend-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  cursor: default;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-name {
  flex: 1;
  color: var(--color-text-secondary);
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.legend-value {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  min-width: 32px;
  text-align: right;
}

.legend-percent {
  color: var(--color-muted-text);
  font-size: var(--font-size-xs);
  min-width: 44px;
  text-align: right;
}

// ── 需求类型分布：渐变柱状图 ──────────────────
.type-bar-chart {
  width: 100%;
  height: 280px;
}

// 最近需求列表
.recent-list {
  max-height: 360px;
  overflow-y: auto;
}

.recent-item {
  padding: var(--spacing-sm) 0;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background-color var(--transition-fast);

  &:not(:last-child) {
    border-bottom: 1px solid var(--color-border);
  }

  &:hover {
    background-color: var(--color-surface-alt);
  }
}

.recent-title {
  font-size: var(--font-size-base);
  color: var(--color-text-primary);
  font-weight: var(--font-weight-medium);
  margin-bottom: var(--spacing-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.recent-date {
  font-size: var(--font-size-xs);
  color: var(--color-muted-text);
  margin-left: auto;
}

// 流转动态
.activity-timeline {
  max-height: 360px;
  overflow-y: auto;
}

.activity-item {
  .activity-title {
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
    font-weight: var(--font-weight-medium);
  }

  .activity-user {
    display: block;
    font-size: var(--font-size-xs);
    color: var(--color-muted-text);
    margin-top: 2px;
  }
}

// 项目进度
.project-progress {
  .progress-item {
    display: flex;
    align-items: center;
    margin-bottom: var(--spacing-md);

    &:last-child {
      margin-bottom: 0;
    }

    .project-name {
      width: 160px;
      font-weight: var(--font-weight-medium);
      flex-shrink: 0;
      color: var(--color-text-secondary);
    }

    .el-progress {
      flex: 1;
      margin: 0 var(--spacing-md);
    }

    .progress-text {
      width: 60px;
      text-align: right;
      color: var(--color-muted-text);
      font-size: var(--font-size-base);
      flex-shrink: 0;
      font-variant-numeric: tabular-nums;
    }
  }
}

// 响应式
@media (max-width: 1024px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

// 流程处理概览
.workflow-overview-card {
  :deep(.el-card__body) {
    padding: var(--spacing-md) var(--spacing-lg);
  }
}

.workflow-overview {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.workflow-circle-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.workflow-circle {
  position: relative;
  width: 110px;
  height: 110px;

  .circle-svg {
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
  }

  .circle-bg {
    fill: none;
    stroke: var(--color-border);
    stroke-width: 10;
  }

  .circle-fill {
    fill: none;
    stroke: #2563EB;
    stroke-width: 10;
    stroke-linecap: round;
    stroke-dashoffset: 0;
    transition: stroke-dasharray 0.6s ease;
  }

  .circle-inner {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;

    .circle-icon {
      font-size: 24px;
      color: #2563EB;
    }

    .circle-label {
      font-size: var(--font-size-xs);
      color: var(--color-muted-text);
      white-space: nowrap;
    }
  }
}

.workflow-stats-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.workflow-stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;

  &.is-clickable {
    cursor: pointer;
    border-radius: var(--radius-sm);
    padding: 4px 6px;
    margin: 0 -6px;
    transition: background-color var(--transition-fast);

    &:hover {
      background-color: var(--color-surface-alt);
    }
  }
}

.workflow-stat-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.workflow-stat-value {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);

  &.is-pending   { color: #2563EB; }
  &.is-processed { color: #059669; }
  &.is-initiated { color: #D97706; }
  &.is-cc        { color: #6366F1; }
}
</style>
