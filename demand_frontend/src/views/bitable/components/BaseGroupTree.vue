<template>
  <div class="bgtree">
    <!-- 头部：新建分组 -->
    <div class="bgtree-header">
      <span class="bgtree-header__title">多维表格</span>
      <el-button v-if="canManage" link class="bgtree-header__btn" title="新建分组" @click="startCreate(null)">
        <el-icon><FolderAdd /></el-icon>
      </el-button>
      <el-button link class="bgtree-header__btn" title="新建多维表格" @click="emit('create-base', null)">
        <el-icon><Plus /></el-icon>
      </el-button>
    </div>

    <!-- 搜索 -->
    <div class="bgtree-search">
      <el-input
        v-model="keyword"
        size="small"
        placeholder="搜索多维表格或分组"
        clearable
        :prefix-icon="Search"
      />
    </div>

    <!-- 树 -->
    <div class="bgtree-list" @dragover.prevent @drop.prevent="handleRootDrop">
      <template v-for="node in nodes" :key="node.key">
        <!-- 行内新建分组输入 -->
        <div v-if="node.kind === 'editor'" class="bgtree-editor" :style="{ paddingLeft: `${6 + node.depth * 14}px` }">
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
          class="bgtree-node"
          :class="[
            `bgtree-node--${node.kind}`,
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
            class="bgtree-node__chev"
            :class="{ 'is-open': node.expanded, 'is-leaf': !node.hasChildren }"
            @click.stop="toggleExpand(node)"
          >
            <el-icon><ArrowRight /></el-icon>
          </span>

          <!-- 图标 -->
          <span class="bgtree-node__icon" :class="`bgtree-node__icon--${node.kind}`">
            <el-icon>
              <Grid v-if="node.kind === 'all'" />
              <FolderOpened v-else-if="node.kind === 'group' && node.expanded" />
              <Folder v-else-if="node.kind === 'group'" />
              <Folder v-else-if="node.kind === 'ungrouped'" />
              <Document v-else />
            </el-icon>
          </span>

          <!-- 名称（搜索时高亮命中片段） -->
          <span class="bgtree-node__name" :title="node.name">
            <template v-for="(seg, i) in highlightSegments(node.name)" :key="i">
              <em v-if="seg.hit">{{ seg.text }}</em>
              <template v-else>{{ seg.text }}</template>
            </template>
          </span>

          <!-- 计数 -->
          <span v-if="node.kind !== 'base'" class="bgtree-node__count">{{ node.count }}</span>

          <!-- 悬浮工具 -->
          <span v-if="node.menu.length" class="bgtree-node__tools">
            <el-icon
              v-if="node.kind === 'group' && canManage"
              class="bgtree-node__tool"
              title="新建子分组"
              @click.stop="startCreate(node.id)"
            >
              <FolderAdd />
            </el-icon>
            <el-dropdown
              v-if="node.menu.length"
              trigger="click"
              placement="bottom-end"
              @command="(cmd: string) => handleCommand(cmd, node)"
            >
              <span class="bgtree-node__tool" title="更多操作" @click.stop>
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

      <div v-if="!nodes.length" class="bgtree-empty">
        <span v-if="keyword">没有匹配的多维表格或分组</span>
        <span v-else>暂无多维表格，点上方 + 新建</span>
      </div>
    </div>

    <!-- 移动多维表格到分组 -->
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
        <el-button type="primary" @click="confirmMoveBase">确定</el-button>
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
import type { BitableBase, BitableBaseGroup } from '@/types/bitable'

type NodeKind = 'all' | 'ungrouped' | 'group' | 'base' | 'editor'

interface MenuItem {
  command: string
  label: string
  divided?: boolean
}

interface FlatNode {
  key: string
  kind: NodeKind
  /** 分组ID 或 Base ID；「全部」「未分组」为 null 或所属分组ID */
  id: number | null
  /** Base 所属分组（用于拖动/归组），未分组为 null */
  parentGroupId: number | null
  name: string
  depth: number
  count?: number
  hasChildren: boolean
  expanded: boolean
  isActive: boolean
  draggable: boolean
  menu: MenuItem[]
}

const props = defineProps<{
  groups: BitableBaseGroup[]
  bases: BitableBase[]
  /** 当前选中范围（用于右侧卡片过滤） */
  selection: BaseTreeSelection
  /** 是否可管理分组（管理员） */
  canManage?: boolean
}>()

const emit = defineEmits<{
  select: [payload: BaseTreeSelection]
  'create-group': [payload: { name: string; parentId: number | null }]
  'rename-group': [payload: { id: number; name: string }]
  'delete-group': [id: number]
  'move-group': [payload: { id: number; parentId: number | null }]
  'move-base': [payload: { baseId: number; groupId: number | null }]
  'rename-base': [payload: { id: number; name: string }]
  'open-base': [baseId: number]
  'open-dashboard': [baseId: number]
  'delete-base': [base: BitableBase]
  'create-base': [groupId: number | null]
}>()

interface BaseTreeSelection {
  type: 'all' | 'group' | 'ungrouped'
  groupId: number | null
}

/**
 * 折叠状态，key 形如 all / g:12 / ug:12 / ug:root；「全部」是平铺汇总视图，默认折叠。
 * 持久化到 localStorage，下次进入保持上次的展开形态。
 */
const COLLAPSED_STORAGE_KEY = 'bitable.baseGroupTree.collapsed'

function loadCollapsedKeys(): Set<string> {
  const fallback = new Set<string>(['all'])
  try {
    const raw = localStorage.getItem(COLLAPSED_STORAGE_KEY)
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
      localStorage.setItem(COLLAPSED_STORAGE_KEY, JSON.stringify([...value]))
    } catch {
      /* 隐私模式等写入失败的场景直接忽略，不影响交互 */
    }
  },
  { deep: true },
)

const keyword = ref('')
const creating = ref<{ parentId: number | null } | null>(null)
const editorValue = ref('')

const dragKey = ref<string | null>(null)
const dropKey = ref<string | null>(null)
const rejectKey = ref<string | null>(null)
let dragging: FlatNode | null = null

const moveDialogVisible = ref(false)
const moveBaseId = ref<number | null>(null)
const moveTargetGroupId = ref<number | null>(null)

/* ---------------- 基础查询 ---------------- */

/**
 * 接口返回的是嵌套树，这里拍平成列表，
 * 后续按 parentId 查找子分组、做拖拽防环校验都基于扁平结构
 */
const allGroups = computed<BitableBaseGroup[]>(() => {
  const out: BitableBaseGroup[] = []
  const walk = (list?: BitableBaseGroup[]) => {
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

function childGroups(parentId: number | null): BitableBaseGroup[] {
  return allGroups.value
    .filter((g) => (g.parentId ?? null) === parentId)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
}

function basesOfGroup(groupId: number | null): BitableBase[] {
  return props.bases.filter((b) => (b.groupId ?? null) === groupId)
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

function baseMatches(base: BitableBase) {
  const kw = keywordLower()
  if (!kw) return true
  return (
    base.name.toLowerCase().includes(kw) ||
    (base.description || '').toLowerCase().includes(kw)
  )
}

function groupMatches(group: BitableBaseGroup): boolean {
  const kw = keywordLower()
  if (!kw) return true
  if (group.name.toLowerCase().includes(kw)) return true
  const ids = [group.id, ...descendantGroupIds(group.id)]
  return props.bases.some((b) => ids.includes(b.groupId ?? -1) && baseMatches(b))
}

function baseMenu(): MenuItem[] {
  return [
    { command: 'open', label: '打开' },
    { command: 'rename', label: '重命名' },
    { command: 'dashboard', label: '仪表盘', divided: true },
    { command: 'move', label: '移动到分组…' },
    { command: 'delete', label: '删除', divided: true },
  ]
}

function groupMenu(): MenuItem[] {
  return [
    { command: 'create-base', label: '新建多维表格' },
    { command: 'rename', label: '重命名', divided: true },
    { command: 'delete', label: '删除分组' },
  ]
}

/* ---------------- 选中态 ---------------- */

function isGroupActive(groupId: number) {
  return props.selection.type === 'group' && props.selection.groupId === groupId
}

function isUngroupedActive(parentGroupId: number | null) {
  return (
    props.selection.type === 'ungrouped' &&
    (props.selection.groupId ?? null) === (parentGroupId ?? null)
  )
}

/* ---------------- 扁平化树 ---------------- */

function toBaseNode(base: BitableBase, depth: number): FlatNode {
  return {
    key: `b:${base.id}`,
    kind: 'base',
    id: base.id,
    parentGroupId: base.groupId ?? null,
    name: base.name,
    depth,
    hasChildren: false,
    expanded: false,
    isActive: false,
    draggable: true,
    menu: baseMenu(),
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
    isActive: isUngroupedActive(parentGroupId),
    draggable: false,
    menu: [],
  }
}

function walkGroups(parentId: number | null, depth: number, out: FlatNode[]) {
  const kw = keywordLower()
  childGroups(parentId).forEach((group) => {
    if (!groupMatches(group)) return

    const subGroups = childGroups(group.id)
    const directBases = basesOfGroup(group.id)
    const visibleBases = kw ? directBases.filter(baseMatches) : directBases
    const key = `g:${group.id}`
    const expanded = !!kw || isExpanded(key)

    out.push({
      key,
      kind: 'group',
      id: group.id,
      parentGroupId: group.parentId ?? null,
      name: group.name,
      depth,
      count: group.totalBaseCount ?? group.baseCount ?? 0,
      hasChildren: subGroups.length > 0 || directBases.length > 0,
      expanded,
      isActive: isGroupActive(group.id),
      draggable: !!props.canManage,
      menu: groupMenu(),
    })

    if (!expanded) return

    if (subGroups.length) {
      // 仅当存在子分组时才用「未分组」把直接挂载的 Base 与子分区分开，避免无意义层级
      if (visibleBases.length) {
        const uKey = ugKey(group.id)
        const uExpanded = !!kw || isExpanded(uKey)
        out.push(toUngroupedNode(group.id, depth + 1, directBases.length, uExpanded))
        if (uExpanded) {
          visibleBases.forEach((b) => out.push(toBaseNode(b, depth + 2)))
        }
      }
      walkGroups(group.id, depth + 1, out)
    } else {
      visibleBases.forEach((b) => out.push(toBaseNode(b, depth + 1)))
    }
  })
}

const nodes = computed<FlatNode[]>(() => {
  const kw = keywordLower()
  const out: FlatNode[] = []

  // 1. 全部（平铺视图，默认折叠；搜索时不自动展开，避免同一个 Base 重复出现）
  const allExpanded = isExpanded('all')
  out.push({
    key: 'all',
    kind: 'all',
    id: null,
    parentGroupId: null,
    name: '全部',
    depth: 0,
    count: props.bases.length,
    hasChildren: props.bases.length > 0,
    expanded: allExpanded,
    isActive: props.selection.type === 'all',
    draggable: false,
    menu: [],
  })
  if (allExpanded) {
    props.bases.filter(baseMatches).forEach((b) => out.push(toBaseNode(b, 1)))
  }

  // 2. 根层级未分组的 Base
  const rootBases = basesOfGroup(null)
  const visibleRootBases = kw ? rootBases.filter(baseMatches) : rootBases
  if (visibleRootBases.length) {
    const key = ugKey(null)
    const expanded = !!kw || isExpanded(key)
    out.push(toUngroupedNode(null, 0, rootBases.length, expanded))
    if (expanded) {
      visibleRootBases.forEach((b) => out.push(toBaseNode(b, 1)))
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

/** 展开当前选中项的所有祖先分组，保证选中项可见 */
function revealSelection() {
  const next = new Set(collapsedKeys.value)
  if (props.selection.type === 'group' && props.selection.groupId != null) {
    let current: number | null = props.selection.groupId
    let guard = 0
    while (current != null && guard++ < 50) {
      next.delete(`g:${current}`)
      const group: BitableBaseGroup | undefined = allGroups.value.find((g) => g.id === current)
      current = group?.parentId ?? null
    }
  } else if (props.selection.type === 'ungrouped' && props.selection.groupId != null) {
    next.delete(ugKey(props.selection.groupId))
  }
  if (next.size !== collapsedKeys.value.size) {
    collapsedKeys.value = next
  }
}

watch(
  () => [props.selection.type, props.selection.groupId],
  () => revealSelection(),
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
  if ((event.target as HTMLElement).closest('.bgtree-node__tools')) return

  if (node.kind === 'base') {
    if (node.id != null) emit('open-base', node.id)
    return
  }
  if (node.kind === 'all') {
    emit('select', { type: 'all', groupId: null })
  } else if (node.kind === 'ungrouped') {
    emit('select', { type: 'ungrouped', groupId: node.parentGroupId })
  } else if (node.kind === 'group') {
    emit('select', { type: 'group', groupId: node.id })
  }
  toggleExpand(node)
}

function handleCommand(command: string, node: FlatNode) {
  if (node.kind === 'base') {
    if (command === 'open' && node.id != null) {
      emit('open-base', node.id)
    } else if (command === 'rename' && node.id != null) {
      promptRenameBase(node.id, node.name)
    } else if (command === 'dashboard' && node.id != null) {
      emit('open-dashboard', node.id)
    } else if (command === 'move' && node.id != null) {
      openMoveDialog(node.id, node.parentGroupId)
    } else if (command === 'delete' && node.id != null) {
      const base = props.bases.find((b) => b.id === node.id)
      if (base) emit('delete-base', base)
    }
    return
  }
  if (command === 'create-base') {
    emit('create-base', node.id)
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
    const input = document.querySelector<HTMLInputElement>('.bgtree-editor input')
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

/** 重命名多维表格（树节点上的快捷入口） */
function promptRenameBase(id: number, currentName: string) {
  // 标题已说明动作，输入框本身也是提示，无需再顶一行说明文案
  ElMessageBox.prompt('', '重命名多维表格', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: currentName,
    inputValidator: (value: string) => (value && value.trim() ? true : '名称不能为空'),
  })
    .then(({ value }) => {
      const name = (value || '').trim()
      if (name && name !== currentName) {
        emit('rename-base', { id, name })
      }
    })
    .catch(() => {})
}

function confirmDeleteGroup(id: number, name: string, count: number) {
  const extra = count > 0 ? `\n\n该分组下的 ${count} 个多维表格与子分组将上移到父级，不会被删除。` : ''
  ElMessageBox.confirm(`确定删除分组「${name}」吗？${extra}`, '删除确认', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => emit('delete-group', id))
    .catch(() => {})
}

/* ---------------- 移动 Base ---------------- */

function openMoveDialog(baseId: number, currentGroupId: number | null) {
  moveBaseId.value = baseId
  moveTargetGroupId.value = currentGroupId ?? -1
  moveDialogVisible.value = true
}

function confirmMoveBase() {
  if (moveBaseId.value == null) return
  const target = moveTargetGroupId.value
  const groupId = target == null || target === -1 ? null : target
  emit('move-base', { baseId: moveBaseId.value, groupId })
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

  if (dragging.kind === 'base') {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部」是平铺视图，不能拖入' }
    }
    const targetGroupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    // 已在目标分组内，无需移动
    if ((dragging.parentGroupId ?? null) === targetGroupId) return { ok: false }
    return { ok: true }
  }

  if (dragging.kind === 'group' && dragging.id != null) {
    if (node.kind === 'all') {
      return { ok: false, reason: '「全部」是平铺视图，不能拖入' }
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

  if (source.kind === 'base' && source.id != null) {
    const groupId = node.kind === 'ungrouped' ? node.parentGroupId ?? null : node.id
    emit('move-base', { baseId: source.id, groupId })
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
.bgtree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-surface);
}

.bgtree-header {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 12px 10px 8px 16px;

  .bgtree-header__title {
    flex: 1;
    font-weight: var(--font-weight-semibold);
    font-size: var(--font-size-sm);
    color: var(--color-text-primary);
  }

  .bgtree-header__btn {
    padding: 2px;
  }
}

.bgtree-search {
  padding: 0 12px 8px;
}

.bgtree-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 6px 12px;
}

.bgtree-editor {
  padding-top: 2px;
  padding-bottom: 4px;
  padding-right: 6px;
}

.bgtree-node {
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

  .bgtree-node__chev {
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

  .bgtree-node__icon {
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

    &--base {
      color: var(--color-primary);
    }
  }

  .bgtree-node__name {
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

  .bgtree-node__count {
    flex: none;
    font-size: 11px;
    line-height: 16px;
    padding: 0 6px;
    border-radius: 9px;
    color: var(--color-muted-text);
    background: var(--color-surface-alt);
  }

  .bgtree-node__tools {
    display: none;
    align-items: center;
    flex: none;
  }

  &:hover .bgtree-node__tools {
    display: flex;
  }

  .bgtree-node__tool {
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

.bgtree-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-muted-text);
}
</style>
