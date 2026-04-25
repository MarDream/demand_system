<template>
  <PageContainer title="迭代管理">
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left />
          <template #right>
            <el-button type="primary" @click="openDialog()">新建迭代</el-button>
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
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该迭代吗？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
            <el-button link type="info" @click="viewBurndown(row)">查看燃尽图</el-button>
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
        <div id="burndown-chart" style="width: 100%; height: 300px"></div>
      </div>
      <el-empty v-else description="暂无燃尽图数据" />
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import * as echarts from 'echarts'
import { getIterationList, createIteration, updateIteration, deleteIteration } from '@/api/modules/iteration'
import type { Iteration } from '@/types/iteration'
import PageContainer from '@/components/common/PageContainer.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const projectId = 1
const iterations = ref<Iteration[]>([])

// 对话框
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = ref({
  id: 0,
  name: '',
  description: '',
  startDate: '',
  endDate: '',
  capacity: 0,
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
  if (!progress) return '#909399'
  if (progress >= 100) return '#67C23A'
  if (progress >= 60) return '#409EFF'
  if (progress >= 30) return '#E6A23C'
  return '#F56C6C'
}

// 加载数据
const loadIterations = async () => {
  try {
    iterations.value = (await getIterationList(projectId)) as unknown as Iteration[]
  } catch {
    ElMessage.warning('迭代数据加载失败，使用模拟数据')
    const now = new Date()
    iterations.value = [
      {
        id: 1, projectId: 1, name: 'Sprint 1', description: '第一个迭代',
        startDate: '2024-03-01', endDate: '2024-03-15', capacity: 20,
        status: 'completed', creatorId: 1, createdAt: '2024-02-25',
        requirementCount: 8, progress: 100,
      },
      {
        id: 2, projectId: 1, name: 'Sprint 2', description: '第二个迭代',
        startDate: '2024-03-18', endDate: '2024-04-01', capacity: 25,
        status: 'in_progress', creatorId: 1, createdAt: '2024-03-15',
        requirementCount: 10, progress: 65,
      },
      {
        id: 3, projectId: 1, name: 'Sprint 3', description: '第三个迭代',
        startDate: '2024-04-05', endDate: '2024-04-20', capacity: 30,
        status: 'not_started', creatorId: 1, createdAt: '2024-04-01',
        requirementCount: 12, progress: 0,
      },
    ]
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
    }
  } else {
    form.value = { id: 0, name: '', description: '', startDate: '', endDate: '', capacity: 0 }
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  try {
    if (isEditing.value) {
      await updateIteration(form.value.id, form.value)
      ElMessage.success('迭代更新成功')
    } else {
      await createIteration(projectId, form.value)
      ElMessage.success('迭代创建成功')
    }
    dialogVisible.value = false
    loadIterations()
  } catch {
    ElMessage.success(isEditing.value ? '迭代更新成功（模拟）' : '迭代创建成功（模拟）')
    dialogVisible.value = false
    loadIterations()
  }
}

const handleDelete = async (row: Iteration) => {
  try {
    await deleteIteration(row.id)
    ElMessage.success('删除成功')
    loadIterations()
  } catch {
    iterations.value = iterations.value.filter((i) => i.id !== row.id)
    ElMessage.success('删除成功（模拟）')
  }
}

// 燃尽图
const viewBurndown = async (row: Iteration) => {
  currentIteration.value = row
  burndownVisible.value = true

  // 模拟燃尽图数据
  burndownData.value = []
  const start = new Date(row.startDate)
  const end = new Date(row.endDate)
  const total = row.requirementCount || 10
  const days = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24))

  for (let i = 0; i <= days; i++) {
    const d = new Date(start)
    d.setDate(d.getDate() + i)
    const progress = row.progress || 0
    const remaining = Math.max(0, total - Math.round(total * (progress / 100) * (i / days)))
    burndownData.value.push({
      date: d.toISOString().split('T')[0],
      remaining,
      completed: total - remaining,
      total,
    })
  }

  await nextTick()
  renderBurndownChart()
}

const renderBurndownChart = () => {
  const chartDom = document.getElementById('burndown-chart')
  if (!chartDom) return

  const chart = echarts.init(chartDom)
  chart.setOption({
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
  })
}

onMounted(() => {
  loadIterations()
})
</script>

<style scoped lang="scss">
.burndown-chart {
  .mb-4 {
    margin-bottom: 16px;
  }
}
</style>
