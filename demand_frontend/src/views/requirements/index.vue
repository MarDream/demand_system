<template>
  <PageContainer title="需求管理">
    <!-- Filter -->
    <FilterCard>
      <div class="view-switch">
        <el-tabs v-model="viewMode" class="view-switch-tabs" @tab-change="handleViewModeChange">
          <el-tab-pane v-if="hasPermission('menu:requirement:view:all')" name="all">
            <template #label>
              <span class="view-switch__tab-label">
                <el-icon><Document /></el-icon>
                <span class="view-switch__tab-text">全部需求</span>
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane v-if="hasPermission('menu:requirement:view:pending')" name="pending">
            <template #label>
              <span class="view-switch__tab-label">
                <el-icon><Bell /></el-icon>
                <span class="view-switch__tab-text">我的待办</span>
                <el-badge
                  v-if="viewCounts.pending > 0"
                  :value="viewCounts.pending"
                  class="view-switch__badge"
                />
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane v-if="hasPermission('menu:requirement:view:done')" name="done">
            <template #label>
              <span class="view-switch__tab-label">
                <el-icon><CircleCheck /></el-icon>
                <span class="view-switch__tab-text">我的已办</span>
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane v-if="hasPermission('menu:requirement:view:follow')" name="follows">
            <template #label>
              <span class="view-switch__tab-label">
                <el-icon><Star /></el-icon>
                <span class="view-switch__tab-text">我的关注</span>
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane v-if="hasPermission('menu:requirement:view:draft')" name="drafts">
            <template #label>
              <span class="view-switch__tab-label">
                <el-icon><EditPen /></el-icon>
                <span class="view-switch__tab-text">我的草稿</span>
                <el-badge
                  v-if="viewCounts.drafts > 0"
                  :value="viewCounts.drafts"
                  class="view-switch__badge"
                />
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>
      <el-form :model="filterForm" inline class="filter-form">
        <el-collapse-transition>
          <div v-show="filterExpanded" class="filter-collapse-wrapper">
            <div class="filter-main">
              <el-form-item label="需求类型" class="filter-item">
                <el-select v-model="filterForm.type" placeholder="全部" clearable class="filter-select--type">
                  <el-option v-for="t in configTypes" :key="t.code" :label="t.name" :value="t.code" />
                </el-select>
              </el-form-item>
              <el-form-item label="优先级" class="filter-item">
                <el-select v-model="filterForm.priority" placeholder="全部" clearable class="filter-select--priority">
                  <el-option v-for="p in configPriorities" :key="p.code" :label="p.name" :value="p.code" />
                </el-select>
              </el-form-item>
              <el-form-item label="状态" class="filter-item">
                <el-select v-model="filterForm.status" placeholder="全部" clearable class="filter-select--status">
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
                  <el-option label="已拒绝" value="已拒绝" />
                  <el-option label="打回" value="打回" />
                  <el-option label="测试不通过" value="测试不通过" />
                  <el-option label="验收不通过" value="验收不通过" />
                </el-select>
              </el-form-item>
              <el-form-item label="负责人" class="filter-item">
                <el-select v-model="filterForm.assigneeId" placeholder="请选择" clearable class="filter-select--assignee">
                  <el-option v-for="user in filterUserList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
                </el-select>
              </el-form-item>
              <el-form-item class="filter-item filter-item--search">
                <el-input
                  v-model="filterForm.keyword"
                  placeholder="关键词搜索（回车搜索）"
                  clearable
                  class="filter-input--keyword"
                  @keyup.enter="handleSearch"
                >
                  <template #append>
                    <el-button
                      class="filter-search-append"
                      aria-label="执行搜索"
                      @click="handleSearch"
                    >
                      <el-icon><Search /></el-icon>
                      <span>搜索</span>
                    </el-button>
                  </template>
                </el-input>
              </el-form-item>
            </div>
            <div v-show="isAllView" class="filter-extra">
              <el-form-item label="时间维度" class="filter-item">
                <el-select v-model="timeDimension" placeholder="选择时间维度" class="filter-select--dimension">
                  <el-option label="创建时间" value="createdAt" />
                  <el-option label="分析完成" value="analysisCompletedAt" />
                  <el-option label="需求确认" value="confirmAt" />
                  <el-option label="开发完成" value="developmentCompletedAt" />
                </el-select>
              </el-form-item>
              <el-form-item label="日期范围" class="filter-item filter-item--date">
                <el-date-picker
                  v-model="timeRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  :default-time="defaultTime"
                  class="filter-date-range"
                />
              </el-form-item>
            </div>
          </div>
        </el-collapse-transition>
        <div class="filter-meta-actions">
          <el-link type="primary" underline="never" class="filter-toggle" @click="filterExpanded = !filterExpanded">
            {{ filterExpanded ? '收起' : '展开' }}
            <el-icon class="filter-toggle__icon" :class="{ 'is-expanded': filterExpanded }"><ArrowDown /></el-icon>
          </el-link>
          <div class="filter-meta-actions__right">
            <el-button
              class="filter-reset-icon-btn"
              :icon="Close"
              circle
              aria-label="重置筛选条件"
              title="重置筛选条件"
              @click="handleReset"
            />
            <el-button
              :icon="Setting"
              circle
              aria-label="列表字段设置"
              title="列表字段设置"
              @click="openColumnConfig"
            />
          </div>
        </div>
      </el-form>
    </FilterCard>

    <!-- Table -->
    <TableCard>
      <template #toolbar>
        <Toolbar>
          <template #left>
            <el-button v-if="hasPermission('button:requirement:create')" type="primary" @click="handleCreate">新建需求</el-button>
            <el-button v-if="hasPermission('button:requirement:export')" text @click="handleExport">导出Excel</el-button>
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
          fit
        >
          <template #empty>
            <el-empty description="暂无需求数据" :image-size="80">
              <el-button type="primary" @click="handleCreate">立即创建</el-button>
            </el-empty>
          </template>
          <el-table-column
            key="follow"
            label=""
            width="48"
            align="center"
          >
            <template #default="{ row }">
              <el-tooltip :content="row.followed ? '取消关注' : '添加关注'" placement="top">
                <el-button
                  link
                  :type="row.followed ? 'warning' : 'info'"
                  :icon="row.followed ? StarFilled : Star"
                  class="requirement-follow-btn"
                  :aria-label="row.followed ? '取消关注' : '添加关注'"
                  @click="handleToggleFollow(row)"
                />
              </el-tooltip>
            </template>
          </el-table-column>
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
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <template v-if="col.key === 'title'">
                  <div class="requirement-title">
                    <el-link type="primary" class="requirement-title__link" @click="handleOpen(row)">{{ row.title }}</el-link>
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
                <template v-else-if="col.key === 'dueDate'">
                  {{ formatDate(row[col.key], 'YYYY-MM-DD HH:mm:ss') }}
                </template>
                <template v-else-if="col.key === 'operations'">
                  <div class="requirement-operation-cell">
                    <!-- 我的待办/已办视图根据operationType显示不同按钮 -->
                    <template v-if="isPendingView || isDoneView || isFollowView">
                      <el-button v-if="row.operationType === 'edit' && hasPermission('button:requirement:update')" link type="primary" @click="handleEdit(row)">编辑</el-button>

                      <!-- 待办按钮：有流转权限但无RBAC权限时显示禁用状态 -->
                      <el-tooltip
                        v-if="row.operationType === 'approve' && !hasPermission('button:requirement:submit')"
                        content="您缺少【提交需求】权限，请联系管理员在【角色管理】中配置"
                        placement="top">
                        <el-button link type="warning" disabled>待办</el-button>
                      </el-tooltip>
                      <el-button
                        v-else-if="row.operationType === 'approve' && hasPermission('button:requirement:submit')"
                        link type="warning" @click="handleOpen(row)">待办</el-button>

                      <el-button v-if="row.operationType === 'view'" link type="primary" @click="handleOpen(row)">查看</el-button>
                    </template>
                    <!-- 全部需求/草稿视图：根据 canEdit 和权限双重判断 -->
                    <template v-else>
                      <el-tooltip content="查看详情" placement="top">
                        <el-button link type="primary" :icon="View" aria-label="查看详情" @click="handleOpen(row)" />
                      </el-tooltip>
                      <el-tooltip v-if="row.canEdit && hasPermission('button:requirement:update')" content="编辑" placement="top">
                        <el-button
                          link
                          type="primary"
                          :icon="Edit"
                          aria-label="编辑"
                          @click="handleEdit(row)"
                        />
                      </el-tooltip>
                      <el-popconfirm
                        v-if="canDeleteRequirement(row)"
                        title="确定删除该需求吗？"
                        @confirm="handleDelete(row.id)"
                      >
                        <template #reference>
                          <el-button link type="danger" :icon="Delete" title="删除" aria-label="删除" />
                        </template>
                      </el-popconfirm>
                    </template>
                  </div>
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
    <el-dialog
      v-model="showColumnConfig"
      title="列表字段设置"
      width="860px"
      class="column-config-dialog"
      @opened="initSelectedColumnSortable"
      @close="handleColumnConfigClose"
    >
      <div class="column-config">
        <section class="column-config__panel column-config__panel--available">
          <div class="column-config__panel-title">备选字段</div>
          <div class="column-config__panel-body">
            <div
              v-for="group in columnGroups"
              :key="group.title"
              class="column-config__group"
            >
              <div class="column-config__group-title">{{ group.title }}</div>
              <el-checkbox-group v-model="draftColumnKeys" class="column-config__checkbox-grid">
                <el-checkbox
                  v-for="col in group.columns"
                  :key="col.key"
                  :value="col.key"
                  class="column-config__checkbox"
                >
                  {{ col.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </section>
        <section class="column-config__panel column-config__panel--selected">
          <div class="column-config__panel-title">当前选定字段</div>
          <div ref="selectedColumnListRef" class="column-config__selected-list">
            <div
              v-for="col in draftSelectedColumns"
              :key="col.key"
              class="column-config__selected-item"
              :data-key="col.key"
            >
              <el-icon class="column-config__drag-handle"><Rank /></el-icon>
              <span class="column-config__selected-label">{{ col.label }}</span>
              <el-button
                link
                :icon="Close"
                class="column-config__remove"
                :aria-label="`移除${col.label}`"
                @click="removeDraftColumn(col.key)"
              />
            </div>
            <el-empty
              v-if="draftSelectedColumns.length === 0"
              description="暂无选定字段"
              :image-size="72"
              class="column-config__empty"
            />
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="handleCancelColumnConfig">取消</el-button>
        <el-button type="primary" @click="saveColumns">确定</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Setting, View, Edit, Delete, ArrowDown, ArrowRight, Star, StarFilled, Rank, Close, Document, Bell, CircleCheck, EditPen, Search } from '@element-plus/icons-vue'
import Sortable, { type SortableEvent } from 'sortablejs'
import { exportToExcel } from '@/utils/excel'
import { requirementApi, userApi } from '@/api'
import { getMyRequirementPending, getMyRequirementDone, getMyRequirementFollows } from '@/api/modules/requirement'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { getColumnConfig, saveColumnConfig } from '@/api/modules/requirement'
import type { Requirement, RequirementMyListQuery, RequirementQuery } from '@/types/requirement'
import type { User } from '@/types/user'
import { normalizeText, formatDate, stripPriorityPrefix } from '@/utils/format'
import { usePermission } from '@/composables/usePermission'
import { useUserStore } from '@/stores/modules/user'
import PageContainer from '@/components/common/PageContainer.vue'
import FilterCard from '@/components/common/FilterCard.vue'
import TableCard from '@/components/common/TableCard.vue'
import Toolbar from '@/components/common/Toolbar.vue'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const userStore = useUserStore()

// Tab切换缓存
const CACHE_EXPIRY_MS = 5 * 60 * 1000 // 5分钟缓存有效期
const tabDataCache = new Map<string, { data: Requirement[], total: number, timestamp: number }>()

const filterExpanded = ref(true)
type RequirementViewMode = 'all' | 'drafts' | 'pending' | 'done' | 'follows'

const viewMode = ref<RequirementViewMode>(
  route.query.view === 'drafts'
    ? 'drafts'
    : route.query.view === 'pending'
      ? 'pending'
      : route.query.view === 'done'
        ? 'done'
        : route.query.view === 'follows'
          ? 'follows'
          : 'all',
)
// 计算 el-radio-group 实际能看到的子项数量，避免出现零子节点触发 [ElOnlyChild] 警告
const visibleViewOptions = computed(() => [
  hasPermission('menu:requirement:view:all'),
  hasPermission('menu:requirement:view:pending'),
  hasPermission('menu:requirement:view:done'),
  hasPermission('menu:requirement:view:follow'),
  hasPermission('menu:requirement:view:draft'),
].filter(Boolean).length)
const isAllView = computed(() => viewMode.value === 'all')
const isDraftView = computed(() => viewMode.value === 'drafts')
const isPendingView = computed(() => viewMode.value === 'pending')
const isDoneView = computed(() => viewMode.value === 'done')
const isFollowView = computed(() => viewMode.value === 'follows')

const DEFAULT_PROJECT_ID = 1

interface ColumnDef {
  key: string
  label: string
  group?: string
  width?: number
  minWidth?: number
  align?: string
  fixed?: string | false
}

// 所有可用列定义
const allColumns: ColumnDef[] = [
  { key: 'title', label: '需求标题', group: '基础字段', minWidth: 220, fixed: false },
  { key: 'requirementNo', label: '需求编号', group: '基础字段', minWidth: 190 },
  { key: 'type', label: '类型', group: '基础字段', minWidth: 100 },
  { key: 'priority', label: '优先级', group: '基础字段', minWidth: 90 },
  { key: 'status', label: '状态', group: '基础字段', minWidth: 100 },
  { key: 'creatorName', label: '提出人', group: '人员与时间', minWidth: 100 },
  { key: 'assigneeName', label: '负责人', group: '人员与时间', minWidth: 100 },
  { key: 'departmentName', label: '归属部门', group: '基础字段', minWidth: 120 },
  { key: 'createdAt', label: '创建时间', group: '人员与时间', minWidth: 170 },
  { key: 'dueDate', label: '期望上线日期', group: '人员与时间', minWidth: 160 },
  { key: 'analysisCompletedAt', label: '分析完成时间', group: '人员与时间', minWidth: 160 },
  { key: 'confirmAt', label: '需求确认时间', group: '人员与时间', minWidth: 160 },
  { key: 'developmentCompletedAt', label: '开发完成时间', group: '人员与时间', minWidth: 160 },
  { key: 'operations', label: '操作', width: 130, fixed: 'right' },
]

// 默认显示的列
const defaultColumnKeys = ['title', 'requirementNo', 'type', 'priority', 'status', 'creatorName', 'assigneeName', 'createdAt', 'operations']

const selectedColumnKeys = ref<string[]>([...defaultColumnKeys])
const draftColumnKeys = ref<string[]>([])
const showColumnConfig = ref(false)
const selectedColumnListRef = ref<HTMLElement>()
let selectedColumnSortable: Sortable | null = null

const configurableColumns = computed(() => allColumns.filter(c => c.key !== 'operations'))

const columnGroups = computed(() => {
  const groupOrder = ['基础字段', '人员与时间', '自定义字段']
  const grouped = new Map<string, ColumnDef[]>()
  configurableColumns.value.forEach((column) => {
    const groupName = column.group || '自定义字段'
    if (!grouped.has(groupName)) {
      grouped.set(groupName, [])
    }
    grouped.get(groupName)!.push(column)
  })
  return groupOrder
    .filter(groupName => grouped.has(groupName))
    .map(groupName => ({
      title: groupName,
      columns: grouped.get(groupName)!,
    }))
})

const draftSelectedColumns = computed(() => {
  const columnMap = new Map(configurableColumns.value.map(column => [column.key, column]))
  return draftColumnKeys.value
    .map(key => columnMap.get(key))
    .filter((column): column is ColumnDef => Boolean(column))
})

const visibleColumns = computed(() => {
  const columnMap = new Map(allColumns.map(column => [column.key, column]))
  const cols = selectedColumnKeys.value
    .map(key => columnMap.get(key))
    .filter((column): column is ColumnDef => Boolean(column))
  if (!cols.find(c => c.key === 'operations')) {
    cols.push(allColumns.find(c => c.key === 'operations')!)
  }
  return cols
})

function openColumnConfig() {
  draftColumnKeys.value = selectedColumnKeys.value.filter(key => key !== 'operations')
  showColumnConfig.value = true
}

async function initSelectedColumnSortable() {
  await nextTick()
  selectedColumnSortable?.destroy()
  if (!selectedColumnListRef.value) return
  selectedColumnSortable = Sortable.create(selectedColumnListRef.value, {
    animation: 150,
    handle: '.column-config__drag-handle',
    draggable: '.column-config__selected-item',
    ghostClass: 'column-config__selected-item--ghost',
    onEnd: handleColumnSortEnd,
  })
}

function handleColumnSortEnd(evt: SortableEvent) {
  const { oldIndex, newIndex } = evt
  if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return

  const keys = [...draftColumnKeys.value]
  const [moved] = keys.splice(oldIndex, 1)
  if (!moved) return
  keys.splice(newIndex, 0, moved)
  draftColumnKeys.value = keys
}

function removeDraftColumn(key: string) {
  draftColumnKeys.value = draftColumnKeys.value.filter(columnKey => columnKey !== key)
}

function handleCancelColumnConfig() {
  showColumnConfig.value = false
}

function handleColumnConfigClose() {
  selectedColumnSortable?.destroy()
  selectedColumnSortable = null
}

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
    const keys = normalizeColumnKeys(draftColumnKeys.value)
    await saveColumnConfig('requirement_list', keys)
    selectedColumnKeys.value = [...keys, 'operations']
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
  if (!code) return '-'

  // 如果 priorityMap 有映射，使用映射值
  let label = priorityMap.value[code] || code

  // 处理 P0-P3 代码（旧格式数据的 fallback）
  const pCodeMap: Record<string, string> = {
    'P0': '紧急',
    'P1': '高',
    'P2': '中',
    'P3': '低'
  }

  if (pCodeMap[code.toUpperCase()]) {
    return pCodeMap[code.toUpperCase()]
  }

  // 处理英文优先级（大小写不敏感）
  const lowerCode = code.toLowerCase()
  const englishMap: Record<string, string> = {
    'urgent': '紧急',
    'high': '高',
    'medium': '中',
    'middle': '中',
    'low': '低'
  }

  if (englishMap[lowerCode]) {
    return englishMap[lowerCode]
  }

  // 去除 P0-、P1- 等前缀
  return stripPriorityPrefix(label)
}

function normalizeColumnKeys(keys: string[]) {
  const allowedKeys = new Set(configurableColumns.value.map(column => column.key))
  return Array.from(new Set(keys.filter(key => key !== 'operations' && allowedKeys.has(key))))
}

// Filter user list
const filterUserList = ref<User[]>([])

async function loadFilterUsers() {
  try {
    const res = await userApi.getFilterUsers() as any
    filterUserList.value = res || []
  } catch (error) {
    // ignore
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
const tableRef = ref<TableInstance>()
const selectedIds = ref<number[]>([])
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
})

function buildMyListParams(): RequirementMyListQuery {
  return {
    type: filterForm.type || undefined,
    priority: filterForm.priority || undefined,
    status: filterForm.status || undefined,
    assigneeId: filterForm.assigneeId,
    keyword: filterForm.keyword || undefined,
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
  }
}

async function refreshViewCounts() {
  try {
    const [drafts, pending] = await Promise.all([
      requirementApi.getMyRequirementDrafts({ pageNum: 1, pageSize: 1 }),
      getMyRequirementPending({ pageNum: 1, pageSize: 1 }),
    ])
    viewCounts.drafts = drafts.total
    viewCounts.pending = pending.total
  } catch {
    // ignore count refresh failures
  }
}

// Fetch data
async function fetchData() {
  // 生成缓存键
  const cacheKey = `${viewMode.value}:${pagination.pageNum}:${pagination.pageSize}:${JSON.stringify(filterForm)}:${timeDimension.value}:${timeRange.value || ''}`

  // 检查缓存（5分钟有效）
  const cached = tabDataCache.get(cacheKey)
  if (cached && Date.now() - cached.timestamp < CACHE_EXPIRY_MS) {
    tableData.value = cached.data
    pagination.total = cached.total
    // 更新视图计数
    if (isDraftView.value) {
      viewCounts.drafts = cached.total
    } else if (isPendingView.value) {
      viewCounts.pending = cached.total
    }
    return
  }

  loading.value = true
  try {
    if (isDraftView.value) {
      const data = await requirementApi.getMyRequirementDrafts(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      tabDataCache.set(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isPendingView.value) {
      const data = await getMyRequirementPending(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      tabDataCache.set(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isDoneView.value) {
      const data = await getMyRequirementDone(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      tabDataCache.set(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isFollowView.value) {
      const data = await getMyRequirementFollows(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      tabDataCache.set(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    const params: RequirementQuery = {
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
    // 更新缓存
    tabDataCache.set(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
  } catch {
    ElMessage.error('获取需求列表失败')
  } finally {
    loading.value = false
    // Update view counts based on current view mode
    if (isDraftView.value) {
      viewCounts.drafts = pagination.total
    } else if (isPendingView.value) {
      viewCounts.pending = pagination.total
    }
  }
}

// Handlers
function handleSearch() {
  pagination.pageNum = 1
  // 清除缓存，强制重新加载
  tabDataCache.clear()
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
  // 清除缓存，强制重新加载
  tabDataCache.clear()
  fetchData()
}

function handleViewModeChange(name: string | number) {
  const value = name as RequirementViewMode
  viewMode.value = value
  pagination.pageNum = 1
  const query: Record<string, string> = {}
  if (value === 'drafts') query.view = 'drafts'
  else if (value === 'pending') query.view = 'pending'
  else if (value === 'done') query.view = 'done'
  else if (value === 'follows') query.view = 'follows'
  router.replace({ query })
  fetchData()
  refreshViewCounts()
}

function handleCreate() {
  router.push({ name: 'RequirementCreate' })
}

function handleEdit(row: Requirement) {
  if (!hasPermission('button:requirement:update')) {
    ElMessage.error('您没有编辑需求的权限')
    return
  }
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

/** 判断是否可以删除需求（草稿：创建人可删除；非草稿：需要权限） */
function canDeleteRequirement(row: Requirement) {
  // 草稿状态：创建人可以删除（无需特殊权限）
  if (row.isDraft) {
    return row.creatorId === userStore.userInfo?.id
  }
  // 非草稿状态：需要删除权限
  return hasPermission('button:requirement:delete')
}

async function handleToggleFollow(row: Requirement) {
  const nextFollowed = !row.followed
  try {
    if (!nextFollowed) {
      await requirementApi.unfollowRequirement(row.id)
      row.followed = false
      ElMessage.success('已取消关注')
      if (isFollowView.value) {
        fetchData()
      }
      return
    }
    await requirementApi.followRequirement(row.id)
    row.followed = true
    ElMessage.success('已添加关注')
  } catch {
    ElMessage.error(nextFollowed ? '添加关注失败' : '取消关注失败')
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
      '提出人': row.creatorName || '-',
      '负责人': row.currentHandlerName || row.assigneeName || '-',
      '归属部门': row.departmentName || '-',
      '创建时间': formatDate(row.createdAt),
      '分析完成时间': formatDate(row.analysisCompletedAt),
      '需求确认时间': formatDate(row.confirmAt),
      '开发完成时间': formatDate(row.developmentCompletedAt),
      '描述': row.description || '',
    }))

    const columnWidths = [
      { wch: 30 }, { wch: 22 }, { wch: 10 }, { wch: 10 }, { wch: 10 },
      { wch: 12 }, { wch: 12 }, { wch: 12 },
      { wch: 20 }, { wch: 20 }, { wch: 20 }, { wch: 20 }, { wch: 40 },
    ]

    exportToExcel(exportData, '需求列表', '需求列表', columnWidths)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// Tag type helpers
function priorityTagType(priority: string): string | undefined {
  // 先转换为中文标签
  const label = priorityLabel(priority)
  // 根据中文标签映射颜色
  const colorMap: Record<string, string | undefined> = {
    '紧急': 'danger',
    '高': 'warning',
    '中': undefined,
    '低': 'info'
  }
  // 使用 in 运算符检查键是否存在，而不是检查值是否为真
  return label in colorMap ? colorMap[label] : 'info'
}

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待分析': 'warning', '待确认': 'warning', '待评审': 'warning',
    '评审中': 'warning', '已通过': 'success', '开发中': 'primary', '测试中': 'info',
    '已上线': 'success', '已验收': 'success', '已取消': 'info', '已拒绝': 'danger',
    '打回': 'danger', '测试不通过': 'danger', '验收不通过': 'danger',
    PENDING_REVIEW: 'warning', REJECTED: 'danger', SENT_BACK: 'danger',
    TEST_FAILED: 'danger', ACCEPT_FAILED: 'danger',
  }
  return map[status] || 'info'
}

onMounted(async () => {
  // 并行加载独立的配置数据（不相互依赖）
  await Promise.all([
    loadFilterUsers(),
    loadConfig(),
    loadColumnConfig(),
  ])

  // 加载主数据
  await fetchData()

  // 异步加载非关键数据（不阻塞页面渲染）
  refreshViewCounts()
})

onBeforeUnmount(() => {
  selectedColumnSortable?.destroy()
  selectedColumnSortable = null
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
/* ============================================
   响应式设计令牌系统
   ============================================ */
:root {
  --filter-gap-sm: 8px;
  --filter-gap-md: 16px;
  --filter-gap-lg: 24px;
  --filter-item-min-width: 180px;
  --filter-search-min-width: 200px;
}

/* ============================================
   视图切换区域
   ============================================ */
.view-switch {
  margin-bottom: clamp(12px, 1.5vw, 20px);
  overflow-x: auto;

  // 隐藏滚动条但保留功能
  &::-webkit-scrollbar {
    height: 0;
    display: none;
  }

  :deep(.el-tabs__header) {
    margin-bottom: 0;
  }

  :deep(.el-tabs__nav-wrap)::after {
    height: 1px;
  }

  :deep(.el-tabs__item) {
    padding: 0 14px;
    height: 40px;
    line-height: 40px;
  }
}

.view-switch__tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.view-switch__tab-text {
  font-size: 14px;
}

.view-switch__badge {
  margin-left: 2px;
}

.view-switch__empty {
  display: inline-block;
  padding: 4px 12px;
  color: var(--color-text-secondary, #909399);
  font-size: 14px;
}

/* ============================================
   筛选表单主体 - 响应式网格布局
   ============================================ */
.filter-form {
  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 12px;
  }
}

.filter-main,
.filter-extra {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(var(--filter-item-min-width), 1fr));
  gap: var(--filter-gap-md);
  align-items: end;

  // 响应式断点：≥1280px 显示更多列
  @media (min-width: 1280px) {
    grid-template-columns: repeat(6, 1fr);

    .filter-item--search {
      grid-column: span 1;
    }
  }

  // 响应式断点：1024px - 1279px
  @media (min-width: 1024px) and (max-width: 1279px) {
    grid-template-columns: repeat(4, 1fr);

    .filter-item--search {
      grid-column: span 2;
    }
  }

  // 响应式断点：768px - 1023px（平板）
  @media (min-width: 768px) and (max-width: 1023px) {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--filter-gap-md);

    .filter-item--search {
      grid-column: span 2;
    }
  }

  // 响应式断点：<768px（手机）
  @media (max-width: 767px) {
    grid-template-columns: 1fr;
    gap: var(--filter-gap-sm);

    .filter-item--search,
    .filter-item--date {
      grid-column: span 1;
      width: 100%;

      > * {
        width: 100% !important;
      }
    }
  }
}

.filter-extra {
  margin-top: calc(var(--filter-gap-md) + 4px);
  padding-top: var(--filter-gap-md);
  border-top: 1px solid var(--el-border-color-lighter);

  // 小屏时隐藏边框
  @media (max-width: 767px) {
    border-top: none;
    margin-top: var(--filter-gap-sm);
    padding-top: 0;
  }
}

/* ============================================
   筛选项样式 - 流式宽度
   ============================================ */
.filter-item {
  min-width: 0; // 允许grid子项收缩
  
  :deep(.el-form-item__label) {
    font-weight: 500;
    font-size: clamp(13px, 0.85vw, 14px);
    color: var(--el-text-color-regular);
    white-space: nowrap;
    padding-right: 8px;
  }
  
  :deep(.el-select),
  :deep(.el-input) {
    width: 100%;
  }
}

// 各字段的特定最小宽度约束
.filter-select--type {
  :deep(.el-select__wrapper) {
    min-width: 120px;
  }
}

.filter-select--priority {
  :deep(.el-select__wrapper) {
    min-width: 90px;
  }
}

.filter-select--status {
  :deep(.el-select__wrapper) {
    min-width: 110px;
  }
}

.filter-select--assignee {
  :deep(.el-select__wrapper) {
    min-width: 120px;
  }
}

.filter-select--dimension {
  :deep(.el-select__wrapper) {
    min-width: 130px;
  }
}

/* ============================================
   关键词搜索（搜索按钮嵌入 input append）
   ============================================ */
.filter-input--keyword {
  :deep(.el-input__wrapper) {
    min-width: var(--filter-search-min-width);
  }

  // 搜索框在宽屏时可以更宽
  @media (min-width: 1280px) {
    :deep(.el-input__wrapper) {
      min-width: 240px;
    }
  }

  // append 区域内的搜索按钮：主色填充 + 紧凑布局
  :deep(.el-input-group__append) {
    padding: 0;
    background: transparent;
    border: none;
    box-shadow: none;
  }
}

.filter-search-append {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 100%;
  padding: 0 14px;
  border: none !important;
  border-radius: 0 var(--radius-md) var(--radius-md) 0 !important;
  background: var(--el-color-primary) !important;
  color: #fff !important;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-standard);

  &:hover:not(:disabled) {
    background: var(--el-color-primary-light-3) !important;
  }

  &:active:not(:disabled) {
    background: var(--el-color-primary-dark-2) !important;
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }

  :deep(.el-icon) {
    font-size: 14px;
  }
}

/* ============================================
   重置图标按钮样式
   ============================================ */
.filter-reset-icon-btn {
  color: var(--el-text-color-secondary);
  border-color: var(--el-border-color);

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}

/* ============================================
   筛选区域底部操作栏
   ============================================ */
.filter-meta-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-sm);

  &__right {
    display: flex;
    align-items: center;
    gap: var(--spacing-xs);
  }
}

/* ============================================
   筛选区域收起状态下的紧凑间距
   ============================================ */
.filter-form:has(.filter-collapse-wrapper[style*="display: none"]) {
  margin-bottom: 0;

  .filter-meta-actions {
    margin-top: 0;
    padding-top: 8px;
    padding-bottom: 4px;
  }
}

.filter-date-range {
  width: 100% !important;
  
  :deep(.el-date-editor) {
    width: 100% !important;
  }
}

/* ============================================
   展开/收起切换按钮
   ============================================ */
.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: clamp(10px, 1.5vw, 16px);
  margin-left: 4px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s ease;
  
  &:hover {
    opacity: 0.8;
  }
  
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
    border-radius: 2px;
  }

  &__icon {
    font-size: 12px;
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    &.is-expanded {
      transform: rotate(180deg);
    }
  }
}
.expand-row {
  padding: 10px 40px;
}

.expand-row__text {
  color: var(--color-text-placeholder);
}

.requirement-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
}

.requirement-title__link {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
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
  color: var(--color-text-secondary);
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

.requirement-operation-cell {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;

  :deep(.el-button.is-link) {
    padding: 4px 6px;
    font-size: 14px;
    border-radius: 4px;
    transition: background-color 0.15s ease, color 0.15s ease;
  }

  :deep(.el-button.is-link.is-circle) {
    padding: 4px;
  }

  :deep(.el-button.is-link:hover) {
    background-color: var(--el-color-primary-light-9);
  }

  :deep(.el-button.is-link.is-danger:hover) {
    background-color: var(--el-color-danger-light-9);
  }

  :deep(.el-button.is-link.is-warning:hover) {
    background-color: var(--el-color-warning-light-9);
  }
}

:deep(.requirement-follow-btn) {
  padding: 4px;
  font-size: 14px;
  border-radius: 4px;
  transition: background-color 0.15s ease, color 0.15s ease;

  &:hover {
    background-color: var(--el-color-primary-light-9);
  }

  &.is-warning:hover {
    background-color: var(--el-color-warning-light-9);
  }
}

// 合并复选框列与关注列：去掉中间 cell 边框
:deep(.el-table__row > td:nth-child(1)),
:deep(.el-table__header-wrapper th:nth-child(1)) {
  border-right: none !important;
}

:deep(.el-table__row > td:nth-child(2)),
:deep(.el-table__header-wrapper th:nth-child(2)) {
  border-left: none !important;
  padding-left: 0 !important;
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 262px;
  gap: 12px;
  min-height: 520px;
}

.column-config__panel {
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 2px;
  background: var(--el-bg-color);
}

.column-config__panel-title {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 16px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.column-config__panel-body {
  height: 478px;
  overflow-y: auto;
  padding: 14px 20px 20px;
}

.column-config__group + .column-config__group {
  margin-top: 14px;
}

.column-config__group-title {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 22px;
}

.column-config__checkbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(150px, 1fr));
  column-gap: 44px;
  row-gap: 10px;
}

.column-config__checkbox {
  display: inline-flex;
  align-items: center;
  height: 26px;
  margin-right: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.column-config__selected-list {
  height: 478px;
  overflow-y: auto;
  padding: 22px 18px;
}

.column-config__selected-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.column-config__selected-item--ghost {
  opacity: 0.55;
  background: var(--el-color-primary-light-9);
}

.column-config__drag-handle {
  color: var(--el-text-color-placeholder);
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.column-config__selected-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-config__remove {
  color: var(--el-text-color-placeholder);

  &:hover {
    color: var(--el-color-danger);
  }
}

.column-config__empty {
  height: 100%;
  justify-content: center;
}

:deep(.column-config-dialog .el-dialog__body) {
  padding: 20px 22px;
}

:deep(.column-config-dialog .el-dialog__footer) {
  padding: 16px 22px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
