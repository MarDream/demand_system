<template>
  <div class="table-group-tree">
    <!-- 头部：新建分组 / 新建数据表 -->
    <div class="tgt-header">
      <span class="tgt-header__title">数据表</span>
      <el-button link class="tgt-header__btn" title="新建分组" @click="startCreate(null)">
        <el-icon><FolderAdd /></el-icon>
      </el-button>
      <el-button link class="tgt-header__btn" title="新建数据表" @click="emit('create-table', null)">
        <el-icon><Plus /></el-icon>
      </el-button>
    </div>

    <!-- 搜索 -->
    <div class="tgt-search">
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索数据表或分组"
        clearable
        :prefix-icon="Search"
      />
    </div>

    <!-- 树 -->
    <div class="tgt-list" @dragover.prevent @drop.prevent="handleRootDrop">
      <template v-for="node in nodes" :key="node.key">
        <!-- 行内新建分组输入 -->
        <div v-if="node.kind === 'editor'" class="tgt-editor" :style="{ paddingLeft: `${6 + node.depth * 14}px` }">
          <el-input
            v-model="editorValue"
            size="small"
            placeholder="输入分组名称"
            @keyup.enter="commitCreate"
            @keyup.esc="cancelCreate"
            @blur="commitCreate"
          />
        </div>

        <div
          v-else
          class="tgt-node"
          :class="[
            `tgt-node--${node.kind}`,
            {
              'is-active': node.isActive,
              'is-drop': dropKey === node.key,
              'is-reject': rejectKey === node.key,
              'is-dragging': dragKey === node.key,
            },
          ]"
          :style="{ paddingLeft: `${6 + node.depth * 14}px` }"
          :data-key="node.key"
          :draggable="node.draggable"
          @click="handleClick($event, node)"
          @dragstart="handleDragStart($event, node)"
          @dragend="handleDragEnd"
          @dragover="handleDragOver($event, node)"
          @dragleave="handleDragLeave(node)"
          @drop="handleDrop($event, node)"
        >
          <!-- 展开箭头 -->
          <span
            class="tgt-node__chev"
            :class="{ 'is-open': node.expanded, 'is-leaf': !node.hasChildren }"
            @click.stop="toggleExpand(node)"
          >
            <el-icon><ArrowRight /></el-icon>
          </span>

          <!-- 图标 -->
          <span class="tgt-node__icon" :class="`tgt-node__icon--${node.kind}`">
            <el-icon>
              <Grid v-if="node.kind === 'all'" />
              <FolderOpened v-else-if="node.kind === 'group' && node.expanded" />
              <Folder v-else-if="node.kind === 'group'" />
              <Folder v-else-if="node.kind === 'ungrouped'" />
              <Document v-else />
            </el-icon>
          </span>

          <!-- 名称（搜索时高亮命中片段） -->
          <span class="tgt-node__name" :title="node.name">
            <template v-for="(seg, i) in highlightSegments(node.name)" :key="i">
              <em v-if="seg.hit">{{ seg.text }}</em>
              <template v-else>{{ seg.text }}</template>
            </template>
          </span>

          <!-- 计数 -->
          <span v-if="node.kind !== 'table'" class="tgt-node__count">{{ node.count }}</span>

          <!-- 悬浮工具 -->
          <span v-if="node.quickAdd || node.menu.length" class="tgt-node__tools">
            <el-icon v-if="node.quickAdd" class="tgt-node__tool" title="新建子分组" @click.stop="startCreate(node.id)">
              <FolderAdd />
            </el-icon>
            <el-dropdown
              v-if="node.menu.length"
              trigger="click"
              placement="bottom-end"
              @command="(cmd: string) => handleCommand(cmd, node)"
            >
              <span class="tgt-node__tool" title="更多操作" @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in node.menu"
                    :key="item.command"
                    :command="item.command"
                    :divided="item.divided"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>
      </template>

      <div v-if="!nodes.length" class="tgt-empty">
        <span v-if="keyword">没有匹配的数据表或分组</span>
        <span v-else>暂无数据表，点上方 + 新建</span>
      </div>
    </div>

    <!-- 移动数据表到分组 -->
    <el-dialog v-model="moveDialogVisible" title="移动到分组" width="420px" append-to-body>
      <el-tree-select
        v-model="moveTargetGroupId"
        :data="moveOptions"
        :props="{ label: 'label', children: 'children' }"
        node-key="value"
        check-strictly
        default-expand-all
        placeholder="选择目标分组"
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMoveTable">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  Document,
  Folder,
  FolderAdd,
  FolderOpened,
  Grid,
  MoreFilled,
  Plus,
  Search,
} from '@element-plus/icons-vue'
import type { BitableTable, BitableTableGroup } from '@/types/bitable'

type NodeKind = 'all' | 'ungrouped' | 'group' | 'table' | 'editor'

interface MenuItem {
  command: string
  label: string
  divided?: boolean
}

interface FlatNode {
  key: string
  kind: NodeKind
  /** 分组ID 或 数据表ID；「全部实体」「未分组」为 null 或所属分组ID */
  id: number | null
  /** 数据表节点所属分组（用于拖动/归组），未分组为 null */
  parentGroupId: number | null
  name: string
  depth: number
  count?: number
  hasChildren: boolean
  expanded: boolean
  isActive: boolean
  draggable: boolean
  quickAdd?: boolean
  menu: MenuItem[]
}

const props = defineProps<{
  /** 当前多维表格ID，用于按 base 持久化折叠状态 */
  baseId: number
  groups: BitableTableGroup[]
  tables: BitableTable[]
  activeTableId: number | null
}>()

const emit = defineEmits<{
  select: [tableId: number]
  'create-group': [payload: { name: string; parentId: number | null }]
  'rename-group': [payload: { id: number; name: string }]
  'delete-group': [id: number]
  'move-group': [payload: { id: number; parentId: number | null }]
  'move-table': [payload: { tableId: number; groupId: number | null }]
  'create-table': [groupId: number | null]
  'rename-table': [tableId: number, name: string]
  'delete-table': [tableId: number]
}>()

/**
 * 折叠状态，key 形如 all / g:12 / ug:12 / ug:root；「全部实体」是平铺汇总视图，默认折叠。
 * 按 base 持久化到 localStorage，下次进入编辑器保持上次的展开形态。
 */
const COLLAPSED_STORAGE_PREFIX = 'bitable.tableGroupTree.collapsed.'

function loadCollapsedKeys(): Set<string> {
  const fallback = new Set<string>(['all'])
  try {
    const raw = localStorage.getItem(COLLAPSED_STORAGE_PREFIX + props.baseId)
    if (!raw) return fallback
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? new Set(parsed as string[]) : fallback
  } catch {
    return fallback
  }
}

const collapsedKeys = ref<Set<string>>(loadCollapsedKeys())

watch(
  collapsedKeys,
  (value) => {
    try {
      localStorage.setItem(COLLAPSED_STORAGE_PREFIX + props.baseId, JSON.stringify([...value]))
    } catch {
      /* 隐私模式等写入失败的场景直接忽略，不影响交互 */
    }
  },
  { deep: true },
)
const selectedGroupId = ref<number | null>(null)
const keyword = ref('')
const creating = ref<{ parentId: number | null } | null>(null)
const editorValue = ref('')

const dragKey = ref<string | null>(null)
const dropKey = ref<string | null>(null)
const rejectKey = ref<string | null>(null)
let dragging: FlatNode | null = null

const moveDialogVisible = ref(false)
const moveTableId = ref<number | null>(null)
const moveTargetGroupId = ref<number | null>(null)

/* ---------------- 基础查询 ---------------- */

/**
 * 接口返回的是嵌套树，这里拍平成列表，
 * 后续按 parentId 查找子分组、做拖拽防环校验都基于扁平结构
 */
const allGroups = computed<BitableTableGroup[]>(() => {
  const out: BitableTableGroup[] = []
  const walk = (list?: BitableTableGroup[]) => {
    ;(list || []).forEach((g) => {
      out.push(g)
      walk(g.children)
    })
  }
  walk(props.groups)
  return out
})

function ugKey(parentGroupId: number | null) {
  return parentGroupId == null ? 'ug:root' : `ug:${parentGroupId}`
}

function isExpanded(key: string) {
  return !collapsedKeys.value.has(key)
}

function childGroups(parentId: number | null): BitableTableGroup[] {
  return allGroups.value
    .filter((g) => (g.parentId ?? null) === parentId)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function tablesOfGroup(groupId: number | null): BitableTable[] {
  return props.tables.filter((t) => (t.groupId ?? null) === groupId)
}

function descendantGroupIds(groupId: number): number[] {
  const out: number[] = []
  const walk = (pid: number) => {
    childGroups(pid).forEach((g) => {
      out.push(g.id)
      walk(g.id)
    })
  }
  walk(groupId)
  return out
}

function isGroupDescendant(candidate: number, ancestor: number) {
  return descendantGroupIds(ancestor).includes(candidate)
}

function keywordLower() {
  return keyword.value.trim().toLowerCase()
}

/** 把名称按关键词切成片段，命中片段用于高亮（不依赖 v-html，避免 XSS） */
function highlightSegments(name: string): { text: string; hit: boolean }[] {
  const kw = keywordLower()
  if (!kw || !name) return [{ text: name, hit: false }]
  const lower = name.toLowerCase()
  const segments: { text: string; hit: boolean }[] = []
  let cursor = 0
  while (cursor < name.length) {
    const idx = lower.indexOf(kw, cursor)
    if (idx < 0) {
      segments.push({ text: name.slice(cursor), hit: false })
      break
    }
    if (idx > cursor) segments.push({ text: name.slice(cursor, idx), hit: false })
    segments.push({ text: name.slice(idx, idx + kw.length), hit: true })
    cursor = idx + kw.length
  }
  return segments.length ? segments : [{ text: name, hit: false }]
}

function groupMatches(group: BitableTableGroup): boolean {
  const kw = keywordLower()
  if (!kw) return true
  if (group.name.toLowerCase().includes(kw)) return true
  const ids = [group.id, ...descendantGroupIds(group.id)]
  return props.tables.some((t) => ids.includes(t.groupId ?? -1) && t.name.toLowerCase().includes(kw))
}

function tableMatches(table: BitableTable) {
  const kw = keywordLower()
  return !kw || table.name.toLowerCase().includes(kw)
}

function tableMenu(): MenuItem[] {
  return [
    { command: 'rename', label: '重命名' },
    { command: 'move', label: '移动到分组…' },
    { command: 'delete', label: '删除数据表', divided: true },
  ]
}

function groupMenu(): MenuItem[] {
  return [
    { command: 'create-sub', label: '新建子分组' },
    { command: 'create-table', label: '新建数据表' },
    { command: 'rename', label: '重命名', divided: true },
    { command: 'delete', label: '删除分组' },
  ]
}

/* ---------------- 扁平化树 ---------------- */

function toTableNode(table: BitableTable, depth: number): FlatNode {
  return {
    key: `t:${table.id}`,
    kind: 'table',
    id: table.id,
    parentGroupId: table.groupId ?? null,
    name: table.name,
    depth,
    hasChildren: false,
    expanded: false,
    isActive: table.id === props.activeTableId,
    draggable: true,
    menu: tableMenu(),
  }
}

function toUngroupedNode(
  parentGroupId: number | null,
  depth: number,
  total: number,
  expanded: boolean,
): FlatNode {
  return {
    key: ugKey(parentGroupId),
    kind: 'ungrouped',
    id: parentGroupId,
    parentGroupId,
    name: '未分组',
    depth,
    count: total,
    hasChildren: true,
    expanded,
    isActive: false,
    draggable: false,
    menu: [{ command: 'create-table', label: '新建数据表' }],
  }
}

function walkGroups(parentId: number | null, depth: number, out: FlatNode[]) {
  const kw = keywordLower()
  childGroups(parentId).forEach((group) => {
    if (!groupMatches(group)) return

    const subGroups = childGroups(group.id)
    const directTables = tablesOfGroup(group.id)
    const visibleTables = kw ? directTables.filter(tableMatches) : directTables
    const key = `g:${group.id}`
    const expanded = !!kw || isExpanded(key)

    out.push({
      key,
      kind: 'group',
      id: group.id,
      parentGroupId: group.parentId ?? null,
      name: group.name,
      depth,
      count: group.totalTableCount ?? group.tableCount ?? 0,
      hasChildren: subGroups.length > 0 || directTables.length > 0,
      expanded,
      isActive: selectedGroupId.value === group.id,
      draggable: true,
      quickAdd: true,
      menu: groupMenu(),
    })

    if (!expanded) return

    if (subGroups.length) {
      // 仅当存在子分组时才用「未分组」把直接挂载的表与子分区分开，避免无意义层级
      if (visibleTables.length) {
        const uKey = ugKey(group.id)
        const uExpanded = !!kw || isExpanded(uKey)
        out.push(toUngroupedNode(group.id, depth + 1, directTables.length, uExpanded))
        if (uExpanded) {
          visibleTables.forEach((t) => out.push(toTableNode(t, depth + 2)))
        }
      }
      walkGroups(group.id, depth + 1, out)
    } else {
      visibleTables.forEach((t) => out.push(toTableNode(t, depth + 1)))
    }
  })
}

const nodes = computed<FlatNode[]>(() => {
  const kw = keywordLower()
  const out: FlatNode[] = []

  // 1. 全部实体（平铺视图，默认折叠；搜索时不自动展开，避免同一张表重复出现三次）
  const allExpanded = isExpanded('all')
  out.push({
    key: 'all',
    kind: 'all',
    id: null,
    parentGroupId: null,
    name: '全部实体',
    depth: 0,
    count: props.tables.length,
    hasChildren: props.tables.length > 0,
    expanded: allExpanded,
    isActive: false,
    draggable: false,
    menu: [],
  })
  if (allExpanded) {
    props.tables.filter(tableMatches).forEach((t) => out.push(toTableNode(t, 1)))
  }

  // 2. 根层级未分组的数据表
  const rootTables = tablesOfGroup(null)
  const visibleRootTables = kw ? rootTables.filter(tableMatches) : rootTables
  if (visibleRootTables.length) {
    const key = ugKey(null)
    const expanded = !!kw || isExpanded(key)
    out.push(toUngroupedNode(null, 0, rootTables.length, expanded))
    if (expanded) {
      visibleRootTables.forEach((t) => out.push(toTableNode(t, 1)))
    }
  }

  // 3. 分组树
  walkGroups(null, 0, out)

  // 4. 新建分组的行内输入框
  if (creating.value) {
    const parentId = creating.value.parentId
    const editorNode: FlatNode = {
      key: 'editor',
      kind: 'editor',
      id: null,
      parentGroupId: parentId,
      name: '',
      depth: 0,
      hasChildren: false,
      expanded: false,
      isActive: false,
      draggable: false,
      menu: [],
    }
    if (parentId == null) {
      const firstRootGroup = out.findIndex((n) => n.kind === 'group' && n.depth === 0)
      editorNode.depth = 0
      out.splice(firstRootGroup < 0 ? out.length : firstRootGroup, 0, editorNode)
    } else {
      const index = out.findIndex((n) => n.kind === 'group' && n.id === parentId)
      if (index < 0) {
        out.push(editorNode)
      } else {
        editorNode.depth = out[index].depth + 1
        out.splice(index + 1, 0, editorNode)
      }
    }
  }

  return out
})

const moveOptions = computed(() => {
  const build = (parentId: number | null, depth: number): unknown[] =>
    childGroups(parentId).map((g) => {
      const children = build(g.id, depth + 1)
      return {
        value: g.id,
        label: `${'　'.repeat(depth)}${g.name}`,
        children: children.length ? children : undefined,
      }
    })
  return [{ value: -1, label: '未分组（根层级）', children: build(null, 1) }]
})

watch(
  () => props.activeTableId,
  (id) => {
    if (id == null) return
    const table = props.tables.find((t) => t.id === id)
    let current: number | null = table?.groupId ?? null
    if (current == null) return
    // 展开当前数据表所在分组的所有祖先，保证选中项可见
    const next = new Set(collapsedKeys.value)
    let guard = 0
    while (current != null && guard++ < 50) {
      next.delete(`g:${current}`)
      const group: BitableTableGroup | undefined = allGroups.value.find((g) => g.id === current)
      current = group?.parentId ?? null
    }
    // 祖先本就在展开态时 next 与当前集合等价，无需写回，
    // 否则进入页面（immediate 触发）会产生一次无意义的持久化写入
    if (next.size !== collapsedKeys.value.size) {
      collapsedKeys.value = next
    }
  },
  { immediate: true },
)

/* ---------------- 交互 ---------------- */

function toggleExpand(node: FlatNode) {
  if (!node.hasChildren) return
  const key = node.kind === 'group' ? `g:${node.id}` : node.key
  const next = new Set(collapsedKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  collapsedKeys.value = next
}

function handleClick(event: MouseEvent, node: FlatNode) {
  if ((event.target as HTMLElement).closest('.tgt-node__tools')) return
  if (node.kind === 'table' && node.id != null) {
    emit('select', node.id)
    return
  }
  if (node.kind === 'group') {
    selectedGroupId.value = node.id
  }
  toggleExpand(node)
}

function handleCommand(command: string, node: FlatNode) {
  if (node.kind === 'table') {
    if (command === 'rename' && node.id != null) {
      promptRenameTable(node.id, node.name)
    } else if (command === 'move' && node.id != null) {
      openMoveDialog(node.id, node.parentGroupId)
    } else if (command === 'delete' && node.id != null) {
      confirmDeleteTable(node.id, node.name)
    }
    return
  }
  if (command === 'create-sub') {
    startCreate(node.id)
  } else if (command === 'create-table') {
    emit('create-table', node.id)
  } else if (command === 'rename' && node.id != null) {
    promptRenameGroup(node.id, node.name)
  } else if (command === 'delete' && node.id != null) {
    confirmDeleteGroup(node.id, node.name, node.count ?? 0)
  }
}

/* ---------------- 新建 / 重命名 / 删除 ---------------- */

function startCreate(parentId: number | null) {
  if (parentId != null) {
    const next = new Set(collapsedKeys.value)
    next.delete(`g:${parentId}`)
    collapsedKeys.value = next
  }
  creating.value = { parentId }
  editorValue.value = ''
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('.tgt-editor input')
    input?.focus()
  })
}

function cancelCreate() {
  creating.value = null
  editorValue.value = ''
}

function commitCreate() {
  if (!creating.value) return
  const name = editorValue.value.trim()
  const parentId = creating.value.parentId
  creating.value = null
  editorValue.value = ''
  if (!name) return
  emit('create-group', { name, parentId })
}

function promptRenameGroup(id: number, currentName: string) {
  ElMessageBox.prompt('', '重命名分组', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: currentName,
    inputValidator: (value: string) => (value && value.trim() ? true : '分组名称不能为空'),
  })
    .then(({ value }) => {
      const name = (value || '').trim()
      if (name && name !== currentName) {
        emit('rename-group', { id, name })
      }
    })
    .catch(() => {})
}

function promptRenameTable(tableId: number, currentName: string) {
  ElMessageBox.prompt('', '重命名数据表', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: currentName,
    inputValidator: (value: string) => (value && value.trim() ? true : '数据表名称不能为空'),
  })
    .then(({ value }) => {
      const name = (value || '').trim()
      if (name && name !== currentName) {
        emit('rename-table', tableId, name)
      }
    })
    .catch(() => {})
}

function confirmDeleteGroup(id: number, name: string, count: number) {
  const extra = count > 0 ? `\n\n该分组下的 ${count} 个数据表与子分组将上移到父级，不会被删除。` : ''
  ElMessageBox.confirm(`确定删除分组「${name}」吗？${extra}`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('delete-group', id))
    .catch(() => {})
}

function confirmDeleteTable(id: number, name: string) {
  ElMessageBox.confirm(`确定删除数据表「${name}」吗？此操作将删除表内所有数据且不可恢复。`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('delete-table', id))
    .catch(() => {})
}

/* ---------------- 移动数据表 ---------------- */

function openMoveDialog(tableId: number, currentGroupId: number | null) {
  moveTableId.value = tableId
  moveTargetGroupId.value = currentGroupId ?? -1
  moveDialogVisible.value = true
}

function confirmMoveTable() {
  if (moveTableId.value == null) return
  const target = moveTargetGroupId.value
  const groupId = target == null || target === -1 ? null : target
  emit('move-table', { tableId: moveTableId.value, groupId })
  moveDialogVisible.value = false
}

/* ---------------- 拖拽 ---------------- */

function handleDragStart(event: DragEvent, node: FlatNode) {
  if (!node.draggable) return
  dragging = node
  dragKey.value = node.key
  event.dataTransfer?.setData('text/plain', node.key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function handleDragEnd() {
  dragging = null
  dragKey.value = null
  dropKey.value = null
  rejectKey.value = null
}

function resolveDropTarget(node: FlatNode): { ok: boolean; reason?: string } {
  if (!dragging || dragging.key === node.key) return { ok: false }
  if (node.kind !== 'group' && node.kind !== 'ungrouped' && node.kind !== 'all') return { ok: false }

  if (dragging.kind === 'table') {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部实体」是平铺视图，不能拖入' }
    }
    const targetGroupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    // 已在目标分组内，无需移动
    if ((dragging.parentGroupId ?? null) === targetGroupId) return { ok: false }
    return { ok: true }
  }

  if (dragging.kind === 'group' && dragging.id != null) {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部实体」是平铺视图，不能拖入' }
    }
    const targetParentId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    if (targetParentId === dragging.id) return { ok: false }
    if (targetParentId != null && isGroupDescendant(targetParentId, dragging.id)) {
      return { ok: false, reason: '不能把分组移到它自己的子分组下面' }
    }
    if ((dragging.parentGroupId ?? null) === targetParentId) return { ok: false }
    return { ok: true }
  }

  return { ok: false }
}

function handleDragOver(event: DragEvent, node: FlatNode) {
  if (!dragging) return
  // 必须始终 preventDefault，否则浏览器不触发 drop，拒绝原因也无法提示
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  const result = resolveDropTarget(node)
  if (result.ok) {
    rejectKey.value = null
    dropKey.value = node.key
  } else {
    dropKey.value = null
    rejectKey.value = result.reason ? node.key : null
  }
}

function handleDragLeave(node: FlatNode) {
  if (dropKey.value === node.key) dropKey.value = null
  if (rejectKey.value === node.key) rejectKey.value = null
}

function handleDrop(event: DragEvent, node: FlatNode) {
  event.stopPropagation()
  const source = dragging
  const result = resolveDropTarget(node)
  handleDragEnd()
  if (!source || !result.ok) {
    if (result.reason) ElMessage.warning(result.reason)
    return
  }

  if (source.kind === 'table' && source.id != null) {
    const groupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    emit('move-table', { tableId: source.id, groupId })
    return
  }
  if (source.kind === 'group' && source.id != null) {
    const parentId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    emit('move-group', { id: source.id, parentId })
  }
}

/** 拖到列表空白处 = 把分组移到根层级 */
function handleRootDrop() {
  const source = dragging
  if (!source || source.kind !== 'group' || source.id == null) return
  if ((source.parentGroupId ?? null) === null) return
  emit('move-group', { id: source.id, parentId: null })
  handleDragEnd()
}
</script>

<style scoped lang="scss">
.table-group-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-surface);
}

.tgt-header {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 12px 10px 8px 16px;

  .tgt-header__title {
    flex: 1;
    font-weight: var(--font-weight-semibold);
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
  }

  .tgt-header__btn {
    padding: 2px;
  }
}

.tgt-search {
  padding: 0 12px 8px;
}

.tgt-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 12px;
}

.tgt-editor {
  padding-top: 2px;
  padding-bottom: 4px;
  padding-right: 6px;
}

.tgt-node {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding-right: 6px;
  border-radius: var(--border-radius-md, 6px);
  cursor: pointer;
  user-select: none;
  color: var(--color-text-primary);
  transition: background 0.12s ease;

  &:hover {
    background: var(--color-surface-alt);
  }

  &.is-active {
    background: var(--color-primary-bg, rgba(37, 99, 235, 0.1));
    color: var(--color-primary);
    font-weight: var(--font-weight-medium, 500);
  }

  &.is-dragging {
    opacity: 0.4;
  }

  &.is-drop {
    background: var(--color-primary-subtle, #eff6ff);
    box-shadow: inset 0 0 0 1.5px var(--color-primary);
  }

  &.is-reject {
    background: var(--color-danger-bg, rgba(239, 68, 68, 0.12));
    box-shadow: inset 0 0 0 1.5px var(--color-danger);
  }

  .tgt-node__chev {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--color-muted-text);
    border-radius: 3px;
    transition: transform 0.15s ease;

    &.is-open {
      transform: rotate(90deg);
    }

    &.is-leaf {
      visibility: hidden;
    }
  }

  .tgt-node__icon {
    width: 16px;
    height: 16px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;

    &--group {
      color: #f59e0b;
    }

    &--ungrouped {
      color: var(--color-text-placeholder);
    }

    &--all {
      color: var(--color-primary);
    }

    &--table {
      color: var(--color-primary);
    }
  }

  .tgt-node__name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--font-size-sm);

    em {
      font-style: normal;
      background: #fef3c7;
      color: #92400e;
      border-radius: 2px;
      padding: 0 1px;
    }
  }

  .tgt-node__count {
    flex: none;
    font-size: 11px;
    line-height: 16px;
    padding: 0 6px;
    border-radius: 9px;
    color: var(--color-muted-text);
    background: var(--color-surface-alt);
  }

  .tgt-node__tools {
    display: none;
    align-items: center;
    flex: none;
  }

  &:hover .tgt-node__tools {
    display: flex;
  }

  .tgt-node__tool {
    width: 18px;
    height: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 3px;
    color: var(--color-muted-text);
    cursor: pointer;

    &:hover {
      background: rgba(15, 23, 42, 0.09);
      color: var(--color-text-primary);
    }
  }
}

.tgt-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-muted-text);
}
</style>
