<template>
  <div class="dashboard-container">
    <!-- 科技感背景装饰 -->
    <div class="tech-bg">
      <div class="tech-bg__grid" />
      <div class="tech-bg__glow tech-bg__glow--1" />
      <div class="tech-bg__glow tech-bg__glow--2" />
    </div>

    <!-- 欢迎区 + 快捷操作 -->
    <div class="dashboard-header">
      <div class="dashboard-header__left">
        <h2 class="dashboard-title">
          <span class="dashboard-title__greeting">{{ getGreeting() }}，</span>
          <span class="dashboard-title__name">{{ userStore.userInfo?.realName || '用户' }}</span>
        </h2>
        <p class="dashboard-desc">今日{{ getCurrentDate() }}，{{ getWeekDay() }}，祝工作顺利 🚀</p>
      </div>
      <div class="dashboard-header__actions">
        <AppButton type="primary" permission="button:requirement:create" @click="router.push('/requirements/create')">
          <el-icon><Document /></el-icon>新建需求
        </AppButton>
        <el-button class="btn-tech" @click="router.push('/requirements?view=pending')">
          <el-icon><List /></el-icon>我的待办
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
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
            @click="router.push(card.route)"
          >
            <template #icon>
              <component :is="card.icon" />
            </template>
          </StatCardPro>
        </el-col>
      </template>
    </el-row>

    <!-- 双栏布局：图表 + 最近动态 -->
    <el-row :gutter="20" class="content-row">
      <!-- 左栏：趋势图表 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>需求状态分布</span>
              </div>
              <el-icon class="collapse-icon" :class="{ 'is-expanded': expandedSections.statusDist }" @click="toggleSection('statusDist')"><ArrowRight /></el-icon>
            </div>
          </template>
          <div v-show="expandedSections.statusDist" class="section-body">
            <template v-if="distLoading">
              <div class="skeleton-chart shimmer" style="height:180px" />
            </template>
            <template v-else-if="pieLoaded">
      <!-- 精美环形图 + 右侧图例 -->
      <div class="pie-section">
        <!-- 左侧环形图 -->
        <div class="pie-section__chart">
          <v-chart
            ref="pieChartRef"
            :option="pieOption"
            :init-options="chartInitOptions"
            class="pie-chart"
            autoresize
            @mouseover="onPieHover"
            @mouseout="onPieUnhover"
          />
        </div>
        <!-- 右侧图例列表 -->
        <div class="pie-section__legend">
          <div class="pie-legend-item pie-legend-item--header">
            <span class="pie-legend-color" />
            <span class="pie-legend-name">状态</span>
            <span class="pie-legend-value">个数</span>
            <span class="pie-legend-pct">占比</span>
          </div>
          <div
            v-for="(item, idx) in pieOption.series[0].data"
            :key="item.name"
            class="pie-legend-item"
            :class="{ 'is-active': hoveredSlice === item.name }"
            @mouseenter="onLegendHover(item.name)"
            @mouseleave="onLegendUnhover"
            @click="onLegendClick(item.name)"
          >
            <span class="pie-legend-color" :style="{ background: statusPieColors[idx % statusPieColors.length] }" />
            <span class="pie-legend-name">{{ item.name }}</span>
            <span class="pie-legend-value">{{ item.value }}</span>
            <span class="pie-legend-pct" :style="{ color: statusPieColors[idx % statusPieColors.length] }">
              {{ pieTotalCount > 0 ? ((item.value / pieTotalCount) * 100).toFixed(1) + '%' : '0%' }}
            </span>
          </div>
          <!-- 合计 -->
          <div class="pie-legend-item pie-legend-item--total">
            <span class="pie-legend-color" />
            <span class="pie-legend-name">合计</span>
            <span class="pie-legend-value">{{ pieTotalCount }}</span>
            <span class="pie-legend-pct">100%</span>
          </div>
        </div>
      </div>
    </template>
            <el-empty v-else description="暂无数据" />
          </div>
        </el-card>

        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>需求类型分布</span>
              </div>
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

        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>需求时长统计</span>
              </div>
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

      <!-- 右栏 -->
      <el-col :xs="24" :lg="8">
        <!-- 流程处理概览 -->
        <el-card shadow="never" class="section-card tech-card workflow-overview-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot section-dot--accent" />
                <span>流程处理概览</span>
              </div>
            </div>
          </template>
          <div class="workflow-overview">
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
                  <span class="circle-rate">{{ workflowProcessRate }}%</span>
                  <span class="circle-label">处理率</span>
                </div>
              </div>
            </div>
            <div class="workflow-stats-list">
              <div
                v-for="item in workflowStatItems"
                :key="item.key"
                class="workflow-stat-item"
                @click="item.route && router.push(item.route)"
                :class="{ 'is-clickable': !!item.route }"
              >
                <div class="workflow-stat-left">
                  <span class="workflow-stat-dot" :class="item.cls" />
                  <span class="workflow-stat-label">{{ item.label }}</span>
                </div>
                <span class="workflow-stat-value" :class="item.cls">
                  <template v-if="workflowStatsLoading">—</template>
                  <template v-else>{{ workflowStats[item.key as keyof typeof workflowStats] }}</template>
                </span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 最近需求 -->
        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>最近需求</span>
              </div>
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
        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>流转动态</span>
              </div>
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
        <el-card shadow="never" class="section-card tech-card">
          <template #header>
            <div class="section-header">
              <div class="section-header__left">
                <span class="section-dot" />
                <span>项目进度</span>
              </div>
            </div>
          </template>
          <el-empty v-if="projectRates.length === 0" description="暂无项目数据" :image-size="60" />
          <div v-else class="project-progress">
            <div v-for="p in projectRates" :key="p.name" class="progress-item">
              <div class="progress-header">
                <span class="project-name">{{ p.name }}</span>
                <span class="progress-text">{{ p.completed }}/{{ p.total }}</span>
              </div>
              <el-progress
                :percentage="p.rate"
                :color="getProgressColor(p.rate)"
                :stroke-width="12"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, h, defineComponent, onMounted } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { Document, List, ArrowRight } from '@element-plus/icons-vue'
import StatCardPro from '@/components/common/StatCardPro.vue'
import AppButton from '@/components/common/AppButton.vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, GraphicComponent } from 'echarts/components'
import { getDashboardData, getDistributionData, getDurationData, getWorkflowProcessStats, getEndNodeStatuses } from '@/api/modules/statistics'
import type { WorkflowProcessStats } from '@/api/modules/statistics'
import { getRequirementList } from '@/api/modules/requirement'
import { useUserStore } from '@/stores/modules/user'
import { formatDate, stripPriorityPrefix, normalizeText } from '@/utils/format'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { useRequirementTag } from '@/composables/useRequirementTag'
import type { Requirement } from '@/types/requirement'

use([SVGRenderer, PieChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, GraphicComponent])

const router = useRouter()
const chartInitOptions = { renderer: 'svg' as const }
const userStore = useUserStore()
const pieChartRef = ref<any>(null)

// ═══════════════════════════════════════════════
// 自定义 SVG 图标（替代 Element Plus 默认图标）
// ═══════════════════════════════════════════════

/**
 * 总需求数图标 — 分层文档 + 图表
 */
const SvgIconTotal = defineComponent({
  setup() {
    return () => h('svg', { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none' }, [
      h('rect', { x: 3, y: 2, width: 18, height: 20, rx: 3, stroke: '#fff', 'stroke-width': 1.8, opacity: 0.9 }),
      h('path', { d: 'M7 8h10M7 12h6M7 16h8', stroke: '#fff', 'stroke-width': 1.8, 'stroke-linecap': 'round', opacity: 0.7 }),
      h('rect', { x: 14, y: 6, width: 7, height: 7, rx: 1.5, stroke: '#fff', 'stroke-width': 1.5, opacity: 0.5 }),
    ])
  }
})

/**
 * 进行中图标 — 动态旋转弧线
 */
const SvgIconProgress = defineComponent({
  setup() {
    return () => h('svg', { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none' }, [
      h('circle', { cx: 12, cy: 12, r: 8, stroke: '#fff', 'stroke-width': 1.8, 'stroke-dasharray': '14 36', 'stroke-linecap': 'round', opacity: 0.9 }),
      h('circle', { cx: 12, cy: 12, r: 8, stroke: '#fff', 'stroke-width': 1.8, 'stroke-dasharray': '8 42', 'stroke-linecap': 'round', opacity: 0.4 }),
    ])
  }
})

/**
 * 已完成图标 — 对勾 + 背景圆
 */
const SvgIconDone = defineComponent({
  setup() {
    return () => h('svg', { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none' }, [
      h('circle', { cx: 12, cy: 12, r: 9, stroke: '#fff', 'stroke-width': 1.8, opacity: 0.9 }),
      h('path', { d: 'M7.5 12.5L10.5 15.5L16.5 9.5', stroke: '#fff', 'stroke-width': 2.2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', opacity: 0.9 }),
    ])
  }
})

/**
 * 已逾期图标 — 三角形警示
 */
const SvgIconAlert = defineComponent({
  setup() {
    return () => h('svg', { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none' }, [
      h('path', { d: 'M12 2L22 20H2L12 2Z', stroke: '#fff', 'stroke-width': 1.8, 'stroke-linejoin': 'round', opacity: 0.9 }),
      h('line', { x1: 12, y1: 9, x2: 12, y2: 14, stroke: '#fff', 'stroke-width': 2.2, 'stroke-linecap': 'round', opacity: 0.9 }),
      h('circle', { cx: 12, cy: 17.5, r: 1.2, fill: '#fff', opacity: 0.9 }),
    ])
  }
})

function getGreeting() {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function getCurrentDate() {
  const now = new Date()
  return `${now.getMonth() + 1}月${now.getDate()}日`
}

function getWeekDay() {
  const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return days[new Date().getDay()]
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

// 已完成卡片路由：先获取结束状态列表，再构造带 nodeStatus 过滤的路由
const endNodeStatusCodes = ref<string[]>([])
const completedRoute = computed<RouteLocationRaw>(() => {
  if (endNodeStatusCodes.value.length > 0) {
    return { name: 'Requirements', query: { nodeStatus: endNodeStatusCodes.value.join(',') } }
  }
  // 兜底：先跳过去，前端 fetchData 会自动适配
  return { name: 'Requirements', query: { view: 'done' } }
})

// 加载结束状态码列表
async function loadEndNodeStatuses() {
  try {
    const res = await getEndNodeStatuses()
    const codes = (res as any)?.data ?? res ?? []
    endNodeStatusCodes.value = Array.isArray(codes) ? codes : []
  } catch {
    endNodeStatusCodes.value = []
  }
}

const statCardsPro = computed(() => [
  { icon: SvgIconTotal, label: '总需求数', value: statsData.value?.totalReqs ?? 0, tip: '全部需求', gradientStart: COLORS.accent, gradientEnd: COLORS.accentHover, trend: null, route: { name: 'Requirements' } },
  { icon: SvgIconProgress, label: '进行中需求', value: statsData.value?.inProgressReqs ?? 0, tip: '开发中', gradientStart: COLORS.amber, gradientEnd: COLORS.amberHover, trend: null, route: { name: 'Requirements', query: { nodeStatus: 'IN_DEVELOPMENT' } } },
  { icon: SvgIconDone, label: '已完成', value: statsData.value?.completedReqs ?? 0, tip: '已交付', gradientStart: COLORS.emerald, gradientEnd: COLORS.emeraldHover, trend: null, route: completedRoute.value },
  { icon: SvgIconAlert, label: '已逾期', value: statsData.value?.overdueReqs ?? 0, tip: '超过截止日期', gradientStart: COLORS.red, gradientEnd: COLORS.redHover, trend: null, route: { name: 'Requirements', query: { isOverdue: 'true' } } },
])

// 状态分布饼图
const distLoading = ref(true)
const pieLoaded = ref(false)
const hoveredSlice = ref<string | null>(null)

// 饼图配色（精致蓝色系 + 辅助色）
const statusPieColors = [
  '#4F8EF7', // 待处理 - 亮蓝
  '#F59E0B', // 进行中 - 琥珀
  '#10B981', // 已完成 - 翠绿
  '#EF4444', // 已逾期 - 红
  '#8B5CF6', // 已关闭 - 紫
  '#06B6D4', // 其他1 - 青
  '#F97316', // 其他2 - 橙
  '#6366F1', // 其他3 - indigo
]

const pieTotalCount = computed(() =>
  (pieOption.value.series[0].data as { name: string; value: number }[]).reduce((s, d) => s + d.value, 0)
)

const pieOption = ref<any>({
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(15, 23, 42, 0.92)',
    borderColor: 'rgba(71, 85, 105, 0.4)',
    borderWidth: 1,
    padding: [10, 14],
    textStyle: { color: '#e2e8f0', fontSize: 13 },
    formatter: (params: any) => {
      const p = params
      const total = pieTotalCount.value
      const pct = total > 0 ? ((p.value / total) * 100).toFixed(1) : '0'
      return `<div style="display:flex;align-items:center;gap:8px;">
        <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color};box-shadow:0 0 6px ${p.color};"></span>
        <strong>${p.name}</strong>
        <span style="margin-left:auto;font-variant-numeric:tabular-nums;">${p.value} <span style="opacity:0.6;font-size:11px;">(${pct}%)</span></span>
      </div>`
    },
  },
  color: statusPieColors,
  series: [{
    name: '需求状态',
    type: 'pie',
    radius: ['50%', '76%'],
    center: ['50%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: {
      borderRadius: 6,
      borderColor: '#0f172a',
      borderWidth: 2.5,
    },
    label: { show: false },
    emphasis: {
      scale: true,
      scaleSize: 8,
      itemStyle: {
        shadowBlur: 20,
        shadowOffsetX: 0,
        shadowColor: 'rgba(0,0,0,0.3)',
      },
    },
    data: [] as { name: string; value: number }[],
  }],
})

function onPieHover(params: any) {
  hoveredSlice.value = params?.name || null
}

function onPieUnhover() {
  hoveredSlice.value = null
}

// 高亮/取消高亮饼图扇形
function highlightSlice(name: string) {
  const inst = pieChartRef.value?.getEchartsInstance?.()
  if (inst) inst.dispatchAction({ type: 'highlight', name })
}
function downplaySlice(name: string) {
  const inst = pieChartRef.value?.getEchartsInstance?.()
  if (inst) inst.dispatchAction({ type: 'downplay', name })
}

function onLegendHover(name: string) {
  hoveredSlice.value = name
  highlightSlice(name)
}

function onLegendUnhover() {
  const prev = hoveredSlice.value
  hoveredSlice.value = null
  if (prev) downplaySlice(prev)
}

function onLegendClick(name: string) {
  // 可选：点击图例筛选
}

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
    // 中心文字：总数 + 标签（graphic 实现）
    // 注意：ECharts 5 不会解析 CSS 变量，必须使用扎实色值
    pieOption.value.graphic = [
      {
        type: 'text',
        left: 'center',
        top: '36%',
        style: {
          text: String(total),
          textAlign: 'center',
          fill: '#f1f5f9',
          fontSize: 32,
          fontWeight: 'bold',
          fontFamily: 'Inter, system-ui, sans-serif',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '54%',
        style: {
          text: '需求总数',
          textAlign: 'center',
          fill: '#64748b',
          fontSize: 13,
          fontFamily: 'Inter, system-ui, sans-serif',
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
  loadEndNodeStatuses()
})
</script>

<style scoped lang="scss">
// ═══════════════════════════════════════════════
// 科技感仪表盘样式
// ═══════════════════════════════════════════════

.dashboard-container {
  padding: 24px;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

// ── 科技感背景 ────────────────────────────────
.tech-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;

  &__grid {
    position: absolute;
    inset: 0;
    background-image:
      radial-gradient(circle, var(--color-border) 1px, transparent 1px);
    background-size: 32px 32px;
    opacity: 0.4;
  }

  &__glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(120px);
    opacity: 0.15;
    pointer-events: none;

    &--1 {
      width: 600px;
      height: 600px;
      background: var(--color-accent, #2563EB);
      top: -200px;
      right: -100px;
      animation: glow-drift 20s ease-in-out infinite alternate;
    }

    &--2 {
      width: 500px;
      height: 500px;
      background: var(--color-accent-hover, #6366F1);
      bottom: -150px;
      left: -100px;
      animation: glow-drift 25s ease-in-out infinite alternate-reverse;
    }
  }
}

@keyframes glow-drift {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(60px, 40px) scale(1.15); }
}

// ── 欢迎区 ────────────────────────────────────
.dashboard-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 20px;

  &__left {
    h2 {
      margin: 0 0 6px;
      letter-spacing: -0.03em;
    }
  }
}

// ── 仪表盘标题 ─────────────────────────────────
.dashboard-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.03em;
  line-height: 1.3;

  &__greeting {
    font-weight: 500;
    color: var(--color-text-secondary);
    font-size: 20px;
  }

  &__name {
    background: linear-gradient(135deg, var(--color-accent, #2563EB), var(--color-accent-hover, #6366F1));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.dashboard-desc {
  font-size: 13px;
  color: var(--color-muted-text);
  margin: 0;
}

.dashboard-header__actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;

  .btn-tech {
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-text-secondary);
    transition: all 0.2s ease;

    &:hover {
      border-color: var(--color-accent, #2563EB);
      color: var(--color-accent, #2563EB);
      background: rgba(37, 99, 235, 0.04);
    }
  }
}

// ── 统计卡片行 ─────────────────────────────────
.stat-row {
  position: relative;
  z-index: 1;
  margin-bottom: 20px;

  .el-col {
    margin-bottom: 16px;
  }
}

// ── 骨架屏 ────────────────────────────────────
.shimmer {
  background: linear-gradient(90deg, var(--color-surface-alt) 25%, var(--color-border) 50%, var(--color-surface-alt) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.skeleton-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;

  &__icon {
    width: 52px;
    height: 52px;
    border-radius: 10px;
  }

  &__info { flex: 1; }

  &__value {
    height: 28px;
    width: 60%;
    border-radius: 6px;
    margin-bottom: 8px;
  }

  &__label {
    height: 14px;
    width: 40%;
    border-radius: 6px;
  }
}

.skeleton-chart {
  height: 320px;
  border-radius: 10px;
}

.skeleton-recent-item {
  padding: 14px 0;
  &:not(:last-child) { border-bottom: 1px solid var(--color-border); }
}

.skeleton-recent-title {
  height: 16px;
  width: 70%;
  border-radius: 6px;
  margin-bottom: 8px;
}

.skeleton-recent-meta {
  height: 12px;
  width: 40%;
  border-radius: 6px;
}

.skeleton-timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
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
  border-radius: 6px;
}

// ── 内容区双栏 ─────────────────────────────────
.content-row {
  position: relative;
  z-index: 1;

  .el-col { margin-bottom: 0; }
}

// ── 科技感卡片 ─────────────────────────────────
.tech-card {
  border: 1px solid var(--color-border) !important;
  border-radius: 12px !important;
  background: var(--color-surface) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: visible !important;

  // 顶部装饰线
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 20px;
    right: 20px;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--color-accent, #2563EB), transparent);
    opacity: 0;
    transition: opacity 0.3s ease;
    border-radius: 0 0 2px 2px;
  }

  &:hover {
    border-color: rgba(37, 99, 235, 0.3) !important;
    box-shadow:
      0 4px 24px rgba(0, 0, 0, 0.08),
      0 0 0 1px rgba(37, 99, 235, 0.05),
      0 0 40px rgba(37, 99, 235, 0.04);

    &::before { opacity: 1; }
  }

  :deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid var(--color-border);
    background: transparent;
  }

  :deep(.el-card__body) {
    padding: 20px;
  }
}

// ── 区块头部 ───────────────────────────────────
.section-card {
  margin-bottom: 16px;

  &:last-child { margin-bottom: 0; }
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);

  &__left {
    display: flex;
    align-items: center;
    gap: 10px;
  }
}

.section-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent, #2563EB);
  position: relative;
  flex-shrink: 0;

  &::after {
    content: '';
    position: absolute;
    inset: -3px;
    border-radius: 50%;
    background: var(--color-accent, #2563EB);
    opacity: 0.2;
    animation: dot-pulse 2s ease-in-out infinite;
  }

  &--accent {
    background: var(--color-accent-hover, #6366F1);

    &::after {
      background: var(--color-accent-hover, #6366F1);
    }
  }
}

@keyframes dot-pulse {
  0%, 100% { transform: scale(1); opacity: 0.2; }
  50% { transform: scale(1.8); opacity: 0; }
}

.collapse-icon {
  font-size: 14px;
  color: var(--color-muted-text);
  transition: transform 0.2s ease;
  cursor: pointer;

  &.is-expanded {
    transform: rotate(90deg);
  }
}

.section-body {
  padding: 0;
}

// ── 需求状态分布：精美环形图 + 图例 ───
.pie-section {
  display: flex;
  align-items: center;
  gap: 36px;
  padding: 8px 0;
  min-height: 260px;

  &__chart {
    flex-shrink: 0;
    width: 260px;
    height: 260px;
    position: relative;
  }
}

.pie-chart {
  width: 100%;
  height: 100%;
}

.pie-section__legend {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

// 图例行
.pie-legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: default;
  transition: all 0.2s ease;

  &:not(.pie-legend-item--header):not(.pie-legend-item--total):hover {
    background: var(--color-surface-alt, rgba(30, 41, 59, 0.5));
  }

  &.is-active {
    background: rgba(37, 99, 235, 0.08);
    box-shadow: inset 3px 0 0 var(--color-accent, #2563EB);
  }

  &--header {
    padding: 0 12px 10px;
    border-bottom: 1px solid var(--color-border, #1e293b);
    margin-bottom: 6px;
    cursor: default;

    .pie-legend-name,
    .pie-legend-value,
    .pie-legend-pct {
      font-size: 11px;
      font-weight: 600;
      color: var(--color-muted-text, #64748b);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
  }

  &--total {
    padding: 12px 12px 4px;
    margin-top: 6px;
    border-top: 1px dashed var(--color-border, #1e293b);
    cursor: default;

    .pie-legend-name {
      font-weight: 600;
      color: var(--color-text-secondary, #94a3b8);
    }

    .pie-legend-value {
      font-weight: 700;
      color: var(--color-text-primary, #e2e8f0);
    }

    .pie-legend-pct {
      font-weight: 600;
      color: var(--color-text-primary, #e2e8f0);
    }
  }
}

.pie-legend-color {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: box-shadow 0.2s ease;
  box-shadow: 0 0 0 transparent;

  .pie-legend-item.is-active & {
    box-shadow: 0 0 8px currentColor;
  }
}

.pie-legend-name {
  width: 72px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary, #94a3b8);
  flex-shrink: 0;
  transition: color 0.2s ease;

  .pie-legend-item.is-active & {
    color: var(--color-text-primary, #e2e8f0);
    font-weight: 600;
  }
}

.pie-legend-value {
  flex: 1;
  text-align: right;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary, #e2e8f0);
  font-variant-numeric: tabular-nums;
  transition: transform 0.2s ease;

  .pie-legend-item.is-active & {
    transform: scale(1.05);
  }
}

.pie-legend-pct {
  width: 56px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  transition: transform 0.2s ease;
}

// ── 柱状图 ─────────────────────────────────────
.type-bar-chart {
  width: 100%;
  height: 280px;
}

// ── 最近需求列表 ────────────────────────────────
.recent-list {
  max-height: 360px;
  overflow-y: auto;
}

.recent-item {
  padding: 12px 10px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.15s ease;

  &:not(:last-child) {
    border-bottom: 1px solid var(--color-border);
  }

  &:hover {
    background: var(--color-surface-alt);
    transform: translateX(4px);
  }
}

.recent-title {
  font-size: 14px;
  color: var(--color-text-primary);
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
  color: var(--color-muted-text);
  margin-left: auto;
}

// ── 流转动态 ────────────────────────────────────
.activity-timeline {
  max-height: 360px;
  overflow-y: auto;
}

.activity-item {
  .activity-title {
    font-size: 13px;
    color: var(--color-text-primary);
    font-weight: 500;
  }

  .activity-user {
    display: block;
    font-size: 12px;
    color: var(--color-muted-text);
    margin-top: 2px;
  }
}

// ── 项目进度 ────────────────────────────────────
.project-progress {
  .progress-item {
    margin-bottom: 18px;

    &:last-child { margin-bottom: 0; }
  }
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.project-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-text {
  font-size: 13px;
  color: var(--color-muted-text);
  font-variant-numeric: tabular-nums;
  margin-left: 12px;
  flex-shrink: 0;
}

// ── 流程处理概览 ────────────────────────────────
.workflow-overview-card {
  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

.workflow-overview {
  display: flex;
  align-items: center;
  gap: 24px;
}

.workflow-circle-wrap {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.workflow-circle {
  position: relative;
  width: 120px;
  height: 120px;

  .circle-svg {
    width: 100%;
    height: 100%;
    transform: rotate(-90deg);
  }

  .circle-bg {
    fill: none;
    stroke: var(--color-border);
    stroke-width: 8;
  }

  .circle-fill {
    fill: none;
    stroke: var(--color-accent, #2563EB);
    stroke-width: 8;
    stroke-linecap: round;
    stroke-dashoffset: 0;
    transition: stroke-dasharray 0.8s cubic-bezier(0.4, 0, 0.2, 1);
    filter: drop-shadow(0 0 6px rgba(37, 99, 235, 0.3));
  }

  .circle-inner {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;

    .circle-icon {
      font-size: 22px;
      color: var(--color-accent, #2563EB);
    }

    .circle-rate {
      font-size: 20px;
      font-weight: 700;
      color: var(--color-text-primary);
      font-variant-numeric: tabular-nums;
    }

    .circle-label {
      font-size: 11px;
      color: var(--color-muted-text);
      white-space: nowrap;
    }
  }
}

.workflow-stats-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.workflow-stat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 8px;
  transition: background-color 0.15s ease;

  &.is-clickable {
    cursor: pointer;

    &:hover {
      background: var(--color-surface-alt);
    }
  }
}

.workflow-stat-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.is-pending   { background: #2563EB; box-shadow: 0 0 6px rgba(37, 99, 235, 0.4); }
  &.is-processed { background: #059669; box-shadow: 0 0 6px rgba(5, 150, 105, 0.4); }
  &.is-initiated { background: #D97706; box-shadow: 0 0 6px rgba(217, 119, 6, 0.4); }
  &.is-cc        { background: #6366F1; box-shadow: 0 0 6px rgba(99, 102, 241, 0.4); }
}

.workflow-stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.workflow-stat-value {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-primary);

  &.is-pending   { color: #2563EB; }
  &.is-processed { color: #059669; }
  &.is-initiated { color: #D97706; }
  &.is-cc        { color: #6366F1; }
}

// ── 响应式 ────────────────────────────────────
@media (max-width: 1024px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .pie-section {
    flex-direction: column;
    align-items: stretch;
    gap: 20px;

    &__chart {
      width: 220px;
      height: 220px;
      margin: 0 auto;
    }
  }
}

@media (max-width: 640px) {
  .dashboard-container {
    padding: 16px;
  }

  .dashboard-title {
    font-size: 20px;

    &__greeting { font-size: 16px; }
  }

  .pie-section__chart {
    width: 180px;
    height: 180px;
  }
}
</style>

