<template>
  <el-drawer
    :model-value="visible"
    title="操作记录"
    size="520px"
    @close="emit('close')"
    @open="handleOpen"
  >
    <div class="operation-history">
      <!-- 筛选区 -->
      <div class="operation-history__filters">
        <div class="operation-history__filter-row">
          <el-select
            v-if="tableId"
            v-model="scope"
            size="small"
            style="width: 132px; flex: none"
            @change="reload"
          >
            <el-option label="当前数据表" value="table" />
            <el-option label="整个多维表格" value="base" />
          </el-select>
          <el-select
            v-model="operationType"
            size="small"
            clearable
            placeholder="全部操作类型"
            style="flex: 1; min-width: 0"
            @change="reload"
          >
            <el-option-group label="记录">
              <el-option label="新增记录" value="insert_record" />
              <el-option label="更新记录" value="update_record" />
              <el-option label="更新单元格" value="update_cell" />
              <el-option label="删除记录" value="delete_record" />
              <el-option label="导入记录" value="import_records" />
              <el-option label="导出记录" value="export_records" />
            </el-option-group>
            <el-option-group label="字段">
              <el-option label="新增字段" value="add_field" />
              <el-option label="更新字段" value="update_field" />
              <el-option label="删除字段" value="delete_field" />
            </el-option-group>
            <el-option-group label="视图">
              <el-option label="新增视图" value="add_view" />
              <el-option label="更新视图" value="update_view" />
              <el-option label="删除视图" value="delete_view" />
              <el-option label="开启视图分享" value="share_view" />
              <el-option label="关闭视图分享" value="unshare_view" />
            </el-option-group>
            <el-option-group label="数据表与容器">
              <el-option label="新增数据表" value="add_table" />
              <el-option label="更新数据表" value="update_table" />
              <el-option label="删除数据表" value="delete_table" />
              <el-option label="创建多维表格" value="create_base" />
              <el-option label="更新多维表格" value="update_base" />
            </el-option-group>
            <el-option-group label="成员">
              <el-option label="新增成员" value="add_member" />
              <el-option label="变更成员角色" value="update_member_role" />
              <el-option label="移除成员" value="remove_member" />
            </el-option-group>
          </el-select>
        </div>

        <div class="operation-history__filter-row">
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            size="small"
            unlink-panels
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :shortcuts="dateShortcuts"
            style="flex: 1; min-width: 0"
            @change="reload"
          />
          <el-select
            v-model="userFilter"
            size="small"
            clearable
            filterable
            placeholder="全部操作人"
            style="width: 148px; flex: none"
            @change="reload"
          >
            <el-option
              v-for="member in memberOptions"
              :key="member.userId"
              :label="member.userName || `用户${member.userId}`"
              :value="member.userId"
            />
          </el-select>
        </div>

        <!-- 工具行：计数 / 合并重复 / 展开收起 / 重置 -->
        <div class="operation-history__toolbar">
          <span class="operation-history__count">
            共 {{ total }} 条<template v-if="mergedCount > 0">，已合并 {{ mergedCount }} 组</template>
          </span>
          <span class="operation-history__toolbar-gap" />
          <el-tooltip content="相邻的同人同操作合并为一条，点击可展开" placement="top">
            <label class="operation-history__toggle">
              <el-switch v-model="mergeDuplicates" size="small" />
              <span>合并重复</span>
            </label>
          </el-tooltip>
          <el-button link type="primary" size="small" @click="toggleExpandAll">
            {{ allExpanded ? '全部收起' : '全部展开' }}
          </el-button>
          <el-button v-if="hasActiveFilter" link size="small" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <!-- 时间线 -->
      <div v-loading="loading" class="operation-history__body">
        <el-empty v-if="!loading && operations.length === 0" description="暂无操作记录" />

        <template v-for="bucket in groupedOperations" :key="bucket.date">
          <div class="operation-history__date" @click="toggleDate(bucket)">
            <el-icon class="operation-history__date-chev" :class="{ 'is-open': isDateExpanded(bucket) }">
              <ArrowRight />
            </el-icon>
            <span>{{ bucket.date }}</span>
            <span class="operation-history__date-count">{{ bucket.count }} 条</span>
          </div>

          <template v-if="isDateExpanded(bucket)">
            <div v-for="group in bucket.groups" :key="group.key" class="operation-item">
              <el-avatar :size="28" class="operation-item__avatar">
                {{ (group.items[0].userName || '?')[0] }}
              </el-avatar>

              <div class="operation-item__content">
                <!-- 摘要行：可展开时整行可点 -->
                <div
                  class="operation-item__line"
                  :class="{ 'is-clickable': group.expandable }"
                  @click="group.expandable && toggleExpand(group.key)"
                >
                  <span class="operation-item__user">{{ group.items[0].userName || '未知用户' }}</span>
                  <span class="operation-item__action">{{ describe(group.items[0]) }}</span>
                  <span v-if="group.merged" class="operation-item__badge">×{{ group.items.length }}</span>
                  <el-icon v-if="group.expandable" class="operation-item__chev" :class="{ 'is-open': isExpanded(group.key) }">
                    <ArrowRight />
                  </el-icon>
                </div>

                <!-- 时间：合并组显示区间 -->
                <div class="operation-item__time">{{ groupTimeText(group) }}</div>

                <!-- 展开区 -->
                <div v-if="group.expandable && isExpanded(group.key)" class="operation-item__changes">
                  <!-- 合并组：逐条列出被合并的操作（各自带时间） -->
                  <template v-if="group.merged">
                    <div v-for="op in group.items" :key="op.id" class="operation-item__child">
                      <span class="operation-item__child-time">{{ formatTime(op.createdAt) }}</span>
                      <div class="operation-item__child-body">
                        <div
                          v-for="(line, idx) in changedFieldLines(op)"
                          :key="idx"
                          class="operation-item__change-line"
                        >
                          <span class="operation-item__field">{{ line.fieldName }}</span>
                          <span class="operation-item__old">{{ line.oldText }}</span>
                          <span class="operation-item__arrow">→</span>
                          <span class="operation-item__new">{{ line.newText }}</span>
                        </div>
                      </div>
                    </div>
                  </template>

                  <!-- 单条：直接列字段级前后值 -->
                  <template v-else>
                    <div
                      v-for="(line, idx) in changedFieldLines(group.items[0])"
                      :key="idx"
                      class="operation-item__change-line"
                    >
                      <span class="operation-item__field">{{ line.fieldName }}</span>
                      <span class="operation-item__old">{{ line.oldText }}</span>
                      <span class="operation-item__arrow">→</span>
                      <span class="operation-item__new">{{ line.newText }}</span>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </template>
        </template>

        <div v-if="hasMore" class="operation-history__more">
          <el-button link type="primary" size="small" :loading="loading" @click="loadMore">
            加载更多
          </el-button>
        </div>
        <div v-else-if="operations.length > 0" class="operation-history__end">已加载全部</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { listBaseMembers, listOperations, listTableOperations } from '@/api/modules/bitable'
import type { BitableBaseMember, BitableField, BitableOperation, BitableOperationQuery } from '@/types/bitable'

const props = defineProps<{
  visible: boolean
  baseId: number | null
  /** 当前数据表 ID，提供后可切换"当前表 / 整个多维表格"范围 */
  tableId?: number | null
  /** 字段定义，用于把 detail 里的 fieldId 解析成字段名 */
  fields?: BitableField[]
}>()

const emit = defineEmits<{ close: [] }>()

const PAGE_SIZE = 30
/** 相邻的同类操作在该时间窗内才合并，避免把隔了很久的两次相同操作误并成一条 */
const MERGE_WINDOW_MS = 10 * 60 * 1000
/** 「全部展开」偏好持久化：用户上次选了展开，下次打开保持展开 */
const EXPAND_ALL_KEY = 'bitable.operationHistory.expandAll'
/** 「合并重复」是展示偏好而非筛选条件，同样持久化 */
const MERGE_KEY = 'bitable.operationHistory.mergeDuplicates'

const loading = ref(false)
const operations = ref<BitableOperation[]>([])
const pageNum = ref(1)
const total = ref(0)
const scope = ref<'table' | 'base'>('table')
const operationType = ref<string>('')
/** 时间范围（按天，闭区间），后端按 00:00:00 ~ 23:59:59 过滤 */
const timeRange = ref<[string, string] | null>(null)
const userFilter = ref<number | null>(null)
const members = ref<BitableBaseMember[]>([])
const mergeDuplicates = ref(localStorage.getItem(MERGE_KEY) !== '0')
const expandedKeys = ref<Set<string>>(new Set())
const expandAllPref = ref(localStorage.getItem(EXPAND_ALL_KEY) === '1')
/** 日期分组的显式展开/折叠记录；未记录的日期走默认规则：当天展开、其余折叠 */
const dateExpandedOverrides = ref<Map<string, boolean>>(new Map())

const hasMore = computed(() => operations.value.length < total.value)

/** 时间范围快捷选项（闭区间，含当天） */
const dateShortcuts = [
  { text: '今天', value: () => [new Date(), new Date()] },
  { text: '最近 7 天', value: () => [new Date(Date.now() - 6 * 86400000), new Date()] },
  { text: '最近 30 天', value: () => [new Date(Date.now() - 29 * 86400000), new Date()] },
]

/** 操作人下拉：当前多维表格的成员（按姓名排序） */
const memberOptions = computed(() =>
  [...members.value]
    .filter((m) => m.userId != null)
    .sort((a, b) => (a.userName || '').localeCompare(b.userName || '', 'zh-CN')),
)

const hasActiveFilter = computed(
  () =>
    !!operationType.value ||
    userFilter.value != null ||
    timeRange.value != null ||
    scope.value !== 'table',
)

interface DisplayGroup {
  key: string
  items: BitableOperation[]
  /** 是否由多条重复操作合并而来 */
  merged: boolean
  /** 是否有可展开的详情 */
  expandable: boolean
}

/**
 * 合并签名：同一个人 + 同一种操作 + 同一目标（字段/记录/数据表/名称）+ 同一个新值。
 * 值不同（如「工时 → 5」和「工时 → -0.04」）视为不同操作，不合并。
 */
function signature(op: BitableOperation): string {
  const parts: string[] = [op.operationType, String(op.userId ?? '')]
  const d = parseDetail(op.detail) as Record<string, unknown> | null
  if (d) {
    if (d.fieldId != null) parts.push('f' + String(d.fieldId))
    if (d.recordId != null) parts.push('r' + String(d.recordId))
    if (d.tableId != null) parts.push('t' + String(d.tableId))
    if (d.name != null) parts.push('n' + String(d.name))
    if (d.role != null) parts.push('role' + String(d.role))
    if (d.newValue !== undefined) parts.push('v' + JSON.stringify(d.newValue))
  }
  return parts.join('|')
}

const displayGroups = computed<DisplayGroup[]>(() => {
  const list = operations.value
  const out: DisplayGroup[] = []
  for (const op of list) {
    const last = out[out.length - 1]
    if (last && mergeDuplicates.value && signature(last.items[last.items.length - 1]) === signature(op)) {
      const prev = new Date(last.items[last.items.length - 1].createdAt).getTime()
      const cur = new Date(op.createdAt).getTime()
      if (Math.abs(prev - cur) <= MERGE_WINDOW_MS) {
        last.items.push(op)
        continue
      }
    }
    out.push({ key: `i:${op.id}`, items: [op], merged: false, expandable: false })
  }
  // 合并组换 key（用最新一条的 id，保持稳定），并标记可展开
  return out.map((g) => {
    const merged = g.items.length > 1
    return {
      key: merged ? `g:${g.items[0].id}` : g.key,
      items: g.items,
      merged,
      expandable: merged || changedFieldLines(g.items[0]).length > 0,
    }
  })
})

interface DateBucket {
  date: string
  /** 是否为当天分组（默认展开，其余日期默认折叠） */
  isToday: boolean
  /** 分组内操作条数（含被合并的），折叠时作为提示展示 */
  count: number
  groups: DisplayGroup[]
}

/** 按日期（天）分组，最新的在前；日期取组内最新一条 */
const groupedOperations = computed<DateBucket[]>(() => {
  const buckets: DateBucket[] = []
  const today = new Date()
  for (const group of displayGroups.value) {
    const date = formatDate(group.items[0].createdAt)
    let bucket = buckets.find((b) => b.date === date)
    if (!bucket) {
      bucket = { date, isToday: isSameDay(new Date(group.items[0].createdAt), today), count: 0, groups: [] }
      buckets.push(bucket)
    }
    bucket.groups.push(group)
    bucket.count += group.items.length
  }
  return buckets
})

const mergedCount = computed(() => displayGroups.value.filter((g) => g.merged).length)

const allExpanded = computed(() => {
  const expandableGroups = displayGroups.value.filter((g) => g.expandable)
  const collapsedDates = groupedOperations.value.filter((b) => !isDateExpanded(b))
  // 没有任何可展开内容（详情或折叠日期）时保持「全部展开」文案
  if (expandableGroups.length === 0 && collapsedDates.length === 0) return false
  return expandableGroups.every((g) => expandedKeys.value.has(g.key)) && collapsedDates.length === 0
})

function isExpanded(key: string) {
  return expandedKeys.value.has(key)
}

function toggleExpand(key: string) {
  const next = new Set(expandedKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedKeys.value = next
}

/** 日期分组默认仅当天展开，其余折叠；用户点击过的日期以显式记录为准 */
function isDateExpanded(bucket: DateBucket) {
  const override = dateExpandedOverrides.value.get(bucket.date)
  if (override != null) return override
  return bucket.isToday
}

function toggleDate(bucket: DateBucket) {
  const next = new Map(dateExpandedOverrides.value)
  next.set(bucket.date, !isDateExpanded(bucket))
  dateExpandedOverrides.value = next
}

function toggleExpandAll() {
  const next = !allExpanded.value
  expandAllPref.value = next
  try {
    localStorage.setItem(EXPAND_ALL_KEY, next ? '1' : '0')
  } catch {
    /* 隐私模式等写入失败的场景忽略，不影响交互 */
  }
  applyExpandPreference()
}

/**
 * 新加载/合并开关变化后，按偏好决定是否默认展开全部。
 * 「全部展开」连同日期分组一起展开；「全部收起」时日期回到默认（当天展开、其余折叠）。
 */
function applyExpandPreference() {
  if (!expandAllPref.value) {
    expandedKeys.value = new Set()
    dateExpandedOverrides.value = new Map()
    return
  }
  expandedKeys.value = new Set(displayGroups.value.filter((g) => g.expandable).map((g) => g.key))
  dateExpandedOverrides.value = new Map(groupedOperations.value.map((b) => [b.date, true]))
}

// 合并开关切换后分组 key 会变，展开态需要重算；同时记住这个展示偏好
watch(mergeDuplicates, (value) => {
  try {
    localStorage.setItem(MERGE_KEY, value ? '1' : '0')
  } catch {
    /* 隐私模式等写入失败的场景忽略，不影响交互 */
  }
  applyExpandPreference()
})

async function handleOpen() {
  scope.value = 'table'
  operationType.value = ''
  timeRange.value = null
  userFilter.value = null
  await loadMembers()
  reload()
}

function resetFilters() {
  operationType.value = ''
  timeRange.value = null
  userFilter.value = null
  scope.value = 'table'
  reload()
}

/** 操作人下拉数据源：当前多维表格的成员；拉取失败时降级为「已加载记录里出现过的用户」 */
async function loadMembers() {
  members.value = []
  if (!props.baseId) return
  try {
    members.value = (await listBaseMembers(props.baseId)) || []
  } catch {
    const seen = new Map<number, BitableBaseMember>()
    for (const op of operations.value) {
      if (op.userId != null && !seen.has(op.userId)) {
        seen.set(op.userId, { userId: op.userId, userName: op.userName } as BitableBaseMember)
      }
    }
    members.value = [...seen.values()]
  }
}

function reload() {
  pageNum.value = 1
  total.value = 0
  operations.value = []
  expandedKeys.value = new Set()
  dateExpandedOverrides.value = new Map()
  fetchPage()
}

function loadMore() {
  pageNum.value += 1
  fetchPage()
}

async function fetchPage() {
  if (!props.baseId) return
  loading.value = true
  try {
    const params: BitableOperationQuery = {
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
      operationType: operationType.value || undefined,
      userId: userFilter.value ?? undefined,
      startTime: timeRange.value?.[0] ? `${timeRange.value[0]}T00:00:00` : undefined,
      endTime: timeRange.value?.[1] ? `${timeRange.value[1]}T23:59:59` : undefined,
    }
    const pageResult =
      scope.value === 'table' && props.tableId
        ? await listTableOperations(props.tableId, params)
        : await listOperations(props.baseId, params)
    // 筛选/范围切换后重载时覆盖列表；加载更多时追加
    if (pageNum.value === 1) {
      operations.value = pageResult.list || []
      applyExpandPreference()
    } else {
      operations.value = operations.value.concat(pageResult.list || [])
      if (expandAllPref.value) applyExpandPreference()
    }
    total.value = pageResult.total
    // 成员接口不可用时，至少保证已出现过的操作人可被筛选
    if (!members.value.length) await loadMembers()
  } finally {
    loading.value = false
  }
}

/** 生成操作描述（类型标签 + detail 摘要） */
function describe(op: BitableOperation): string {
  const label = op.operationTypeLabel || op.operationType
  const detail = parseDetail(op.detail)
  switch (op.operationType) {
    case 'insert_record':
      return `新增记录 ${recordLabel(detail)}`
    case 'update_record':
      return `修改记录 ${recordLabel(detail)}`
    case 'update_cell':
      return `更新记录 ${recordLabel(detail)} 的单元格`
    case 'delete_record':
      return `删除记录 ${recordLabel(detail)}`
    case 'import_records': {
      const count = detail && typeof detail === 'object' && 'count' in detail ? (detail as Record<string, unknown>).count : null
      return `导入 ${count != null ? count + ' 条' : ''}记录`
    }
    case 'export_records': {
      const format = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).format : null
      return `导出数据表${format ? `（${String(format).toUpperCase()}）` : ''}`
    }
    case 'add_field': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `新增字段${name ? `「${name}」` : ''}`
    }
    case 'update_field': {
      const name = fieldName(detail)
      return `更新字段${name ? `「${name}」` : ''}`
    }
    case 'delete_field': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `删除字段${name ? `「${name}」` : ''}`
    }
    case 'add_view': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `新增视图${name ? `「${name}」` : ''}`
    }
    case 'update_view':
      return '更新视图配置'
    case 'delete_view': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `删除视图${name ? `「${name}」` : ''}`
    }
    case 'share_view': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).viewName : null
      return `开启视图分享${name ? `「${name}」` : ''}`
    }
    case 'unshare_view':
      return '关闭视图分享'
    case 'add_table': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `新增数据表${name ? `「${name}」` : ''}`
    }
    case 'update_table': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `更新数据表${name ? `「${name}」` : ''}`
    }
    case 'delete_table': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `删除数据表${name ? `「${name}」` : ''}`
    }
    case 'create_base': {
      const name = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).name : null
      return `创建多维表格${name ? `「${name}」` : ''}`
    }
    case 'update_base': {
      const d = detail && typeof detail === 'object' ? (detail as Record<string, unknown>) : null
      if (d && d.before != null && d.after != null && d.before !== d.after) {
        return `重命名多维表格：${String(d.before)} → ${String(d.after)}`
      }
      return '更新多维表格信息'
    }
    case 'add_member': {
      const role = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).role : null
      return `添加成员${role ? `（角色：${role}）` : ''}`
    }
    case 'update_member_role': {
      const role = detail && typeof detail === 'object' ? (detail as Record<string, unknown>).role : null
      return `变更成员角色${role ? `为 ${role}` : ''}`
    }
    case 'remove_member':
      return '移除成员'
    default:
      return label
  }
}

interface ChangeLine {
  fieldName: string
  oldText: string
  newText: string
}

/** 更新记录/单元格时展开的字段级前后值对比 */
function changedFieldLines(op: BitableOperation): ChangeLine[] {
  if (op.operationType !== 'update_record' && op.operationType !== 'update_cell') return []
  const detail = parseDetail(op.detail)
  if (!detail || typeof detail !== 'object') return []
  const d = detail as Record<string, unknown>
  if (op.operationType === 'update_cell') {
    const fieldName = resolveFieldName(d.fieldId)
    return [{ fieldName, oldText: '', newText: formatValue(d.newValue) }]
  }
  const changedFields = d.changedFields
  if (!changedFields || typeof changedFields !== 'object') return []
  const lines: ChangeLine[] = []
  for (const [fieldId, change] of Object.entries(changedFields as Record<string, unknown>)) {
    if (!change || typeof change !== 'object') continue
    const c = change as Record<string, unknown>
    lines.push({
      fieldName: resolveFieldName(/^\d+$/.test(fieldId) ? Number(fieldId) : fieldId),
      oldText: formatValue(c.oldValue),
      newText: formatValue(c.newValue),
    })
  }
  return lines.slice(0, 10)
}

function parseDetail(detail: unknown): unknown {
  if (detail && typeof detail === 'object') return detail
  if (typeof detail === 'string' && detail.startsWith('{')) {
    try {
      return JSON.parse(detail)
    } catch {
      return null
    }
  }
  return null
}

function recordLabel(detail: unknown): string {
  if (detail && typeof detail === 'object' && 'recordId' in detail) {
    return `#${String((detail as Record<string, unknown>).recordId)}`
  }
  return ''
}

function fieldName(detail: unknown): string {
  if (detail && typeof detail === 'object' && 'name' in detail) {
    const name = (detail as Record<string, unknown>).name
    if (typeof name === 'string') return name
  }
  if (detail && typeof detail === 'object' && 'fieldId' in detail) {
    return resolveFieldName((detail as Record<string, unknown>).fieldId)
  }
  return ''
}

function resolveFieldName(fieldId: unknown): string {
  if (fieldId == null) return '未知字段'
  const id = Number(fieldId)
  const field = props.fields?.find((f) => f.id === id)
  return field ? field.name : `字段#${String(fieldId)}`
}

/** 单元格值对象 {valueText|valueNumber|valueDate|...} → 人类可读文本 */
const CELL_VALUE_KEYS = [
  'valueText',
  'valueNumber',
  'valueDate',
  'valueBoolean',
  'valueSelect',
  'valueMultiSelect',
  'valueUser',
  'valueJson',
] as const

function formatValue(value: unknown): string {
  if (value == null || value === '') return '（空）'
  if (Array.isArray(value)) return value.length ? value.map(formatValue).join(', ') : '（空）'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') {
    const v = value as Record<string, unknown>
    const key = CELL_VALUE_KEYS.find((k) => k in v)
    if (key) {
      if (key === 'valueBoolean') return v.valueBoolean ? '是' : '否'
      if (key === 'valueJson') return JSON.stringify(v.valueJson)
      return formatValue(v[key])
    }
    // 兜底：单键对象取唯一值（后端会把值包一层），多键对象原样展示
    const keys = Object.keys(v)
    if (keys.length === 1) return formatValue(v[keys[0]])
    return JSON.stringify(v)
  }
  return String(value)
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function formatDate(createdAt: string): string {
  const d = new Date(createdAt)
  if (isSameDay(d, new Date())) return '今天'
  if (isSameDay(d, new Date(Date.now() - 24 * 3600 * 1000))) return '昨天'
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function formatTime(createdAt: string): string {
  const d = new Date(createdAt)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/** 合并组显示时间区间，单条显示时刻 */
function groupTimeText(group: DisplayGroup): string {
  if (!group.merged) return formatTime(group.items[0].createdAt)
  const times = group.items.map((o) => new Date(o.createdAt).getTime())
  const from = new Date(Math.min(...times))
  const to = new Date(Math.max(...times))
  const a = `${pad2(from.getHours())}:${pad2(from.getMinutes())}`
  const b = `${pad2(to.getHours())}:${pad2(to.getMinutes())}`
  return a === b ? a : `${a} ~ ${b}`
}
</script>

<style scoped>
.operation-history {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.operation-history__filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.operation-history__filter-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.operation-history__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.operation-history__toolbar-gap {
  flex: 1;
}

.operation-history__count {
  flex: none;
}

.operation-history__toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}

.operation-history__body {
  flex: 1;
  overflow-y: auto;
}

.operation-history__date {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  cursor: pointer;
  user-select: none;
}

.operation-history__date:hover {
  color: var(--el-text-color-primary);
}

.operation-history__date-chev {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  transition: transform 0.15s ease;
}

.operation-history__date-chev.is-open {
  transform: rotate(90deg);
}

.operation-history__date-count {
  margin-left: auto;
  font-weight: 400;
  color: var(--el-text-color-placeholder);
}

.operation-item {
  display: flex;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.operation-item:last-child {
  border-bottom: none;
}

.operation-item__avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-7);
  color: var(--el-color-primary);
  font-size: 13px;
}

.operation-item__content {
  flex: 1;
  min-width: 0;
}

.operation-item__line {
  font-size: 13px;
  line-height: 1.5;
  display: flex;
  align-items: center;
  gap: 6px;
}

.operation-item__line.is-clickable {
  cursor: pointer;
  border-radius: 4px;
}

.operation-item__line.is-clickable:hover {
  background: var(--el-fill-color-light);
}

.operation-item__user {
  font-weight: 600;
  color: var(--el-text-color-primary);
  flex: none;
}

.operation-item__action {
  color: var(--el-text-color-regular);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.operation-item__badge {
  flex: none;
  font-size: 11px;
  line-height: 16px;
  padding: 0 5px;
  border-radius: 9px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.operation-item__chev {
  flex: none;
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  transition: transform 0.15s ease;
}

.operation-item__chev.is-open {
  transform: rotate(90deg);
}

.operation-item__changes {
  margin-top: 4px;
  padding: 6px 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.operation-item__child {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.8;
}

.operation-item__child + .operation-item__child {
  margin-top: 2px;
  padding-top: 2px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.operation-item__child-time {
  flex: none;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
}

.operation-item__child-body {
  flex: 1;
  min-width: 0;
}

.operation-item__change-line {
  font-size: 12px;
  line-height: 1.8;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.operation-item__field {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.operation-item__old {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
  word-break: break-all;
}

.operation-item__arrow {
  color: var(--el-text-color-placeholder);
}

.operation-item__new {
  color: var(--el-color-success);
  word-break: break-all;
}

.operation-item__time {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  font-variant-numeric: tabular-nums;
}

.operation-history__more,
.operation-history__end {
  text-align: center;
  padding: 10px 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
