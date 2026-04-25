<template>
  <PageContainer title="需求管理">
    <!-- Filter -->
    <FilterCard>
      <el-form :model="filterForm" inline>
        <el-form-item label="需求类型">
          <el-select v-model="filterForm.type" placeholder="全部" clearable style="width: 140px">
            <el-option
              v-for="t in configTypes"
              :key="t.code"
              :label="t.name"
              :value="t.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filterForm.priority" placeholder="全部" clearable style="width: 100px">
            <el-option
              v-for="p in configPriorities"
              :key="p.code"
              :label="p.name"
              :value="p.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="新建" value="新建" />
            <el-option label="待评审" value="待评审" />
            <el-option label="评审中" value="评审中" />
            <el-option label="已通过" value="已通过" />
            <el-option label="开发中" value="开发中" />
            <el-option label="测试中" value="测试中" />
            <el-option label="已上线" value="已上线" />
            <el-option label="已验收" value="已验收" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="filterForm.assigneeId" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="user in filterUserList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="filterForm.keyword" placeholder="关键词搜索" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
      </el-form>
      <template #actions>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </template>
    </FilterCard>

    <!-- Table -->
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left>
            <el-button type="primary" @click="handleCreate">新建需求</el-button>
            <el-button @click="handleExport">导出Excel</el-button>
          </template>
          <template #right>
            <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
              批量删除
            </el-button>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table
          v-loading="loading"
          :data="tableData"
          border
          stripe
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column type="expand" width="40">
            <template #default="{ row }">
              <div class="expand-row">
                <p class="expand-row__text" v-if="row.childCount && row.childCount > 0">
                  子需求（共 {{ row.childCount }} 个，详情查看）
                </p>
                <p class="expand-row__text" v-else>暂无子需求</p>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="需求标题" min-width="220">
            <template #default="{ row }">
              <el-link type="primary" @click="handleViewDetail(row.id)">{{ row.title }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag>{{ typeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="priorityTagType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="负责人" width="100" align="center">
            <template #default="{ row }">{{ row.assigneeName || '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170" align="center">
            <template #default="{ row }">{{ row.createdAt }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="handleViewDetail(row.id)">查看详情</el-button>
              <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete(row.id)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template #pagination>
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </template>
    </TableCard>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { requirementApi, userApi } from '@/api'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import type { Requirement, RequirementQuery } from '@/types/requirement'
import type { User } from '@/types/user'
import PageContainer from '@/components/common/PageContainer.vue'
import FilterCard from '@/components/common/FilterCard.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const router = useRouter()

// Default projectId
const DEFAULT_PROJECT_ID = 1

const configTypes = ref<any[]>([])
const configPriorities = ref<any[]>([])

const typeMap = ref<Record<string, string>>({})
const priorityMap = ref<Record<string, string>>({})

async function loadConfig() {
  try {
    const [typesRes, prioritiesRes] = await Promise.all([
      requirementConfigApi.listTypes(),
      requirementConfigApi.listPriorities(),
    ])
    configTypes.value = Array.isArray(typesRes) ? typesRes : (typesRes as any).data || []
    configPriorities.value = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any).data || []
    typeMap.value = Object.fromEntries(configTypes.value.map((t: any) => [t.code, t.name]))
    priorityMap.value = Object.fromEntries(configPriorities.value.map((p: any) => [p.code, p.name]))
  } catch {
    // ignore
  }
}

function typeLabel(code: string) {
  return typeMap.value[code] || code || '-'
}

function priorityLabel(code: string) {
  return priorityMap.value[code] || code || '-'
}

// Filter user list (动态加载用户列表用于过滤)
const filterUserList = ref<User[]>([])

async function loadFilterUsers() {
  try {
    const res = await userApi.getUserList({ pageNum: 1, pageSize: 100 }) as any
    filterUserList.value = res.list
  } catch (error) {
    console.error('加载用户列表失败', error)
  }
}

// Filter form
const filterForm = reactive({
  type: '',
  priority: '',
  status: '',
  assigneeId: undefined as number | undefined,
  keyword: '',
})

// Table data
const loading = ref(false)
const tableData = ref<Requirement[]>([])
const selectedIds = ref<number[]>([])

// Pagination
const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
})

// Fetch data
async function fetchData() {
  loading.value = true
  try {
    const params: RequirementQuery = {
      projectId: DEFAULT_PROJECT_ID,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    }
    if (filterForm.type) params.type = filterForm.type
    if (filterForm.priority) params.priority = filterForm.priority
    if (filterForm.status) params.status = filterForm.status
    if (filterForm.assigneeId) params.assigneeId = filterForm.assigneeId
    if (filterForm.keyword) params.keyword = filterForm.keyword

    const res = await requirementApi.getRequirementList(params)
    const data = res
    tableData.value = data.list
    pagination.total = data.total
  } catch {
    ElMessage.error('获取需求列表失败')
  } finally {
    loading.value = false
  }
}

// Handlers
function handleSearch() {
  pagination.pageNum = 1
  fetchData()
}

function handleReset() {
  filterForm.type = ''
  filterForm.priority = ''
  filterForm.status = ''
  filterForm.assigneeId = undefined
  filterForm.keyword = ''
  pagination.pageNum = 1
  fetchData()
}

function handleCreate() {
  router.push({ name: 'RequirementCreate' })
}

function handleEdit(row: Requirement) {
  router.push({ name: 'RequirementCreate', query: { id: row.id } })
}

function handleViewDetail(id: number) {
  router.push({ name: 'RequirementDetail', params: { id } })
}

async function handleDelete(id: number) {
  try {
    await requirementApi.deleteRequirement(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

function handleSelectionChange(selection: Requirement[]) {
  selectedIds.value = selection.map((item) => item.id)
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个需求吗？`, '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    for (const id of selectedIds.value) {
      await requirementApi.deleteRequirement(id)
    }
    ElMessage.success('批量删除成功')
    fetchData()
  } catch {
    // user cancelled or error
  }
}

async function handleExport() {
  if (tableData.value.length === 0) {
    ElMessage.warning('没有数据可导出')
    return
  }

  try {
    const exportData = tableData.value.map(row => ({
      '需求标题': row.title || '',
      '类型': typeLabel(row.type),
      '优先级': priorityLabel(row.priority),
      '状态': row.status || '',
      '负责人': row.assigneeName || '-',
      '创建时间': row.createdAt || '',
      '描述': row.description || '',
    }))

    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '需求列表')

    // 设置列宽
    ws['!cols'] = [
      { wch: 30 }, // 需求标题
      { wch: 10 }, // 类型
      { wch: 10 }, // 优先级
      { wch: 10 }, // 状态
      { wch: 12 }, // 负责人
      { wch: 20 }, // 创建时间
      { wch: 40 }, // 描述
    ]

    const date = new Date().toISOString().slice(0, 10)
    XLSX.writeFile(wb, `需求列表_${date}.xlsx`)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// Tag type helpers
function priorityTagType(priority: string): string {
  const map: Record<string, string> = {
    P0: 'danger',
    P1: 'warning',
    P2: 'info',
    P3: 'success',
  }
  return map[priority] || 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info',
    '待评审': 'info',
    '评审中': 'warning',
    '已通过': 'success',
    '开发中': 'primary',
    '测试中': 'info',
    '已上线': 'success',
    '已验收': 'success',
  }
  return map[status] || 'info'
}

onMounted(() => {
  fetchData()
  loadFilterUsers()
  loadConfig()
})
</script>

<style scoped lang="scss">
.expand-row {
  padding: 10px 40px;
}

.expand-row__text {
  color: $text-color-placeholder;
}
</style>
