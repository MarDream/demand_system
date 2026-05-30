<template>
  <PageContainer title="需求管理">
    <!-- Filter -->
    <FilterCard>
      <div class="view-switch">
        <el-radio-group v-model="viewMode" size="small" @change="handleViewModeChange">
          <el-radio-button value="all">全部需求</el-radio-button>
          <el-radio-button value="pending">
            <el-badge :value="viewCounts.pending" :hidden="viewCounts.pending === 0">我的待办</el-badge>
          </el-radio-button>
          <el-radio-button value="done">
            <el-badge :value="viewCounts.done" :hidden="viewCounts.done === 0">我的已办</el-badge>
          </el-radio-button>
          <el-radio-button value="drafts">
            <el-badge :value="viewCounts.drafts" :hidden="viewCounts.drafts === 0">我的草稿</el-badge>
          </el-radio-button>
        </el-radio-group>
      </div>
      <el-form :model="filterForm" inline>
        <div class="filter-main">
          <el-form-item v-if="!isDraftView && !isPendingView && !isDoneView" label="需求类型">
            <el-select v-model="filterForm.type" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="t in configTypes" :key="t.code" :label="t.name" :value="t.code" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isDraftView && !isPendingView && !isDoneView" label="优先级">
            <el-select v-model="filterForm.priority" placeholder="全部" clearable style="width: 100px">
              <el-option v-for="p in configPriorities" :key="p.code" :label="p.name" :value="p.code" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isDraftView && !isPendingView && !isDoneView" label="状态">
            <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="新建" value="新建" />
              <el-option label="待分析" value="待分析" />
              <el-option label="待确认" value="待确认" />
              <el-option label="待评审" value="待评审" />
              <el-option label="评审中" value="评审中" />
              <el-option label="已通过" value="已通过" />
              <el-option label="开发中" value="开发中" />
              <el-option label="测试中" value="测试中" />
              <el-option label="已上线" value="已上线" />
              <el-option label="已验收" value="已验收" />
              <el-option label="已取消" value="已取消" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="!isDraftView && !isPendingView && !isDoneView" label="负责人">
            <el-select v-model="filterForm.assigneeId" placeholder="请选择" clearable style="width: 140px">
              <el-option v-for="user in filterUserList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="filterForm.keyword" placeholder="关键词搜索" clearable style="width: 220px" @keyup.enter="handleSearch" />
          </el-form-item>
        </div>
        <el-collapse-transition>
          <div v-show="filterExpanded && !isDraftView && !isPendingView && !isDoneView" class="filter-extra">
            <el-form-item label="时间维度">
              <el-select v-model="timeDimension" placeholder="选择时间维度" style="width: 140px">
                <el-option label="创建时间" value="createdAt" />
                <el-option label="分析完成" value="analysisCompletedAt" />
                <el-option label="需求确认" value="confirmAt" />
                <el-option label="开发完成" value="developmentCompletedAt" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker
                v-model="timeRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD HH:mm:ss"
                :default-time="defaultTime"
                style="width: 300px"
              />
            </el-form-item>
          </div>
        </el-collapse-transition>
        <el-link type="primary" underline="never" class="filter-toggle" @click="filterExpanded = !filterExpanded">
          {{ filterExpanded ? '收起' : '展开' }}
          <el-icon class="filter-toggle__icon" :class="{ 'is-expanded': filterExpanded }"><ArrowDown /></el-icon>
        </el-link>
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
            <el-button :icon="Setting" circle @click="showColumnConfig = true" title="列设置" />
            <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete">
              批量删除
            </el-button>
          </template>
        </Toolbar>
      </template>

      <template #table>
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="tableData"
          row-key="id"
          :expand-row-keys="expandedRowKeys"
          border
          stripe
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column
            type="expand"
            width="1"
            class-name="requirement-expand-column"
            label-class-name="requirement-expand-column"
          >
            <template #default="{ row }">
              <div class="expand-row">
                <p class="expand-row__text" v-if="row.childCount && row.childCount > 0">
                  子需求（共 {{ row.childCount }} 个，详情查看）
                </p>
                <p class="expand-row__text" v-else>暂无子需求</p>
              </div>
            </template>
          </el-table-column>
          <template v-for="col in visibleColumns" :key="col.key">
            <el-table-column
              :label="col.label"
              :width="col.width"
              :min-width="col.minWidth"
              :align="col.align || 'center'"
              :fixed="col.fixed"
            >
              <template #default="{ row }">
                <template v-if="col.key === 'title'">
                  <div class="requirement-title">
                    <el-link type="primary" @click="handleOpen(row)">{{ row.title }}</el-link>
                    <button
                      v-if="hasChildRequirements(row)"
                      type="button"
                      class="requirement-title__toggle"
                      :aria-label="isRowExpanded(row) ? '折叠子需求' : '展开子需求'"
                      @click.stop="toggleExpandedRow(row)"
                    >
                      <el-icon class="requirement-title__toggle-icon" :class="{ 'is-expanded': isRowExpanded(row) }">
                        <ArrowRight />
                      </el-icon>
                    </button>
                  </div>
                </template>
                <template v-else-if="col.key === 'requirementNo'">
                  {{ row.requirementNo || '-' }}
                </template>
                <template v-else-if="col.key === 'type'">
                  <el-tag>{{ typeLabel(row.type) }}</el-tag>
                </template>
                <template v-else-if="col.key === 'priority'">
                  <el-tag :type="priorityTagType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
                </template>
                <template v-else-if="col.key === 'status'">
                  <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
                </template>
                <template v-else-if="col.key === 'assigneeName'">
                  {{ row.currentHandlerName || row.assigneeName || '-' }}
                </template>
                <template v-else-if="col.key.endsWith('At') || col.key === 'createdAt'">
                  {{ formatDate(row[col.key]) }}
                </template>
                <template v-else-if="col.key === 'operations'">
                  <!-- 我的待办/已办视图根据operationType显示不同按钮 -->
                  <template v-if="isPendingView || isDoneView">
                    <el-button v-if="row.operationType === 'edit'" link type="primary" @click="handleEdit(row)">编辑</el-button>
                    <el-button v-if="row.operationType === 'approve'" link type="warning" @click="handleOpen(row)">待办</el-button>
                    <el-button v-if="row.operationType === 'view'" link type="primary" @click="handleOpen(row)">查看</el-button>
                  </template>
                  <!-- 全部需求/草稿视图显示原有操作按钮 -->
                  <template v-else>
                    <el-tooltip content="查看详情" placement="top">
                      <el-button link type="primary" :icon="View" @click="handleOpen(row)" />
                    </el-tooltip>
                    <el-tooltip content="编辑" placement="top">
                      <el-button link type="primary" :icon="Edit" @click="handleEdit(row)" />
                    </el-tooltip>
                    <el-popconfirm title="确定删除该需求吗？" @confirm="handleDelete(row.id)">
                      <template #reference>
                        <el-button link type="danger" :icon="Delete" title="删除" />
                      </template>
                    </el-popconfirm>
                  </template>
                </template>
                <template v-else>
                  {{ row[col.key as keyof Requirement] ?? '-' }}
                </template>
              </template>
            </el-table-column>
          </template>
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

    <!-- 列配置弹窗 -->
    <el-dialog v-model="showColumnConfig" title="列显示设置" width="400px">
      <div class="column-config">
        <el-checkbox v-model="checkAll" :indeterminate="isIndeterminate" @change="handleCheckAll">
          全选
        </el-checkbox>
        <el-divider />
        <el-checkbox-group v-model="selectedColumnKeys">
          <div v-for="col in allColumns.filter(c => c.key !== 'operations')" :key="col.key" class="column-item">
            <el-checkbox :label="col.key">{{ col.label }}</el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="showColumnConfig = false">取消</el-button>
        <el-button type="primary" @click="saveColumns">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Setting, View, Edit, Delete, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import { exportToExcel } from '@/utils/excel'
import { requirementApi, userApi } from '@/api'
import { getMyRequirementPending, getMyRequirementDone } from '@/api/modules/requirement'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { getColumnConfig, saveColumnConfig } from '@/api/modules/requirement'
import type { Requirement, RequirementMyListQuery, RequirementQuery } from '@/types/requirement'
import type { User } from '@/types/user'
import { normalizeText, formatDate, stripPriorityPrefix } from '@/utils/format'
import PageContainer from '@/components/common/PageContainer.vue'
import FilterCard from '@/components/common/FilterCard.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const route = useRoute()
const router = useRouter()

const filterExpanded = ref(true)
const viewMode = ref<'all' | 'drafts' | 'pending' | 'done'>(route.query.view === 'drafts' ? 'drafts' : route.query.view === 'pending' ? 'pending' : route.query.view === 'done' ? 'done' : 'all')
const isDraftView = computed(() => viewMode.value === 'drafts')
const isPendingView = computed(() => viewMode.value === 'pending')
const isDoneView = computed(() => viewMode.value === 'done')

const DEFAULT_PROJECT_ID = 1

interface ColumnDef {
  key: string
  label: string
  width?: number
  minWidth?: number
  align?: string
  fixed?: string | false
}

// 所有可用列定义
const allColumns: ColumnDef[] = [
  { key: 'title', label: '需求标题', minWidth: 220, fixed: false },
  { key: 'requirementNo', label: '需求编号', width: 190 },
  { key: 'type', label: '类型', width: 100 },
  { key: 'priority', label: '优先级', width: 90 },
  { key: 'status', label: '状态', width: 100 },
  { key: 'creatorName', label: '创建人', width: 100 },
  { key: 'assigneeName', label: '负责人', width: 100 },
  { key: 'opsFollowName', label: '运营跟进人', width: 110 },
  { key: 'maintFollowName', label: '运维跟进人', width: 110 },
  { key: 'departmentName', label: '归属部门', width: 120 },
  { key: 'createdAt', label: '创建时间', width: 170 },
  { key: 'analysisCompletedAt', label: '分析完成时间', width: 160 },
  { key: 'confirmAt', label: '需求确认时间', width: 160 },
  { key: 'developmentCompletedAt', label: '开发完成时间', width: 160 },
  { key: 'operations', label: '操作', width: 120, fixed: 'right' },
]

// 默认显示的列
const defaultColumnKeys = ['title', 'requirementNo', 'type', 'priority', 'status', 'creatorName', 'assigneeName', 'createdAt', 'operations']

const selectedColumnKeys = ref<string[]>([...defaultColumnKeys])
const showColumnConfig = ref(false)

const checkAll = computed(() => {
  const optional = allColumns.filter(c => c.key !== 'operations')
  return optional.every(c => selectedColumnKeys.value.includes(c.key))
})

const isIndeterminate = computed(() => {
  const optional = allColumns.filter(c => c.key !== 'operations')
  const checked = optional.filter(c => selectedColumnKeys.value.includes(c.key)).length
  return checked > 0 && checked < optional.length
})

function handleCheckAll(val: boolean) {
  const optional = allColumns.filter(c => c.key !== 'operations')
  selectedColumnKeys.value = val ? optional.map(c => c.key) : []
}

const visibleColumns = computed(() => {
  const cols = allColumns.filter(c => selectedColumnKeys.value.includes(c.key))
  if (!cols.find(c => c.key === 'operations')) {
    cols.push(allColumns.find(c => c.key === 'operations')!)
  }
  return cols
})

async function loadColumnConfig() {
  try {
    const res = await getColumnConfig('requirement_list')
    if (res && Array.isArray(res)) {
      selectedColumnKeys.value = [...normalizeColumnKeys(res), 'operations']
    }
  } catch {
    // 使用默认配置
  }
}

async function saveColumns() {
  try {
    const keys = selectedColumnKeys.value.filter(k => k !== 'operations')
    await saveColumnConfig('requirement_list', keys)
    ElMessage.success('列配置已保存')
    showColumnConfig.value = false
  } catch {
    ElMessage.error('保存列配置失败')
  }
}

// 配置
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
    const typeList = Array.isArray(typesRes) ? typesRes : (typesRes as any).data || []
    const priorityList = Array.isArray(prioritiesRes) ? prioritiesRes : (prioritiesRes as any).data || []
    configTypes.value = typeList.map((t: any) => ({ ...t, name: normalizeText(t.name) }))
    configPriorities.value = priorityList.map((p: any) => ({ ...p, name: stripPriorityPrefix(normalizeText(p.name)) }))
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
  return stripPriorityPrefix(priorityMap.value[code] || code || '-')
}

function normalizeColumnKeys(keys: string[]) {
  const normalized = keys.filter(key => key !== 'operations')
  if (!normalized.includes('requirementNo')) {
    const typeIndex = normalized.indexOf('type')
    if (typeIndex >= 0) {
      normalized.splice(typeIndex, 0, 'requirementNo')
    } else {
      normalized.unshift('requirementNo')
    }
  }
  return normalized
}

// Filter user list
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

// 时间筛选
const timeDimension = ref<'createdAt' | 'analysisCompletedAt' | 'confirmAt' | 'developmentCompletedAt'>('createdAt')
const timeRange = ref<[string, string] | null>(null)
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

// Table data
const loading = ref(false)
const tableData = ref<Requirement[]>([])
const selectedIds = ref<number[]>([])
const tableRef = ref<TableInstance>()
const expandedRowKeys = ref<number[]>([])

// Pagination
const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
})
const viewCounts = reactive({
  drafts: 0,
  pending: 0,
  done: 0,
})

async function refreshViewCounts() {
  try {
    const [drafts, pending, done] = await Promise.all([
      requirementApi.getMyRequirementDrafts({ pageNum: 1, pageSize: 1 }),
      getMyRequirementPending({ pageNum: 1, pageSize: 1 }),
      getMyRequirementDone(),
    ])
    viewCounts.drafts = drafts.total
    viewCounts.pending = pending.total
    viewCounts.done = done.length
  } catch {
    // ignore count refresh failures
  }
}

// Fetch data
async function fetchData() {
  loading.value = true
  try {
    if (isDraftView.value) {
      const params: RequirementMyListQuery = {
        keyword: filterForm.keyword || undefined,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      }
      const data = await requirementApi.getMyRequirementDrafts(params)
      tableData.value = data.list
      pagination.total = data.total
      return
    }

    if (isPendingView.value) {
      const params: RequirementMyListQuery = {
        keyword: filterForm.keyword || undefined,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
      }
      const data = await getMyRequirementPending(params)
      tableData.value = data.list
      pagination.total = data.total
      return
    }

    if (isDoneView.value) {
      const data = await getMyRequirementDone({ keyword: filterForm.keyword })
      tableData.value = data
      pagination.total = data.length
      return
    }

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

    if (timeRange.value) {
      const [start, end] = timeRange.value
      if (timeDimension.value === 'createdAt') {
        params.createdAtStart = start
        params.createdAtEnd = end
      } else if (timeDimension.value === 'analysisCompletedAt') {
        params.analysisCompletedAtStart = start
        params.analysisCompletedAtEnd = end
      } else if (timeDimension.value === 'confirmAt') {
        params.confirmAtStart = start
        params.confirmAtEnd = end
      } else if (timeDimension.value === 'developmentCompletedAt') {
        params.developmentCompletedAtStart = start
        params.developmentCompletedAtEnd = end
      }
    }

    const data = await requirementApi.getRequirementList(params)
    tableData.value = data.list
    pagination.total = data.total
  } catch {
    ElMessage.error('获取需求列表失败')
  } finally {
    loading.value = false
    void refreshViewCounts()
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
  timeDimension.value = 'createdAt'
  timeRange.value = null
  pagination.pageNum = 1
  fetchData()
}

function handleViewModeChange(value: 'all' | 'drafts' | 'pending' | 'done') {
  viewMode.value = value
  pagination.pageNum = 1
  selectedIds.value = []
  const query: Record<string, string> = {}
  if (value === 'drafts') query.view = 'drafts'
  else if (value === 'pending') query.view = 'pending'
  else if (value === 'done') query.view = 'done'
  router.replace({ query })
  fetchData()
}

function handleCreate() {
  router.push({ name: 'RequirementCreate' })
}

function handleEdit(row: Requirement) {
  router.push({ name: 'RequirementCreate', query: { id: row.id } })
}

function handleOpen(row: Requirement) {
  if (row.isDraft) {
    handleEdit(row)
    return
  }
  handleViewDetail(row.id)
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

function hasChildRequirements(row: Requirement) {
  return Number(row.childCount || 0) > 0
}

function isRowExpanded(row: Requirement) {
  return expandedRowKeys.value.includes(row.id)
}

function toggleExpandedRow(row: Requirement) {
  if (!hasChildRequirements(row)) return

  const nextExpanded = !isRowExpanded(row)
  const expandedKeySet = new Set(expandedRowKeys.value)

  if (nextExpanded) {
    expandedKeySet.add(row.id)
  } else {
    expandedKeySet.delete(row.id)
  }

  expandedRowKeys.value = Array.from(expandedKeySet)
  tableRef.value?.toggleRowExpansion?.(row, nextExpanded)
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
      '需求编号': row.requirementNo || '',
      '类型': typeLabel(row.type),
      '优先级': priorityLabel(row.priority),
      '状态': row.status || '',
      '创建人': row.creatorName || '-',
      '负责人': row.currentHandlerName || row.assigneeName || '-',
      '运营跟进人': row.opsFollowName || '-',
      '运维跟进人': row.maintFollowName || '-',
      '归属部门': row.departmentName || '-',
      '创建时间': formatDate(row.createdAt),
      '分析完成时间': formatDate(row.analysisCompletedAt),
      '需求确认时间': formatDate(row.confirmAt),
      '开发完成时间': formatDate(row.developmentCompletedAt),
      '描述': row.description || '',
    }))

    const columnWidths = [
      { wch: 30 }, { wch: 22 }, { wch: 10 }, { wch: 10 }, { wch: 10 },
      { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 12 }, { wch: 12 },
      { wch: 20 }, { wch: 20 }, { wch: 20 }, { wch: 20 }, { wch: 40 },
    ]

    exportToExcel(exportData, '需求列表', '需求列表', columnWidths)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// Tag type helpers
function priorityTagType(priority: string): string {
  const map: Record<string, string> = { P0: 'danger', P1: 'warning', P2: 'info', P3: 'success' }
  return map[priority] || 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待分析': 'warning', '待确认': 'warning', '待评审': 'warning',
    '评审中': 'warning', '已通过': 'success', '开发中': 'primary', '测试中': 'info',
    '已上线': 'success', '已验收': 'success', '已取消': 'info',
  }
  return map[status] || 'info'
}

onMounted(() => {
  fetchData()
  loadFilterUsers()
  loadConfig()
  loadColumnConfig()
  refreshViewCounts()
})

watch(tableData, (rows) => {
  const validRowIds = new Set(
    rows
      .filter(hasChildRequirements)
      .map(row => row.id),
  )
  expandedRowKeys.value = expandedRowKeys.value.filter(id => validRowIds.has(id))
})
</script>

<style scoped lang="scss">
.expand-row {
  padding: 10px 40px;
}

.expand-row__text {
  color: $text-color-placeholder;
}

.requirement-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.requirement-title__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: $text-color-secondary;
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.requirement-title__toggle-icon {
  font-size: 14px;
  transition: transform 0.2s ease, color 0.2s ease;

  &.is-expanded {
    transform: rotate(90deg);
  }
}

:deep(.requirement-expand-column) {
  width: 1px !important;
  min-width: 1px !important;
  padding: 0 !important;
}

:deep(.requirement-expand-column .cell) {
  display: none;
}

.column-config {
  .column-item {
    margin-bottom: 8px;
  }
}

.view-switch {
  margin-bottom: 12px;
}

.filter-toggle {
  display: inline-flex;
  align-items: center;
  margin-left: 8px;

  &__icon {
    transition: transform 0.3s;

    &.is-expanded {
      transform: rotate(180deg);
    }
  }
}
</style>
