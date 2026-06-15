<template>
  <PageContainer title="迭代管理">
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left />
          <template #right>
            <AppButton type="primary" permission="button:iteration:create" @click="openDialog()">新建迭代</AppButton>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table :data="iterations" border>
        <el-table-column prop="name" label="迭代名称" min-width="180" />
        <el-table-column label="开始日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column label="结束日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.endDate) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量(人天)" width="110" />
        <el-table-column prop="requirementCount" label="需求数" width="90" />
        <el-table-column label="完成进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :color="getProgressColor(row.progress)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <AppButton link type="primary" permission="button:iteration:update" @click="openDialog(row)"><el-icon><EditPen /></el-icon></AppButton>
            <AppButton link type="danger" permission="button:iteration:delete">
              <el-popconfirm title="确定删除该迭代吗？" @confirm="handleDelete(row)">
                <template #reference>
                  <el-button link type="danger"><el-icon><Delete /></el-icon></el-button>
                </template>
              </el-popconfirm>
            </AppButton>
            <el-tooltip content="查看燃尽图">
              <el-button v-permission="'button:iteration:view'" link type="info" @click="viewBurndown(row)"><el-icon><TrendCharts /></el-icon></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
        </el-table>
      </template>
    </TableCard>

    <!-- 创建/编辑迭代对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑迭代' : '新建迭代'"
      width="500px"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="迭代名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入迭代名称" />
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="容量(人天)" prop="capacity">
          <el-input-number v-model="form.capacity" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入迭代描述" />
        </el-form-item>
        <el-form-item label="关联需求">
          <el-select
            v-model="form.requirementIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            style="width: 100%"
            placeholder="选择需要纳入当前迭代的需求"
          >
            <el-option
              v-for="requirement in requirementOptions"
              :key="requirement.id"
              :label="formatRequirementOption(requirement)"
              :value="requirement.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 燃尽图对话框 -->
    <el-dialog v-model="burndownVisible" title="迭代燃尽图" width="700px">
      <div v-if="burndownData.length > 0" class="burndown-chart">
        <el-descriptions :column="2" border class="mb-4">
          <el-descriptions-item label="迭代名称">{{ currentIteration?.name }}</el-descriptions-item>
          <el-descriptions-item label="日期范围">
            {{ formatDate(currentIteration?.startDate) }} ~ {{ formatDate(currentIteration?.endDate) }}
          </el-descriptions-item>
        </el-descriptions>
        <div ref="burndownChartRef" style="width: 100%; height: 300px"></div>
      </div>
      <el-empty v-else description="暂无燃尽图数据" />
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, nextTick, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { EditPen, Delete, TrendCharts } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import * as echarts from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'

echarts.use([SVGRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent])
import { getIterationList, createIteration, updateIteration, deleteIteration, getBurndownData } from '@/api/modules/iteration'
import { requirementApi } from '@/api'
import type { Iteration, IterationFormData, IterationRequirementOption } from '@/types/iteration'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'
import AppButton from '@/components/common/AppButton.vue'

const route = useRoute()
const projectId = computed(() => {
  const id = Number(route.query.projectId)
  return Number.isFinite(id) && id > 0 ? id : 1
})
const iterations = ref<Iteration[]>([])
const requirementOptions = ref<IterationRequirementOption[]>([])

// 对话框
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = ref<IterationFormData>({
  id: 0,
  name: '',
  description: '',
  startDate: '',
  endDate: '',
  capacity: 0,
  requirementIds: [],
})

const rules = {
  name: [{ required: true, message: '请输入迭代名称', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
}

// 燃尽图
const burndownVisible = ref(false)
const currentIteration = ref<Iteration | null>(null)
const burndownData = ref<{ date: string; remaining: number; completed: number; total: number }[]>([])
const burndownChartRef = ref<HTMLDivElement | null>(null)
let burndownChart: echarts.EChartsType | null = null

// 工具方法
const formatDate = (date: string | undefined) => {
  if (!date) return '-'
  return date.split('T')[0]
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    '未开始': 'info',
    '进行中': 'primary',
    '已完成': 'success',
    '已关闭': 'warning',
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    not_started: '未开始',
    in_progress: '进行中',
    completed: '已完成',
    closed: '已关闭',
  }
  return map[status] || status
}

const getProgressColor = (progress: number | undefined) => {
  if (!progress) return 'var(--color-muted-text)'
  if (progress >= 100) return 'var(--color-success)'
  if (progress >= 60) return 'var(--color-accent)'
  if (progress >= 30) return 'var(--color-warning)'
  return 'var(--color-danger)'
}

// 加载数据
const loadIterations = async () => {
  try {
    iterations.value = (await getIterationList(projectId.value)) as unknown as Iteration[]
  } catch (error) {
    iterations.value = []
    ElMessage.error(resolveErrorMessage(error, '迭代数据加载失败'))
  }
}

const loadRequirementOptions = async () => {
  try {
    const res = await requirementApi.getRequirementList({
      pageNum: 1,
      pageSize: 1000,
      projectId: projectId.value,
    }) as any
    requirementOptions.value = (res?.list || []).map((item: any) => ({
      id: item.id,
      title: item.title,
      type: item.type,
      priority: item.priority,
      status: item.status,
      iterationId: item.iterationId,
    }))
  } catch {
    requirementOptions.value = []
  }
}

// 对话框操作
const openDialog = (row?: Iteration) => {
  isEditing.value = !!row
  if (row) {
    form.value = {
      id: row.id,
      name: row.name,
      description: row.description || '',
      startDate: row.startDate,
      endDate: row.endDate,
      capacity: row.capacity || 0,
      version: row.version,
      requirementIds: requirementOptions.value
        .filter((item) => item.iterationId === row.id)
        .map((item) => item.id),
    }
  } else {
    form.value = { id: 0, name: '', description: '', startDate: '', endDate: '', capacity: 0, requirementIds: [] }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  try {
    if (isEditing.value) {
      await updateIteration(form.value.id || 0, form.value)
      ElMessage.success('迭代更新成功')
    } else {
      await createIteration(projectId.value, form.value)
      ElMessage.success('迭代创建成功')
    }
    dialogVisible.value = false
    await Promise.all([loadIterations(), loadRequirementOptions()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, isEditing.value ? '迭代更新失败' : '迭代创建失败'))
  }
}

const handleDelete = async (row: Iteration) => {
  try {
    await deleteIteration(row.id)
    ElMessage.success('删除成功')
    await Promise.all([loadIterations(), loadRequirementOptions()])
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

const formatRequirementOption = (requirement: IterationRequirementOption) => {
  const planText = requirement.iterationId ? '已排期' : '未排期'
  return `${requirement.title} [${requirement.priority}/${requirement.status}/${planText}]`
}

// 燃尽图
const viewBurndown = async (row: Iteration) => {
  currentIteration.value = row
  burndownVisible.value = true

  try {
    const res = await getBurndownData(row.id) as any
    const payload = res?.data || res
    const rawList = Array.isArray(payload) ? payload : Array.isArray(payload?.series) ? payload.series : Array.isArray(payload?.list) ? payload.list : []
    burndownData.value = rawList.map((item: any) => ({
      date: item.date,
      completed: Number(item.completed ?? item.completedCount ?? 0),
      total: Number(item.total ?? item.totalCount ?? row.requirementCount ?? 0),
      remaining: Number(item.remaining ?? item.remainingCount ?? Math.max(0, Number(item.total ?? item.totalCount ?? row.requirementCount ?? 0) - Number(item.completed ?? item.completedCount ?? 0))),
    }))
  } catch (error) {
    burndownData.value = []
    ElMessage.error(resolveErrorMessage(error, '燃尽图数据加载失败'))
  }

  await nextTick()
  renderBurndownChart()
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const message = (error as any)?.response?.data?.message || (error as any)?.message
  return typeof message === 'string' && message.trim() ? message : fallback
}

const disposeBurndownChart = () => {
  burndownChart?.dispose()
  burndownChart = null
}

const renderBurndownChart = () => {
  const chartDom = burndownChartRef.value
  if (!chartDom) return

  const existingChart = echarts.getInstanceByDom(chartDom)
  burndownChart = existingChart || burndownChart || echarts.init(chartDom)

  burndownChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['理想剩余', '实际剩余', '已完成'] },
    xAxis: {
      type: 'category',
      data: burndownData.value.map((d) => d.date.slice(5)),
    },
    yAxis: { type: 'value', name: '需求数' },
    series: [
      {
        name: '理想剩余',
        type: 'line',
        data: burndownData.value.map((d, i) =>
          Math.max(0, d.total - Math.round(d.total * (i / Math.max(burndownData.value.length - 1, 1)))),
        ),
        lineStyle: { type: 'dashed' },
      },
      { name: '实际剩余', type: 'line', data: burndownData.value.map((d) => d.remaining) },
      { name: '已完成', type: 'bar', data: burndownData.value.map((d) => d.completed) },
    ],
  }, true)
}

const resizeBurndownChart = () => {
  burndownChart?.resize()
}

watch(burndownVisible, (visible) => {
  if (visible) {
    nextTick(() => {
      resizeBurndownChart()
    })
    return
  }

  disposeBurndownChart()
})

onMounted(() => {
  window.addEventListener('resize', resizeBurndownChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeBurndownChart)
  disposeBurndownChart()
})

watch(projectId, () => {
  loadIterations()
  loadRequirementOptions()
}, { immediate: true })
</script>

<style scoped lang="scss">
.burndown-chart {
  .mb-4 {
    margin-bottom: 16px;
  }
}
</style>
