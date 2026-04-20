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

    <!-- 状态分布 + 最近需求 -->
    <el-row :gutter="16" class="content-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>需求状态分布</span>
            </div>
          </template>
          <div class="chart-placeholder" v-loading="distLoading">
            <el-empty v-if="!distributionData || Object.keys(distributionData).length === 0" description="暂无数据" />
            <div v-else class="distribution-list">
              <div v-for="(count, status) in distributionData" :key="status" class="distribution-item">
                <span class="status-name">{{ status }}</span>
                <el-progress
                  :percentage="getPercent(count)"
                  :color="getStatusColor(status)"
                  :stroke-width="12"
                  :show-text="false"
                />
                <span class="status-count">{{ count }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最近需求</span>
            </div>
          </template>
          <div class="recent-list" v-loading="recentLoading">
            <el-empty v-if="recentRequirements.length === 0" description="暂无需求" />
            <div v-for="item in recentRequirements" :key="item.id" class="recent-item">
              <div class="recent-title">{{ item.title }}</div>
              <div class="recent-meta">
                <el-tag :type="getStatusType(item.status)" size="small">{{ item.status }}</el-tag>
                <el-tag :type="getPriorityType(item.priority)" size="small" class="priority-tag">{{ item.priority }}</el-tag>
                <span class="recent-date">{{ formatDate(item.createdAt) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 项目进度概览 -->
    <el-row class="content-row">
      <el-col :span="24">
        <el-card shadow="hover" v-loading="projectLoading">
          <template #header>
            <div class="card-header">
              <span>项目进度概览</span>
            </div>
          </template>
          <el-table :data="projectTable" stripe style="width: 100%" empty-text="暂无项目数据">
            <el-table-column prop="name" label="项目名称" min-width="180" />
            <el-table-column prop="total" label="需求总数" width="120" align="center" />
            <el-table-column prop="completed" label="已完成" width="100" align="center" />
            <el-table-column prop="rate" label="完成率" width="160" align="center">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.rate"
                  :color="row.rate >= 80 ? '#67C23A' : row.rate >= 50 ? '#E6A23C' : '#F56C6C'"
                  :stroke-width="14"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Document, Loading, CircleCheck, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDashboardData, getDistributionData } from '@/api/modules/statistics'
import { getRequirementList } from '@/api/modules/requirement'
import { useUserStore } from '@/stores/modules/user'
import type { Requirement } from '@/types/requirement'

const userStore = useUserStore()

// 统计卡片数据
const statsLoading = ref(true)
const statsData = ref<any>(null)

const statCards = computed(() => [
  {
    icon: Document,
    label: '总需求数',
    value: statsData.value?.totalRequirements ?? 0,
    tip: '全部需求',
    bgColor: '#ecf5ff',
    iconColor: '#409EFF',
  },
  {
    icon: Loading,
    label: '进行中需求',
    value: statsData.value?.inProgress ?? 0,
    tip: '开发中',
    bgColor: '#fdf6ec',
    iconColor: '#E6A23C',
  },
  {
    icon: CircleCheck,
    label: '已完成',
    value: statsData.value?.completed ?? 0,
    tip: '已交付',
    bgColor: '#f0f9eb',
    iconColor: '#67C23A',
  },
  {
    icon: Warning,
    label: '已逾期',
    value: statsData.value?.overdue ?? 0,
    tip: '超过截止日期',
    bgColor: '#fef0f0',
    iconColor: '#F56C6C',
  },
])

// 状态分布
const distLoading = ref(true)
const distributionData = ref<Record<string, number>>({})
const totalDistribution = ref(0)

// 最近需求
const recentLoading = ref(true)
const recentRequirements = ref<Requirement[]>([])

// 项目进度
const projectLoading = ref(true)
const projectTable = ref<any[]>([])

function getPercent(count: number) {
  if (totalDistribution.value === 0) return 0
  return Math.round((count / totalDistribution.value) * 100)
}

function getStatusColor(status: string) {
  const map: Record<string, string> = {
    '待处理': '#909399',
    '进行中': '#E6A23C',
    '已完成': '#67C23A',
    '已关闭': '#409EFF',
    '已逾期': '#F56C6C',
  }
  return map[status] || '#909399'
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    '待处理': 'info',
    '进行中': 'warning',
    '已完成': 'success',
    '已关闭': '',
    '已逾期': 'danger',
  }
  return (map[status] || 'info') as any
}

function getPriorityType(priority: string) {
  const map: Record<string, string> = {
    '紧急': 'danger',
    '高': 'warning',
    '中': '',
    '低': 'info',
  }
  return (map[priority] || 'info') as any
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

async function loadDashboardData() {
  statsLoading.value = true
  try {
    // 尝试从 API 获取，如果没有则使用默认值
    const res = await getDashboardData(1)
    const data = (res as any).data?.data || (res as any).data || {}
    statsData.value = data
  } catch {
    statsData.value = { totalRequirements: 0, inProgress: 0, completed: 0, overdue: 0 }
  } finally {
    statsLoading.value = false
  }
}

async function loadDistributionData() {
  distLoading.value = true
  try {
    const res = await getDistributionData(1)
    const data = (res as any).data?.data || (res as any).data || {}
    const typedData = data as Record<string, number>
    distributionData.value = typedData
    totalDistribution.value = Object.values(typedData).reduce((sum, v) => sum + Number(v), 0)
  } catch {
    distributionData.value = {}
  } finally {
    distLoading.value = false
  }
}

async function loadRecentRequirements() {
  recentLoading.value = true
  try {
    const res = await getRequirementList({
      pageNum: 1,
      pageSize: 5,
      sortField: 'createdAt',
      sortOrder: 'desc',
    })
    const data = (res as any).data?.data || (res as any).data || {}
    recentRequirements.value = data.list || []
  } catch {
    recentRequirements.value = []
  } finally {
    recentLoading.value = false
  }
}

async function loadProjectTable() {
  projectLoading.value = true
  try {
    // 这里假设有一个项目列表接口，暂时使用模拟数据
    projectTable.value = [
      { name: '需求管理系统 v1.0', total: 0, completed: 0, rate: 0 },
    ]
    // 尝试从需求列表统计
    const res = await getRequirementList({
      pageNum: 1,
      pageSize: 1000,
    })
    const data = (res as any).data?.data || (res as any).data || {}
    const list: Requirement[] = data.list || []
    const total = list.length
    const completed = list.filter((r: Requirement) => r.status === '已完成').length
    const rate = total > 0 ? Math.round((completed / total) * 100) : 0
    projectTable.value = [
      { name: '需求管理系统 v1.0', total, completed, rate },
    ]
  } catch {
    projectTable.value = []
  } finally {
    projectLoading.value = false
  }
}

onMounted(() => {
  loadDashboardData()
  loadDistributionData()
  loadRecentRequirements()
  loadProjectTable()
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
  transition: transform 0.2s, box-shadow 0.2s;

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

.content-row {
  margin-bottom: 16px;
}

.card-header {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart-placeholder {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.distribution-list {
  padding: 8px 0;
}

.distribution-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;

  &:not(:last-child) {
    border-bottom: 1px solid #F0F2F5;
  }
}

.status-name {
  min-width: 70px;
  font-size: 13px;
  color: #606266;
}

.status-count {
  min-width: 40px;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.recent-list {
  max-height: 300px;
  overflow-y: auto;
}

.recent-item {
  padding: 12px 0;

  &:not(:last-child) {
    border-bottom: 1px solid #F0F2F5;
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

.priority-tag {
  margin-left: 0;
}

.recent-date {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
</style>
