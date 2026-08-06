<template>
  <el-tree-select
    :model-value="modelValue"
    :data="treeData"
    :props="treeProps"
    :placeholder="placeholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="filterable"
    :loading="loading"
    node-key="nodeKey"
    check-strictly
    :render-after-expand="false"
    :style="{ width: '100%', ...(style || {}) }"
    @update:model-value="handleChange"
  >
    <template #default="{ data }">
      <!-- 分组节点：显示分组名（默认分组加前缀） -->
      <template v-if="data.isGroup">
        <span class="role-select__group-label">
          <span v-if="data.groupId === null" class="role-select__group-default">[默认]</span>
          {{ data.groupName }}
        </span>
      </template>
      <!-- 叶子节点（角色）：名称 + 权限预览图标 -->
      <template v-else>
        <span class="role-select__role-name">{{ data.name }}</span>
        <el-popover
          placement="right"
          :width="320"
          trigger="hover"
          :show-after="250"
          :hide-after="120"
          popper-class="role-select__popover"
          @show="handlePermIconEnter(data.id)"
        >
          <template #reference>
            <span class="role-select__perm-trigger" @click.stop>
              <el-icon><InfoFilled /></el-icon>
            </span>
          </template>
          <div class="role-select__perm-popup">
            <div class="role-select__perm-header">
              <span class="role-select__perm-title">{{ data.name }}</span>
              <span class="role-select__perm-meta">
                <template v-if="permLoading[data.id]">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>加载中…</span>
                </template>
                <template v-else-if="permMap[data.id] !== undefined">
                  共 {{ permMap[data.id].length }} 项权限
                </template>
              </span>
            </div>
            <div
              v-if="permMap[data.id] === undefined && !permLoading[data.id]"
              class="role-select__perm-loading"
            >
              鼠标移入图标即可加载
            </div>
            <div
              v-else-if="permMap[data.id] && !permMap[data.id].length"
              class="role-select__perm-empty"
            >
              该角色暂无权限
            </div>
            <el-scrollbar
              v-else-if="permMap[data.id] && permMap[data.id].length"
              max-height="320"
              class="role-select__perm-scrollbar"
            >
              <el-tree
                :data="permTreeMap[data.id] || []"
                :props="{ label: 'label', children: 'children' }"
                node-key="key"
                :default-expand-all="false"
                :expand-on-click-node="false"
                class="role-select__perm-tree"
              >
                <template #default="{ data: nodeData }">
                  <span class="role-select__perm-node">
                    <span class="role-select__perm-node-label">{{ nodeData.label }}</span>
                    <el-tag
                      v-if="nodeData.permCode"
                      size="small"
                      type="info"
                      effect="plain"
                      class="role-select__perm-code"
                    >
                      {{ nodeData.permCode }}
                    </el-tag>
                  </span>
                </template>
              </el-tree>
            </el-scrollbar>
          </div>
        </el-popover>
      </template>
    </template>
  </el-tree-select>
</template>

<script setup lang="ts">
/**
 * RoleSelect - 统一的角色选择器
 *
 * 按「角色组」分组展示角色树，支持单选（只允许选叶子节点）。
 * 自包含数据加载（内部调用 getRoleTree），调用方无需关心数据来源。
 * 多次实例化复用同一缓存，避免重复请求。
 *
 * 典型用法：
 *   <RoleSelect v-model="form.roleId" placeholder="请选择角色" />
 */
import { computed, onMounted, ref } from 'vue'
import type { CSSProperties } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled, Loading } from '@element-plus/icons-vue'
import { getRoleTree, getRolePermissions } from '@/api/modules/role'
import { getAllMenus, type MenuItem } from '@/api/modules/menu'

interface RoleNode {
  id: number
  name: string
  code: string
  isDefault?: number
  groupIds?: number[]
}

interface RoleGroupNode {
  groupId: number | null
  groupName: string
  isDefault?: number
  children: RoleNode[]
}

interface PermTreeNode {
  /** 唯一 key（菜单 id 或 permissionCode） */
  key: string
  /** 节点显示名 */
  label: string
  /** 叶子节点：是否对应一个权限点 */
  isLeaf: boolean
  /** 仅叶子节点带权限编码 */
  permCode?: string
  children: PermTreeNode[]
}

const props = withDefaults(
  defineProps<{
    /** 选中的角色 ID（叶子节点） */
    modelValue?: number | null
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    filterable?: boolean
    style?: CSSProperties
  }>(),
  {
    modelValue: undefined,
    placeholder: '请选择角色',
    disabled: false,
    clearable: true,
    filterable: true,
    style: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null | undefined]
  change: [value: number | null | undefined]
}>()

const treeProps = {
  // 叶子节点的字段（角色 id 来自 RoleNode.id）
  value: 'id',
  label: 'name',
  children: 'children',
  // 标记是否叶子节点：仅 RoleNode 有 id 字段（分组节点是 groupId）
  isLeaf: (data: any) => data?.isLeaf === true,
}

const loading = ref(false)
/**
 * el-tree-select 需要的扁平化数据：分组节点 + 角色节点混合
 * 我们使用两层结构：顶层是分组节点（groupId 字段），子节点是角色（id 字段）
 * 通过 isLeaf 区分；为避免 el-tree-select 把分组节点的 groupId 当作选中值（导致选不到角色），
 * 给分组节点注入一个虚拟的 groupKey 让其不参与 v-model 匹配。
 */
const treeData = ref<any[]>([])

/* ── 模块级缓存：同一会话多个 RoleSelect 实例共享一份数据 ── */
let cachedTree: any[] | null = null
let inflightPromise: Promise<any[]> | null = null

/**
 * 全量菜单的 permissionCode → 从根到该菜单项的链路（含每级菜单的 permissionCode）。
 * 模块级缓存，多实例共享；同一会话只拉一次。
 */
interface MenuPathEntry {
  id: number
  name: string
  permissionCode?: string
}
let menuPathMap: Map<string, MenuPathEntry[]> | null = null
let menuInflightPromise: Promise<Map<string, MenuPathEntry[]>> | null = null

async function loadMenuPathMap(): Promise<Map<string, MenuPathEntry[]>> {
  if (menuPathMap) return menuPathMap
  if (menuInflightPromise) return menuInflightPromise
  menuInflightPromise = (async () => {
    try {
      const res = (await getAllMenus()) as unknown as MenuItem[]
      const byId = new Map<number, MenuItem>()
      const collect = (m: MenuItem) => {
        byId.set(m.id, m)
        m.children?.forEach(collect)
      }
      res.forEach(collect)

      const pathMap = new Map<string, MenuPathEntry[]>()
      const getPath = (id: number): MenuPathEntry[] => {
        const path: MenuPathEntry[] = []
        let cur: MenuItem | undefined = byId.get(id)
        while (cur) {
          path.unshift({
            id: cur.id,
            name: cur.name,
            permissionCode: cur.permissionCode,
          })
          cur = cur.parentId ? byId.get(cur.parentId) : undefined
        }
        return path
      }

      byId.forEach((m) => {
        if (m.permissionCode) {
          pathMap.set(m.permissionCode, getPath(m.id))
        }
      })
      menuPathMap = pathMap
      return pathMap
    } finally {
      menuInflightPromise = null
    }
  })()
  return menuInflightPromise
}

async function loadRoleTree(force = false): Promise<any[]> {
  if (!force && cachedTree) return cachedTree
  if (!force && inflightPromise) return inflightPromise
  loading.value = true
  inflightPromise = (async () => {
    try {
      const res: any = await getRoleTree()
      const rawList: RoleGroupNode[] = Array.isArray(res) ? res : (res?.data ?? [])
      // 为每个分组节点加 isGroup=true 与虚拟 groupKey（避免与角色 id 冲突）
      // 同时注入虚拟 id（字符串，与数字角色 id 类型不同不会冲突），
      // 满足 el-tree-select props.value='id' 的类型校验，消除分组节点 id 为 undefined 的告警
      const normalized = rawList.map((group, idx) => {
        const groupVirtualId = `__group_${group.groupId ?? 'default'}_${idx}`
        return {
          groupId: group.groupId,
          groupName: group.groupName,
          isDefault: group.isDefault,
          isGroup: true,
          // 每个节点必须有 nodeKey 作为 Tree 内部索引
          nodeKey: groupVirtualId,
          id: groupVirtualId,
          children: (group.children || []).map((role, roleIdx) => ({
            ...role,
            isLeaf: true,
            nodeKey: `__role_${role.id}_${idx}_${roleIdx}`,
          })),
        }
      })
      cachedTree = normalized
      // 顺手预拉菜单映射（fire-and-forget，结果命中下次 hover）
      loadMenuPathMap().catch(() => null)
      return normalized
    } catch (err) {
      ElMessage.error('加载角色列表失败')
      return []
    } finally {
      loading.value = false
      inflightPromise = null
    }
  })()
  return inflightPromise
}

onMounted(() => {
  loadRoleTree().then((data) => {
    treeData.value = data
  })
})

/* ── 权限懒加载：按 roleId 缓存 ── */
const permMap = ref<Record<number, string[]>>({})
const permLoading = ref<Record<number, boolean>>({})

async function ensureRolePermissions(roleId: number): Promise<void> {
  if (permMap.value[roleId] !== undefined) return
  if (permLoading.value[roleId]) return
  // 同一组件实例内可能同时 hover 多个角色，用 update 整体替换避免脏写
  permLoading.value = { ...permLoading.value, [roleId]: true }
  try {
    // 顺手等一下菜单映射（如果还没准备好）
    await loadMenuPathMap()
    const res: any = await getRolePermissions(roleId)
    const codes: string[] = res?.permissionCodes ?? res?.data?.permissionCodes ?? []
    permMap.value = { ...permMap.value, [roleId]: codes }
  } catch {
    ElMessage.error('加载角色权限失败')
    permMap.value = { ...permMap.value, [roleId]: [] }
  } finally {
    permLoading.value = { ...permLoading.value, [roleId]: false }
  }
}

function handlePermIconEnter(roleId: number): void {
  void ensureRolePermissions(roleId)
}

/**
 * 根据角色权限码构建菜单树。
 *
 * 同名菜单既可能作为「父菜单」挂在权限码下面，也可能自身就是一个权限码
 * （比如「系统配置」既是顶级菜单，也对应 menu:system-config 这个权限点）。
 * 这种情况下该菜单在树中作为叶子节点（带 permCode），其它权限码挂在它的 children 下，
 * 避免重复显示两遍「系统配置」。
 */
function buildPermTree(codes: string[]): PermTreeNode[] {
  const pathMap = menuPathMap
  if (!pathMap) return []
  const codeSet = new Set(codes)
  const root: PermTreeNode[] = []
  const lookup = new Map<string, PermTreeNode>()
  codes.forEach((code) => {
    const path = pathMap.get(code)
    if (!path) return
    let parentList = root
    let parentKey = ''
    path.forEach((node, idx) => {
      const isPathLeaf = idx === path.length - 1
      const nodeIsPermPoint = !!node.permissionCode && codeSet.has(node.permissionCode)
      // 决策：当前节点是「独立权限码节点」还是「纯父菜单节点」
      const key = nodeIsPermPoint
        ? `code:${node.permissionCode!}`
        : `menu:${parentKey}/${node.id}`
      let treeNode = lookup.get(key)
      if (!treeNode) {
        treeNode = {
          key,
          label: node.name,
          // 自身就是权限码，或 path 终点且不是父 → 是叶子
          isLeaf: nodeIsPermPoint || isPathLeaf,
          permCode: nodeIsPermPoint
            ? node.permissionCode
            : isPathLeaf
              ? code
              : undefined,
          children: [],
        }
        lookup.set(key, treeNode)
        parentList.push(treeNode)
      }
      parentKey = key
      parentList = treeNode.children
    })
  })
  return root
}

/** 已加载的 roleId → 菜单树（响应式，跟随 permMap 变化） */
const permTreeMap = computed<Record<number, PermTreeNode[]>>(() => {
  if (!menuPathMap) return {}
  const result: Record<number, PermTreeNode[]> = {}
  Object.entries(permMap.value).forEach(([roleIdStr, codes]) => {
    const roleId = Number(roleIdStr)
    result[roleId] = buildPermTree(codes)
  })
  return result
})

/**
 * 选中值变化：仅当为叶子节点（角色 ID 数字）时向上抛；
 * 父节点不应被当作有效选择，避免脏值。
 */
function handleChange(val: any) {
  if (val === null || val === undefined) {
    emit('update:modelValue', null)
    emit('change', null)
    return
  }
  if (typeof val === 'number') {
    emit('update:modelValue', val)
    emit('change', val)
    return
  }
  // 字符串/其他值（误选了分组）→ 忽略并清除
  emit('update:modelValue', null)
  emit('change', null)
}

/* 暴露给上层：组件外主动重新拉取（角色组/角色变更后调用） */
defineExpose({
  refresh: () => loadRoleTree(true).then((data) => {
    treeData.value = data
  }),
})
</script>

<style scoped>
.role-select__group-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.role-select__group-default {
  color: var(--el-color-warning);
  margin-right: 4px;
}
.role-select__role-name {
  color: var(--el-text-color-primary);
}

/* ── 权限预览触发器（小图标） ── */
.role-select__perm-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-left: 6px;
  border-radius: 50%;
  cursor: help;
  color: var(--el-color-info);
  font-size: 13px;
  vertical-align: middle;
  transition: color 0.2s ease;
}
.role-select__perm-trigger:hover {
  color: var(--el-color-primary);
}
</style>

<style>
/* popover 内容不需要 scoped，因为它是 teleport 到 body 的 */
.role-select__popover .role-select__perm-popup {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.role-select__popover .role-select__perm-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.role-select__popover .role-select__perm-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 13px;
}
.role-select__popover .role-select__perm-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.role-select__popover .role-select__perm-loading,
.role-select__popover .role-select__perm-empty {
  text-align: center;
  padding: 24px 0;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.role-select__popover .role-select__perm-scrollbar {
  width: 100%;
}
.role-select__popover .role-select__perm-tree {
  font-size: 12px;
}
.role-select__popover .role-select__perm-tree .el-tree-node__content {
  height: 26px;
}
.role-select__popover .role-select__perm-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-primary);
}
.role-select__popover .role-select__perm-code {
  font-size: 11px;
  font-family: var(--el-font-family-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
}
</style>