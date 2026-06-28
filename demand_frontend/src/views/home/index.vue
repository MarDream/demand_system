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
            <div class="chart-box">
              <template v-if="distLoading">
                <div class="skeleton-chart shimmer" />
              </template>
              <v-chart v-else-if="pieLoaded" :option="pieOption" :init-options="chartInitOptions" class="chart" autoresize />
              <el-empty v-else description="暂无数据" />
            </div>
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
            <div class="chart-box">
              <template v-if="distLoading">
                <div class="skeleton-chart shimmer" />
              </template>
              <v-chart v-else-if="barLoaded" :option="barOption" :init-options="chartInitOptions" class="chart" autoresize />
              <el-empty v-else description="暂无数据" />
            </div>
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

      <!-- 右栏：最近需求 + 流转历史 + 项目进度 -->
      <el-col :xs="24" :lg="8">
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
import { getDashboardData, getDistributionData, getDurationData } from '@/api/modules/statistics'
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
const pieOption = ref({
  tooltip: { trigger: 'item' },
  legend: { top: '5%', left: 'center' },
  series: [{
    name: '需求状态',
    type: 'pie',
    radius: ['40%', '70%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 10, borderColor: 'var(--color-on-primary)', borderWidth: 2 },
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
    itemStyle: { color: COLORS.accent },
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

function getProgressColor(rate: number) {
  if (rate >= 80) return COLORS.emeraldHover
  if (rate >= 50) return COLORS.accent
  if (rate >= 30) return COLORS.amberHover
  return COLORS.red
}

function getStatusType(status: string) {
  const map: Record<string, string> = { '待处理': 'info', '进行中': 'warning', '已完成': 'success', '已关闭': '', '已逾期': 'danger' }
  return (map[status] || 'info') as any
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
  // 配置了 color 时由 :style 接管，type 仅作无 color 时的兜底
  if (priorityColorMap.value[priority]) return '' as any
  const label = getPriorityLabel(priority)
  const colorMap: Record<string, string> = {
    '紧急': 'danger',
    '高': 'warning',
    '中': '',
    '低': 'info'
  }
  return (colorMap[label] || 'info') as any
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

// 需求类型英文转中文映射
function getTypeLabel(type: string): string {
  const typeMap: Record<string, string> = {
    'feature': '功能需求',
    'bug': '缺陷',
    'improvement': '优化改进',
    'enhancement': '功能增强',
    'task': '任务',
    'story': '用户故事',
    'research': '研究',
    'test': '测试',
    'document': '文档',
    'order': '工单',
    'requirement': '需求',
    'other': '其他'
  }
  return typeMap[type.toLowerCase()] || type
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

    barOption.value.xAxis.data = Object.keys(typeDist).map(type => getTypeLabel(type))
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
</style>
