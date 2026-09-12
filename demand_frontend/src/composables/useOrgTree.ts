import { ref } from 'vue'
import { getOrgTree } from '@/api/modules/user'
import type { OrgNode } from '@/types/user'

/**
 * 组织树通用能力：统一"加载组织树 + 解包响应 + 节点查找"的重复实现。
 *
 * - orgTree: 共享的响应式树数据（模块级，多组件复用同一次请求结果）
 * - loadOrgTree(force?): 拉取组织树，默认进程内缓存，传 true 强制刷新
 * - findOrgNodeById: 按 id 深度查找节点
 * - extractOrgNodesByType: 按组织类型（company/dept 等）扁平提取节点
 */

/** 共享树数据：所有消费方引用同一个响应式数组 */
const sharedTree = ref<OrgNode[]>([])
/** 进行中的请求 Promise，避免并发重复请求 */
let pendingRequest: Promise<OrgNode[]> | null = null

/** 解包接口返回：兼容数组、{ data: 数组 }、{ data: { data: 数组 } } 三种形态 */
export function normalizeArray<T>(value: unknown): T[] {
  if (Array.isArray(value)) return value as T[]
  const data = (value as any)?.data
  if (Array.isArray(data)) return data as T[]
  if (Array.isArray(data?.data)) return data.data as T[]
  return []
}

/** 按 id 深度优先查找组织节点 */
export function findOrgNodeById(nodes: OrgNode[], targetId: number | null | undefined): OrgNode | null {
  if (!targetId) return null
  for (const node of nodes) {
    if (node.id === targetId) return node
    if (node.children?.length) {
      const matched = findOrgNodeById(node.children, targetId)
      if (matched) return matched
    }
  }
  return null
}

/** 按组织类型扁平提取节点（去掉子级），如提取全部公司节点作为下拉项 */
export function extractOrgNodesByType(nodes: OrgNode[], targetType: OrgNode['orgType']): OrgNode[] {
  const result: OrgNode[] = []
  const walk = (items: OrgNode[]) => {
    items.forEach(item => {
      if (item.orgType === targetType) {
        result.push({ ...item, children: undefined })
      }
      if (item.children?.length) {
        walk(item.children)
      }
    })
  }
  walk(nodes)
  return result
}

/**
 * 加载组织树。默认使用共享缓存（同一会话只请求一次）；
 * 传 force=true 跳过缓存强制刷新。
 */
export async function loadOrgTree(force = false): Promise<OrgNode[]> {
  if (!force && sharedTree.value.length > 0) {
    return sharedTree.value
  }
  if (!pendingRequest) {
    pendingRequest = getOrgTree()
      .then((res) => {
        const tree = normalizeArray<OrgNode>(res)
        sharedTree.value = tree
        return tree
      })
      .finally(() => {
        pendingRequest = null
      })
  }
  return pendingRequest
}

/** 组织树 composable：为组件提供共享树数据与刷新能力 */
export function useOrgTree() {
  return {
    orgTree: sharedTree,
    loadOrgTree,
    findOrgNodeById,
    extractOrgNodesByType,
  }
}
