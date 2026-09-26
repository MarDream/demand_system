<template>
  <PageContainer title="需求管理" class="requirements-page">
    <!-- Control Console -->
    <FilterCard class="requirement-control-card">
      <div class="requirement-control">
        <div class="requirement-control__top">
          <!-- 左侧：视图标签栏（平铺全部视图） -->
          <div class="view-switch-area">
            <!-- 视图切换：全部视图直接平铺展示（不再折叠为下拉框） -->
            <div class="view-tabs" role="tablist">
              <el-button
                v-for="tab in visibleViewTabs"
                :key="tab.key"
                :type="viewMode === tab.key ? 'primary' : 'default'"
                class="view-tab"
                @click="handleViewModeChange(tab.key)"
              >
                <el-icon class="view-tab__icon"><component :is="tab.icon" /></el-icon>
                <span class="view-tab__text">{{ tab.label }}</span>
                <el-badge
                  v-if="tab.countValue > 0"
                  :value="tab.countValue"
                  class="view-tab__badge"
                />
              </el-button>
            </div>
          </div>

          <!-- 右侧：操作按钮组 -->
          <div class="requirement-control__actions">
            <!-- 主操作 -->
            <el-button
              v-if="hasPermission('button:requirement:create')"
              type="primary"
              class="requirement-primary-action"
              @click="handleCreate"
            >
              新建需求
            </el-button>

            <!-- 更多操作下拉 -->
            <el-dropdown trigger="click" class="more-actions-dropdown">
              <el-button class="more-actions-trigger">
                更多操作
                <el-icon class="more-actions__arrow"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="hasPermission('button:requirement:export')"
                    :disabled="exporting"
                    @click="handleExport"
                  >
                    <el-icon><Download /></el-icon>
                    {{ exporting ? '导出中...' : '导出Excel' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <!-- 工具按钮 -->
            <el-tooltip content="重置筛选条件" placement="top">
              <el-button
                class="filter-reset-icon-btn"
                :icon="Refresh"
                circle
                aria-label="重置筛选条件"
                @click="handleReset"
              />
            </el-tooltip>
            <el-tooltip :content="filterExpanded ? '收起筛选' : '展开筛选'" placement="top">
              <el-button
                class="filter-toggle-icon-btn"
                :icon="filterExpanded ? ArrowUp : ArrowDown"
                circle
                :aria-label="filterExpanded ? '收起筛选' : '展开筛选'"
                @click="filterExpanded = !filterExpanded"
              />
            </el-tooltip>
            <el-tooltip content="列表字段设置" placement="top">
              <el-button
                :icon="Setting"
                circle
                aria-label="列表字段设置"
                @click="openColumnConfig"
              />
            </el-tooltip>
          </div>
        </div>

        <!-- 筛选区域 -->
        <el-form :model="filterForm" inline class="filter-form">
          <div
            class="filter-main"
            :class="{ 'is-expanded': filterExpanded }"
          >
            <div class="filter-main__inner">
              <!-- 基础筛选 -->
              <div class="filter-basic">
                <el-form-item label="需求类型" class="filter-item filter-item--type">
                  <el-select v-model="filterForm.type" placeholder="全部" clearable class="filter-select--type">
                    <el-option v-for="t in configTypes" :key="t.code" :label="t.name" :value="t.code" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态" class="filter-item filter-item--status">
                  <el-select v-model="filterForm.status" placeholder="全部" clearable class="filter-select--status" @change="handleStatusFilterChange">
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
              </div>

              <!-- 高级筛选 -->
              <div class="filter-advanced">
                <div class="filter-advanced__inner">
                  <el-form-item label="优先级" class="filter-item filter-item--priority">
                    <el-select v-model="filterForm.priority" placeholder="全部" clearable class="filter-select--priority">
                      <el-option v-for="p in configPriorities" :key="p.code" :label="p.name" :value="p.code">
                        <span class="priority-option">
                          <span v-if="p.color" class="priority-dot" :style="{ backgroundColor: p.color }"></span>
                          {{ p.name }}
                        </span>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item v-show="isAllView" label="当前处理人" class="filter-item filter-item--assignee">
                    <el-select v-model="filterForm.assigneeId" placeholder="请选择" clearable class="filter-select--assignee">
                      <el-option v-for="user in filterUserList" :key="user.id" :label="user.realName || user.username" :value="user.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="时间维度" class="filter-item filter-item--dimension">
                    <el-select v-model="timeDimension" placeholder="选择时间维度" clearable class="filter-select--dimension">
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
                  <el-form-item class="filter-item filter-item--search">
                    <el-input
                      v-model="filterForm.keyword"
                      :placeholder="keywordPlaceholder"
                      clearable
                      class="filter-input--keyword"
                      @keyup.enter="handleSearch"
                    >
                      <template #prepend>
                        <el-select
                          v-model="filterForm.keywordScope"
                          class="keyword-scope-select"
                          aria-label="关键词搜索范围"
                          @change="handleKeywordScopeChange"
                        >
                          <el-option
                            v-for="option in keywordScopeOptions"
                            :key="option.value"
                            :label="option.label"
                            :value="option.value"
                          >
                            <span class="keyword-scope-option__label">{{ option.label }}</span>
                            <span class="keyword-scope-option__hint">{{ option.hint }}</span>
                          </el-option>
                        </el-select>
                      </template>
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
              </div>
            </div>
          </div>
        </el-form>
      </div>
    </FilterCard>

    <!-- Table -->
    <TableCard class="requirement-table-card">
      <template #table="{ height }">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="tableData"
          row-key="id"
          :expand-row-keys="expandedRowKeys"
          border
          stripe
          fit
          :height="height"
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
                  <el-tag :type="localPriorityTagType(row.priority)" :style="localPriorityTagStyle(row.priority)">{{ localPriorityLabel(row.priority) }}</el-tag>
                </template>
                <template v-else-if="col.key === 'status'">
                  <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
                </template>
                <template v-else-if="col.key === 'assigneeName'">
                  {{ currentHandlerDisplay(row) }}
                </template>
                <template v-else-if="col.key.endsWith('At') || col.key === 'createdAt'">
                  {{ formatDate(row[col.key]) }}
                </template>
                <template v-else-if="col.key === 'dueDate'">
                  {{ formatDate(row[col.key], 'YYYY-MM-DD') }}
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
          :total="pagination.total === -1 ? undefined : pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        >
          <template #total v-if="pagination.total === -1">
            <span style="color: var(--el-text-color-secondary); font-size: 13px;">共多条数据</span>
          </template>
        </el-pagination>
      </template>
    </TableCard>

    <!-- 列配置弹窗 -->
    <ColumnConfigDialog
      v-model="showColumnConfig"
      :column-groups="columnGroups"
      :draft-selected-columns="draftSelectedColumns"
      :draft-column-keys="draftColumnKeys"
      @update:draft-column-keys="draftColumnKeys = $event"
      @remove="removeDraftColumn"
      @save="saveColumns"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import { Setting, View, Edit, Delete, ArrowDown, ArrowUp, ArrowRight, Star, StarFilled, Document, Bell, CircleCheck, EditPen, CopyDocument, Search, Refresh, Download, List } from '@element-plus/icons-vue'
import { requirementApi, userApi } from '@/api'
import { getMyRequirementPending, getMyRequirementDone, getMyRequirementFollows, getMyRequirementCc, getMyRequirementAll, exportRequirementExcel } from '@/api/modules/requirement'
import { getTabBadgeCounts } from '@/api/modules/statistics'
import { requirementConfigApi } from '@/api/modules/requirementConfig'
import { workflowEngineApi, type CurrentNodeHandler } from '@/api/modules/workflow-engine'
import type { Requirement, RequirementMyListQuery, RequirementQuery } from '@/types/requirement'
import type { User } from '@/types/user'
import { normalizeText, formatDate, stripPriorityPrefix } from '@/utils/format'
import { resolveErrorMessage } from '@/utils/error'
import { saveBlob } from '@/utils/download'
import { usePermission } from '@/composables/usePermission'
import { useRequirementTag } from '@/composables/useRequirementTag'
import { useToast } from '@/composables/useToast'
import { useUserStore } from '@/stores/modules/user'
import { useColumnConfig, type ColumnDef } from '@/composables/useColumnConfig'
import PageContainer from '@/components/common/PageContainer.vue'
import FilterCard from '@/components/common/FilterCard.vue'
import TableCard from '@/components/common/TableCard.vue'
import ColumnConfigDialog from '@/components/common/ColumnConfigDialog.vue'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const toast = useToast()
const userStore = useUserStore()

// Tab切换缓存
const CACHE_EXPIRY_MS = 5 * 60 * 1000 // 5分钟缓存有效期
const MAX_CACHE_SIZE = 50 // 最大缓存条目数，防止内存泄漏
const tabDataCache = new Map<string, { data: Requirement[], total: number, timestamp: number }>()

// 缓存写入：超限时清理最老条目
function setCache(key: string, value: { data: Requirement[], total: number, timestamp: number }) {
  if (tabDataCache.size >= MAX_CACHE_SIZE) {
    let oldestKey = ''
    let oldestTime = Infinity
    tabDataCache.forEach((v, k) => {
      if (v.timestamp < oldestTime) {
        oldestTime = v.timestamp
        oldestKey = k
      }
    })
    if (oldestKey) tabDataCache.delete(oldestKey)
  }
  tabDataCache.set(key, value)
}

function invalidateViewCache(view: RequirementViewMode) {
  Array.from(tabDataCache.keys()).forEach((key) => {
    // key 前缀是 `${activeRole}:${view}:`；只按 view 匹配（跨角色全部失效，操作后本就该重拉）
    if (key.includes(`:${view}:`)) {
      tabDataCache.delete(key)
    }
  })
}

// 待办操作后刷新，只清除待办视图缓存
function invalidatePendingCache() {
  const view: RequirementViewMode = 'pending'
  invalidateViewCache(view)
}

// 请求取消控制器：Tab切换或翻页时取消未完成的请求，避免竞态
let fetchAbortController: AbortController | null = null

const filterExpanded = ref(true)
type RequirementViewMode = 'all' | 'drafts' | 'pending' | 'done' | 'follows' | 'cc' | 'my-all'

const viewMode = ref<RequirementViewMode>(
  route.query.view === 'drafts'
    ? 'drafts'
    : route.query.view === 'pending'
      ? 'pending'
      : route.query.view === 'done'
        ? 'done'
        : route.query.view === 'follows'
          ? 'follows'
          : route.query.view === 'cc'
            ? 'cc'
            : route.query.view === 'my-all'
              ? 'my-all'
              : 'all',
)
// 视图标签定义：平铺展示全部视图（顺序固定，界面不再折叠为下拉框）
// 说明：cc（抄送我的）沿用既有权限位 menu:requirement:view:all，保持原行为不变。
interface ViewTab {
  key: string
  label: string
  icon: any
  permission: string
  count?: () => number
}
const viewTabs: ViewTab[] = [
  { key: 'all', label: '全部需求', icon: Document, permission: 'menu:requirement:view:all' },
  { key: 'my-all', label: '我的全部', icon: List, permission: 'menu:requirement:view:all' },
  { key: 'pending', label: '我的待办', icon: Bell, permission: 'menu:requirement:view:pending', count: () => viewCounts.pending },
  { key: 'done', label: '我的已办', icon: CircleCheck, permission: 'menu:requirement:view:done' },
  { key: 'follows', label: '我的关注', icon: Star, permission: 'menu:requirement:view:follow', count: () => viewCounts.follows },
  { key: 'cc', label: '抄送我的', icon: CopyDocument, permission: 'menu:requirement:view:all', count: () => viewCounts.cc },
  { key: 'drafts', label: '我的草稿', icon: EditPen, permission: 'menu:requirement:view:draft', count: () => viewCounts.drafts },
]

// 仅保留有权限的视图，并解析角标数量
const visibleViewTabs = computed(() =>
  viewTabs
    .filter((tab) => hasPermission(tab.permission))
    .map((tab) => ({ ...tab, countValue: tab.count ? tab.count() : 0 })),
)
const isAllView = computed(() => viewMode.value === 'all')
const isDraftView = computed(() => viewMode.value === 'drafts')
const isPendingView = computed(() => viewMode.value === 'pending')
const isDoneView = computed(() => viewMode.value === 'done')
const isFollowView = computed(() => viewMode.value === 'follows')
const isCcView = computed(() => viewMode.value === 'cc')
const isMyAllView = computed(() => viewMode.value === 'my-all')

const DEFAULT_PROJECT_ID = 1

// 所有可用列定义
const requirementAllColumns: ColumnDef[] = [
  { key: 'title', label: '需求标题', group: '基础字段', minWidth: 220, fixed: false },
  { key: 'requirementNo', label: '需求编号', group: '基础字段', minWidth: 190 },
  { key: 'type', label: '类型', group: '基础字段', minWidth: 100 },
  { key: 'priority', label: '优先级', group: '基础字段', minWidth: 90 },
  { key: 'status', label: '状态', group: '基础字段', minWidth: 100 },
  { key: 'creatorName', label: '提出人', group: '人员与时间', minWidth: 100 },
  { key: 'assigneeName', label: '当前处理人', group: '人员与时间', minWidth: 120 },
  { key: 'departmentName', label: '归属部门', group: '基础字段', minWidth: 120 },
  { key: 'createdAt', label: '创建时间', group: '人员与时间', minWidth: 170 },
  { key: 'updatedAt', label: '更新时间', group: '人员与时间', minWidth: 170, disabled: true },
  { key: 'dueDate', label: '期望上线日期', group: '人员与时间', minWidth: 120 },
  { key: 'analysisCompletedAt', label: '分析完成时间', group: '人员与时间', minWidth: 160 },
  { key: 'confirmAt', label: '需求确认时间', group: '人员与时间', minWidth: 160 },
  { key: 'developmentCompletedAt', label: '开发完成时间', group: '人员与时间', minWidth: 160 },
  { key: 'operations', label: '操作', width: 130, fixed: 'right' },
]

// 默认显示的列
const requirementDefaultKeys = ['title', 'requirementNo', 'type', 'priority', 'status', 'creatorName', 'assigneeName', 'createdAt', 'dueDate', 'analysisCompletedAt', 'confirmAt', 'developmentCompletedAt', 'operations']

const {
  showColumnConfig,
  openColumnConfig,
  saveColumns,
  loadColumnConfig,
  columnGroups,
  draftSelectedColumns,
  draftColumnKeys,
  visibleColumns,
  removeDraftColumn,
} = useColumnConfig({
  pageKey: 'requirement_list',
  columns: requirementAllColumns,
  defaultKeys: requirementDefaultKeys,
  // 更新时间为必选列：节点流转/编辑都会刷新该时间，列表按其倒序展示
  requiredKeys: ['operations', 'updatedAt'],
})

// 配置
const configTypes = ref<any[]>([])
const configPriorities = ref<any[]>([])
const typeMap = ref<Record<string, string>>({})
const priorityMap = ref<Record<string, string>>({})
const priorityColorMap = ref<Record<string, string>>({})

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
    priorityColorMap.value = Object.fromEntries(
      priorityList
        .filter((p: any) => p.color)
        .map((p: any) => [p.code, p.color])
    )
  } catch {
    // ignore
  }
}

function typeLabel(code: string) {
  return typeMap.value[code] || code || '-'
}

const { priorityLabel, priorityTagType, priorityTagStyle } = useRequirementTag()

/** 本地包装：自动传入动态配置映射 */
function localPriorityLabel(code: string) {
  return priorityLabel(code, priorityMap.value)
}

function localPriorityTagType(priority: string): string | undefined {
  return priorityTagType(priority, priorityMap.value, priorityColorMap.value)
}

/** 优先级标签样式：直接使用"需求配置-优先级"中配置的 color */
function localPriorityTagStyle(priority: string) {
  return priorityTagStyle(priorityColorMap.value[priority])
}

/**
 * 获取需求行的当前处理人显示名
 * 优先使用工作流当前节点处理人（角色多人→角色名，单人→用户名），
 * 无工作流信息时回退到 assigneeName
 */
function currentHandlerDisplay(row: Requirement): string {
  const handler = currentHandlerMap.value.get(row.id)
  if (handler?.display) return handler.display
  // 兜底：无工作流实例或草稿状态
  return (row as any).currentHandlerName || row.assigneeName || '-'
}

// Filter user list
const filterUserList = ref<User[]>([])

async function loadFilterUsers() {
  try {
    const res = await userApi.getFilterUsers() as any
    filterUserList.value = res || []
  } catch (error) {
    console.error(error)
  }
}

// Filter form
const filterForm = reactive({
  type: '',
  priority: '',
  status: '',
  assigneeId: undefined as number | undefined,
  keyword: '',
  keywordScope: 'all',
  nodeStatus: '',
  isOverdue: false,
})

/**
 * 关键词搜索范围选项
 * 选择具体范围后，搜索只在对应字段内匹配（后端 keywordScope 参数）
 */
const keywordScopeOptions = [
  { value: 'all', label: '综合', hint: '需求标题 + 工单正文', placeholder: '关键词搜索' },
  { value: 'title', label: '标题', hint: '仅匹配需求标题', placeholder: '按需求标题搜索' },
  { value: 'requirementNo', label: '编号', hint: '仅匹配需求编号', placeholder: '按需求编号搜索' },
  { value: 'description', label: '正文', hint: '仅匹配工单正文', placeholder: '按工单正文搜索' },
  { value: 'assignee', label: '处理人', hint: '仅匹配当前处理人姓名', placeholder: '按当前处理人搜索' },
  { value: 'comment', label: '评论', hint: '仅匹配需求评论内容', placeholder: '按评论内容搜索' },
] as const

const keywordPlaceholder = computed(
  () => keywordScopeOptions.find(option => option.value === filterForm.keywordScope)?.placeholder ?? '关键词搜索',
)

/** 切换搜索范围：已输入关键词时立即按新范围重查 */
function handleKeywordScopeChange() {
  if (!filterForm.keyword) return
  handleSearch()
}

// 时间筛选
const timeDimension = ref<'createdAt' | 'analysisCompletedAt' | 'confirmAt' | 'developmentCompletedAt'>('createdAt')
const timeRange = ref<[string, string] | null>(null)
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

// Table data
const loading = ref(false)
const tableData = ref<Requirement[]>([])
/** 工作流当前节点处理人映射：requirementId → handler info */
const currentHandlerMap = ref<Map<number, CurrentNodeHandler>>(new Map())
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
  follows: 0,
  cc: 0,
})

// 时间维度筛选参数：按所选维度把日期范围映射到对应字段的起止参数
// （"全部需求"与"我的待办/已办/关注/抄送/全部/草稿"视图共用同一口径）
function applyTimeDimensionParams<T extends Record<string, any>>(target: T): T {
  if (!timeRange.value) return target
  const [start, end] = timeRange.value
  const keyMap: Partial<Record<typeof timeDimension.value, [string, string]>> = {
    createdAt: ['createdAtStart', 'createdAtEnd'],
    analysisCompletedAt: ['analysisCompletedAtStart', 'analysisCompletedAtEnd'],
    confirmAt: ['confirmAtStart', 'confirmAtEnd'],
    developmentCompletedAt: ['developmentCompletedAtStart', 'developmentCompletedAtEnd'],
  }
  const keys = keyMap[timeDimension.value]
  if (keys) {
    target[keys[0]] = start
    target[keys[1]] = end
  }
  return target
}

function buildMyListParams(): RequirementMyListQuery {
  return applyTimeDimensionParams({
    type: filterForm.type || undefined,
    priority: filterForm.priority || undefined,
    status: filterForm.status || undefined,
    assigneeId: filterForm.assigneeId,
    keyword: filterForm.keyword || undefined,
    keywordScope: filterForm.keyword ? filterForm.keywordScope : undefined,
    nodeStatus: filterForm.nodeStatus || undefined,
    isOverdue: filterForm.isOverdue || undefined,
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
  })
}

async function refreshViewCounts() {
  try {
    const res = await getTabBadgeCounts() as any
    const counts = res?.data ?? res
    viewCounts.pending = counts?.pending ?? 0
    viewCounts.follows = counts?.follows ?? 0
    viewCounts.cc = counts?.cc ?? 0
    viewCounts.drafts = counts?.drafts ?? 0
  } catch {
    // ignore count refresh failures
  }
}

// Fetch data
async function fetchData() {
  // 取消上一个未完成的请求，避免竞态
  if (fetchAbortController) {
    fetchAbortController.abort()
  }
  fetchAbortController = new AbortController()
  const currentController = fetchAbortController

  // 生成缓存键（含 activeRole：角色切换后数据口径不同，不能命中旧角色的缓存）
  const cacheKey = `${userStore.activeRole || ''}:${viewMode.value}:${pagination.pageNum}:${pagination.pageSize}:${JSON.stringify(filterForm)}:${timeDimension.value}:${timeRange.value || ''}`

  // 检查缓存（5分钟有效）— 缓存命中时直接展示，不发请求
  const cached = tabDataCache.get(cacheKey)
  if (cached && Date.now() - cached.timestamp < CACHE_EXPIRY_MS) {
    tableData.value = cached.data
    pagination.total = cached.total
    return
  }

  // Tab切换时：先展示该Tab的旧缓存数据（如有），再后台刷新
  const staleCacheKey = `${userStore.activeRole || ''}:${viewMode.value}:1:${pagination.pageSize}:${JSON.stringify(filterForm)}:${timeDimension.value}:${timeRange.value || ''}`
  const staleCached = tabDataCache.get(staleCacheKey)
  if (staleCached && tableData.value.length === 0) {
    tableData.value = staleCached.data
    pagination.total = staleCached.total
  }

  loading.value = true
  try {
    if (isDraftView.value) {
      const data = await requirementApi.getMyRequirementDrafts(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isPendingView.value) {
      const data = await getMyRequirementPending(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isDoneView.value) {
      const data = await getMyRequirementDone(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isFollowView.value) {
      const data = await getMyRequirementFollows(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      // 更新缓存
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isCcView.value) {
      const data = await getMyRequirementCc(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    if (isMyAllView.value) {
      const data = await getMyRequirementAll(buildMyListParams())
      tableData.value = data.list
      pagination.total = data.total
      setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
      return
    }

    const params: RequirementQuery = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    }
    if (filterForm.type) params.type = filterForm.type
    if (filterForm.priority) params.priority = filterForm.priority
    if (filterForm.status) params.status = filterForm.status
    if (filterForm.nodeStatus) params.nodeStatus = filterForm.nodeStatus
    if (filterForm.isOverdue) params.isOverdue = filterForm.isOverdue
    if (filterForm.assigneeId) params.assigneeId = filterForm.assigneeId
    if (filterForm.keyword) params.keyword = filterForm.keyword
    if (filterForm.keyword) params.keywordScope = filterForm.keywordScope

    applyTimeDimensionParams(params)

    const data = await requirementApi.getRequirementList(params)
    tableData.value = data.list
    pagination.total = data.total
    // 更新缓存
    setCache(cacheKey, { data: data.list, total: data.total, timestamp: Date.now() })
  } catch (err: any) {
    // 请求被取消（Tab切换/翻页）或后端已有明确错误提示（拦截器已弹），静默忽略
    if (err?.name === 'AbortError' || err?.message?.includes('cancel')) {
      return
    }
  } finally {
    loading.value = false
  }
}

/**
 * 批量加载当前页需求的工作流处理人信息
 * 用于列表"当前处理人"列根据工作流节点配置动态显示
 */
async function loadCurrentHandlers() {
  // 注意：RequirementListVO 不返回 workflowInstanceId 字段，
  // 不能用它过滤。后端 batchGetCurrentHandlers 内部会自动跳过
  // 没有运行中工作流实例的需求，这里只需排除草稿。
  const ids = tableData.value
    .filter(r => !r.isDraft)
    .map(r => r.id)
  if (!ids.length) {
    currentHandlerMap.value = new Map()
    return
  }
  try {
    const handlers = await workflowEngineApi.batchGetCurrentHandlers(ids)
    currentHandlerMap.value = new Map(handlers.map(h => [h.requirementId, h]))
  } catch {
    // 静默失败，列表仍可显示 assigneeName 兜底
    currentHandlerMap.value = new Map()
  }
}

/**
 * 监听列表数据变化，自动批量加载工作流处理人信息。
 *
 * 关键：fetchData 在缓存命中（tabDataCache 5 分钟有效）时会直接 return，
 * 之前手动调用会漏掉缓存命中、待办/已办/草稿等分支。
 * 改用 watch 统一触发，确保无论数据来自缓存还是新请求，
 * 只要 tableData 变化就刷新处理人映射。
 */
watch(tableData, () => {
  loadCurrentHandlers()
})

// Handlers

// 防抖定时器：handleSearch 和 handleReset 共享，避免快速连续触发时竞态
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function debouncedSearch(fn: () => void, wait = 300) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(fn, wait)
}

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
  filterForm.nodeStatus = ''
  filterForm.isOverdue = false
  filterForm.assigneeId = undefined
  filterForm.keyword = ''
  filterForm.keywordScope = 'all'
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
  else if (value === 'cc') query.view = 'cc'
  else if (value === 'my-all') query.view = 'my-all'
  router.replace({ query })
  // 主数据优先加载，视图计数异步刷新不阻塞
  fetchData()
  refreshViewCounts()
}

function handleCreate() {
  router.push({ name: 'RequirementCreate' })
}

function handleEdit(row: Requirement) {
  if (!hasPermission('button:requirement:update')) {
    toast.error('您没有编辑需求的权限')
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
  // 携带来源视图：详情页返回（返回按钮/面包屑/浏览器后退）时恢复对应 tab 列表
  const query: Record<string, string> = {}
  if (viewMode.value !== 'all') query.view = viewMode.value
  router.push({ name: 'RequirementDetail', params: { id }, query })
}

async function handleDelete(id: number) {
  try {
    await requirementApi.deleteRequirement(id)
    toast.success('需求已删除')
    fetchData()
  } catch {
    toast.error('删除失败')
  }
}

/** 判断是否可以删除需求：后端按 delete() 同款口径计算（草稿=创建人；非草稿=创建者或管理员+删除权限+无流转记录） */
function canDeleteRequirement(row: Requirement) {
  // 草稿状态：创建人可以删除（无需特殊权限）
  if (row.isDraft) {
    return row.creatorId === userStore.userInfo?.id
  }
  // 非草稿：以后端 canDelete 为准（含「已流转不可删」判定，避免渲染必然报错的按钮）
  return row.canDelete === true
}

async function handleToggleFollow(row: Requirement) {
  const nextFollowed = !row.followed
  try {
    if (!nextFollowed) {
      await requirementApi.unfollowRequirement(row.id)
      row.followed = false
      invalidateViewCache('follows')
      invalidateViewCache(viewMode.value)
      refreshViewCounts()
      toast.success('已取消关注')
      if (isFollowView.value) {
        fetchData()
      }
      return
    }
    await requirementApi.followRequirement(row.id)
    row.followed = true
    invalidateViewCache('follows')
    invalidateViewCache(viewMode.value)
    refreshViewCounts()
    toast.success('已添加关注')
  } catch {
    toast.error(nextFollowed ? '添加关注失败' : '取消关注失败')
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
    toast.success('批量删除成功')
    fetchData()
  } catch {
    // user cancelled or error
  }
}

// 导出状态：防止重复点击
const exporting = ref(false)
let exportAbortController: AbortController | null = null

async function handleExport() {
  // 防重：导出进行中时禁止再次点击
  if (exporting.value) {
    toast.warning('正在导出中，请勿重复操作')
    return
  }

  // 构建检索条件参数
  const params: RequirementQuery = {
    pageNum: 1,
    pageSize: pagination.pageSize,
  }
  if (filterForm.type) params.type = filterForm.type
  if (filterForm.priority) params.priority = filterForm.priority
  if (filterForm.status) params.status = filterForm.status
  if (filterForm.assigneeId) params.assigneeId = filterForm.assigneeId
  if (filterForm.keyword) params.keyword = filterForm.keyword
  if (filterForm.keyword) params.keywordScope = filterForm.keywordScope

  // 高级筛选：时间维度（各视图导出与列表共用同一口径）
  applyTimeDimensionParams(params)

  // 视图类型映射
  const viewMap: Record<string, string> = {
    all: 'all',
    drafts: 'drafts',
    pending: 'pending',
    done: 'done',
    follows: 'follows',
    cc: 'cc',
    'my-all': 'my-all',
  }
  const view = viewMap[viewMode.value] || 'all'

  // 构建导出列配置：只导出用户当前显示的列（排除 operations 操作列），保持显示顺序
  const exportColumnKeys = visibleColumns.value
    .filter(col => col.key !== 'operations')
    .map(col => col.key)

  // 创建 AbortController，支持取消正在进行的导出
  exportAbortController = new AbortController()
  exporting.value = true

  try {
    const response = await exportRequirementExcel(params, view, exportColumnKeys, exportAbortController.signal)

    // 从响应头获取文件名
    const contentDisposition = response.headers['content-disposition']
    let fileName = `需求列表_${new Date().toISOString().slice(0, 10)}.xlsx`
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*=UTF-8''(.+)/)
      if (match) {
        fileName = decodeURIComponent(match[1])
      }
    }

    // saveBlob 内部优先使用 File System Access API，用户取消时返回 false；否则降级为锚点下载
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const saved = await saveBlob(blob, fileName)
    if (!saved) {
      toast.info('已取消导出')
      return
    }

    toast.success('导出成功，文件已下载')
  } catch (err: any) {
    // 请求被取消（用户主动取消或新导出覆盖旧导出）
    if (err?.name === 'AbortError' || err?.code === 'ERR_CANCELED') {
      return
    }
    // 后端返回的业务错误（如数据量超限）
    if (err?.response?.status === 500 && err?.response?.data) {
      try {
        const errorData = typeof err.response.data === 'string'
          ? JSON.parse(err.response.data)
          : err.response.data
        if (errorData?.message) {
          toast.error(errorData.message)
          return
        }
      } catch { /* ignore */ }
    }
    toast.error('导出失败，请稍后重试')
  } finally {
    exporting.value = false
    exportAbortController = null
  }
}

// Tag type helpers — priorityTagType 已由 useRequirementTag 提供

function statusTagType(status: string): string {
  const map: Record<string, string> = {
    '新建': 'info', '待分析': 'warning', '待确认': 'warning', '待评审': 'warning',
    '评审中': 'warning', '已通过': 'success', '开发中': 'primary', '测试中': 'info',
    '已上线': 'success', '已验收': 'success', '已取消': 'info', '已拒绝': 'danger',
    '打回': 'danger', '测试不通过': 'danger', '验收不通过': 'danger',
  }
  return map[status] || 'info'
}

// 节点状态选择器已移除（与「状态」重复），但保留 nodeStatus 作为隐藏筛选值：
// 首页卡片深链 ?nodeStatus= 仍需生效——单码直接映射到「状态」下拉，多码（逗号串）走隐藏传参
const NODE_STATUS_LABEL_MAP: Record<string, string> = {
  DRAFT: '新建',
  PENDING_ANALYSIS: '待分析',
  PENDING_CONFIRM: '待确认',
  PENDING_REVIEW: '待评审',
  IN_DEVELOPMENT: '开发中',
  IN_TESTING: '测试中',
  DEPLOYED: '已上线',
  ACCEPTED: '已验收',
  CANCELLED: '已取消',
  REJECTED: '已拒绝',
  ROLLBACK: '打回',
  TEST_FAILED: '测试不通过',
  ACCEPTANCE_FAILED: '验收不通过',
}

/** 手动改「状态」时清掉隐藏的 nodeStatus，避免两个过滤条件同时下发给后端做 AND */
function handleStatusFilterChange() {
  filterForm.nodeStatus = ''
}

onMounted(async () => {
  // 并行加载独立的配置数据（不相互依赖）
  await Promise.all([
    loadFilterUsers(),
    loadConfig(),
    loadColumnConfig(),
  ])

  // 从 URL query 读取 nodeStatus / isOverdue 筛选条件（由首页/仪表盘卡片点击传入）
  const nodeStatusFromQuery = route.query.nodeStatus
  const isOverdueFromQuery = route.query.isOverdue
  if (nodeStatusFromQuery) {
    const raw = String(nodeStatusFromQuery)
    const codes = raw.split(',').map(s => s.trim()).filter(Boolean)
    if (codes.length === 1 && NODE_STATUS_LABEL_MAP[codes[0]]) {
      // 单码：映射为「状态」下拉值，界面上可见
      filterForm.status = NODE_STATUS_LABEL_MAP[codes[0]]
    } else {
      // 多码或未知码：保留隐藏传参（UI 上无对应选择器）
      filterForm.nodeStatus = raw
    }
  }
  if (isOverdueFromQuery !== undefined && isOverdueFromQuery !== null) {
    const overdueValue = Array.isArray(isOverdueFromQuery) ? isOverdueFromQuery[0] : isOverdueFromQuery
    filterForm.isOverdue = String(overdueValue) === 'true'
  }

  // 加载主数据
  // detail.vue 操作完成后跳回时，检测刷新标记并清除缓存
  const refreshCache = route.query._r === '1'
  if (refreshCache) {
    tabDataCache.clear()
    // 清除 URL 中的刷新标记
    router.replace({ query: { ...route.query, _r: undefined } })
  }

  await fetchData()

  // 异步加载非关键数据（不阻塞页面渲染）
  refreshViewCounts()
})

// 角色切换后重拉：待办/已办按生效角色过滤，缓存 key 也含 activeRole，
// 但同 key 下已渲染的旧数据必须主动刷新（页面不重挂，onMounted 不会再跑）
watch(() => userStore.activeRole, (role, oldRole) => {
  if (role === oldRole) return
  tabDataCache.clear()
  pagination.pageNum = 1
  fetchData()
  refreshViewCounts()
})

// 暴露刷新方法给 detail.vue 在操作完成后调用
defineExpose({ refreshViewCounts, invalidatePendingCache })

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
/* 优先级下拉选项颜色圆点（与创建页保持一致） */
.priority-option {
  display: flex;
  align-items: center;
  gap: 8px;
}
.priority-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ============================================
   响应式设计令牌系统
   ============================================ */
:root {
  --filter-gap-sm: 8px;
  --filter-gap-md: 16px;
  --filter-gap-lg: 24px;
  --filter-item-min-width: 160px;
  --filter-search-min-width: 240px;
}

/* ============================================
   需求管理控制台 - 紧凑商业化布局
   ============================================ */
.requirements-page {
  padding: 10px 14px 14px;
  gap: 8px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.requirement-control-card {
  :deep(.el-card__body) {
    padding: 8px 10px 10px;
  }

  :deep(.app-filter-card__body) {
    display: block;
  }
}

.requirement-table-card {
  flex: 1;
  margin-top: 0;

  :deep(.el-card__body) {
    padding: 10px;
  }
}

.requirement-control {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.requirement-control__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
}

/* ============================================
   视图切换区域 - 平铺标签栏
   ============================================ */
.view-switch-area {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

/* 视图标签栏：平铺全部视图 */
.view-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;

  /* el-button 相邻默认 margin-left:12px，会与 flex gap 叠加，抹平 */
  .view-tab + .view-tab {
    margin-left: 0;
  }

  .view-tab {
    position: relative;
    height: 30px;
    padding: 0 12px;
    font-size: 13px;
    font-weight: 500;
    border-radius: var(--radius-md);

    .view-tab__icon {
      font-size: 14px;
      margin-right: 4px;
    }

    .view-tab__text {
      white-space: nowrap;
    }

    &__badge {
      margin-left: 4px;

      :deep(.el-badge__content) {
        font-size: 10px;
        height: 14px;
        line-height: 14px;
        padding: 0 4px;
      }
    }
  }
}

/* ============================================
   操作按钮区域
   ============================================ */
.requirement-control__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;

  :deep(.el-button) {
    height: 30px;
    padding: 0 10px;
    border-radius: var(--radius-md);
    font-size: 12px;
  }

  :deep(.el-button.is-circle) {
    width: 30px;
    padding: 0;
  }
}

.requirement-primary-action {
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.18);
}

/* 更多操作下拉 */
.more-actions-dropdown {
  .more-actions-trigger {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;

    .more-actions__arrow {
      font-size: 10px;
      margin-left: 2px;
    }
  }
}

/* ============================================
   筛选表单主体 - 响应式网格布局
   ============================================ */
.filter-form {
  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: 0;
  }

  :deep(.el-form-item__content) {
    min-height: 30px;
  }
}

/* ── 筛选区域（整体可展开/收起） ── */
.filter-main {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 250ms ease;
  overflow: hidden;

  &.is-expanded {
    grid-template-rows: 1fr;
  }

  // 内部需要一个真正的包装层才能让 grid 0fr/1fr 动画生效
  > .filter-main__inner {
    min-height: 0;
  }
}

/* ── 基础筛选 ── */
.filter-basic {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: flex-end;

  .filter-item {
    flex: 0 0 auto;
    width: auto;
  }
}

/* ── 高级筛选（始终随主筛选区一起折叠，不再二级折叠） ── */
.filter-advanced {
  &__inner {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    align-items: flex-end;
    min-height: 0;
    padding-top: 8px;

    .filter-item {
      flex: 0 0 auto;
      width: auto;
    }

    .filter-item--date {
      width: auto;
      min-width: 0;
    }

    .filter-item--search {
      width: auto;

      :deep(.el-form-item__content) {
        width: auto;
      }
    }
  }
}

/* ============================================
   筛选项样式 - 流式宽度
   ============================================ */
.filter-item {
  display: inline-flex;
  align-items: center;
  min-width: 0;

  :deep(.el-form-item__label) {
    flex: 0 0 auto;
    width: auto;
    max-width: none;
    height: 30px;
    padding-right: 6px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 500;
    line-height: 30px;
    white-space: nowrap;
    overflow: visible;
  }

  :deep(.el-form-item__content) {
    flex: 0 0 auto;
    min-width: 0;
  }

  :deep(.el-select),
  :deep(.el-input),
  :deep(.el-date-editor) {
    width: auto;
    min-width: 0;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper) {
    min-width: 0;
    min-height: 30px;
    height: 30px;
    box-shadow: 0 0 0 1px var(--el-border-color) inset;
  }
}

.filter-item--type {
  :deep(.el-form-item__label) {
    min-width: 48px;
  }
}

.filter-item--priority,
.filter-item--status,
.filter-item--assignee,
.filter-item--dimension {
  :deep(.el-form-item__label) {
    min-width: 36px;
  }
}

.filter-item--date {
  :deep(.el-form-item__label) {
    min-width: 48px;
  }
}

.filter-item--search {
  :deep(.el-form-item__content) {
    width: 100%;
  }
}

// 各字段按实际展示内容设置紧凑宽度
.filter-select--type {
  width: 138px;

  :deep(.el-select__wrapper) {
    min-width: 138px;
  }
}

.filter-select--priority {
  width: 126px;

  :deep(.el-select__wrapper) {
    min-width: 126px;
  }
}

.filter-select--status {
  width: 142px;

  :deep(.el-select__wrapper) {
    min-width: 142px;
  }
}

.filter-select--assignee {
  width: 160px;

  :deep(.el-select__wrapper) {
    min-width: 160px;
  }
}

.filter-select--dimension {
  width: 142px;

  :deep(.el-select__wrapper) {
    min-width: 142px;
  }
}

.filter-date-range {
  width: 312px;
  min-width: 312px;

  :deep(.el-range-input) {
    min-width: 0;
  }

  :deep(.el-range-separator) {
    flex-shrink: 0;
  }
}

/* ============================================
   关键词搜索（搜索按钮嵌入 input append）
   提高优先级：.filter-item :deep(.el-input) 的 width:auto/min-width:0 会把本组件压扁，
   导致 prepend/append 单元格被压缩、内部定宽的下拉和按钮溢出互相遮挡
   ============================================ */
.filter-item--search .filter-input--keyword {
  width: 400px;
  min-width: 400px;
  flex-shrink: 0;

  :deep(.el-input__wrapper) {
    min-width: 0;
  }

  // prepend/append 是 flex 单元格，禁止收缩才能装下内部定宽的范围下拉与搜索按钮
  :deep(.el-input-group__prepend),
  :deep(.el-input-group__append) {
    flex-shrink: 0;
  }

  // 单元格宽度必须与内部定宽内容精确匹配（flex-basis 定死），
  // 否则 EP 的 flex 分配会把单元格压小于内容，内容居中溢出、左右互相遮挡
  :deep(.el-input-group__prepend) {
    flex: 0 0 92px;
    width: 92px;
  }

  :deep(.el-input-group__append) {
    flex: 0 0 62px;
    width: 62px;
  }

  // prepend 区域内的搜索范围下拉：去掉自身边框，与输入框融为一体
  :deep(.el-input-group__prepend) {
    padding: 0;
    background: var(--el-fill-color-light);
    border-right-color: var(--el-border-color);

    .el-select {
      width: 92px;
      // EP 默认 margin: 0 -20px 是为抵消单元格自带 20px padding 的；单元格 padding 已归零，
      // 保留负 margin 会让下拉比单元格宽 40px，向两侧溢出被邻块遮挡
      margin: 0;
    }

    .el-select__wrapper {
      min-height: 30px;
      padding: 0 24px 0 10px;
      background: transparent;
      box-shadow: none !important;
      font-size: 13px;
    }

    .el-select__suffix {
      right: 7px;
      font-size: 12px;
    }
  }

  // append 区域内的搜索按钮：主色填充 + 紧凑布局
  :deep(.el-input-group__append) {
    padding: 0;
    background: transparent;
    border: none;
    box-shadow: none;

    // 同 prepend：EP 默认的 margin: 0 -20px 会让按钮溢出单元格、盖住输入框的清除图标
    .el-button {
      margin: 0;
    }
  }
}

// 搜索范围下拉选项：左侧范围名称 + 右侧匹配说明
.keyword-scope-option__label {
  font-weight: 600;
}

.keyword-scope-option__hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.filter-search-append {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 62px;
  height: 30px;
  padding: 0 8px;
  border: none !important;
  border-radius: 0 var(--radius-md) var(--radius-md) 0 !important;
  background: var(--el-color-primary) !important;
  color: #fff !important;
  font-size: 12px;
  font-weight: 600;
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
   展开/收起切换按钮
   ============================================ */
.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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

/* ============================================
   响应式适配
   ============================================ */
@media (max-width: 767px) {
  .view-switch-area {
    flex-wrap: wrap;
  }

  .view-tabs {
    width: 100%;
    overflow-x: auto;

    &::-webkit-scrollbar {
      height: 0;
      display: none;
    }
  }

  .filter-basic,
  .filter-advanced__inner {
    display: grid;
    grid-template-columns: 1fr;
  }

  .filter-item,
  .filter-item--date,
  .filter-item--search {
    width: 100%;
    justify-self: stretch;
  }

  .filter-item {
    :deep(.el-select),
    :deep(.el-input),
    :deep(.el-date-editor) {
      width: 100%;
    }
  }

  .filter-date-range,
  .filter-item--search .filter-input--keyword {
    width: 100%;
    min-width: 0;
  }

  .filter-item--search {
    :deep(.el-form-item__content) {
      width: 100%;
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
    padding: 3px 6px;
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

// 列设置弹窗样式已迁至 src/styles/column-config.scss（全局）
</style>
