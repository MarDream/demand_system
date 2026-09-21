import { ref } from 'vue'
import { getFilterUsers } from '@/api/modules/user'
import { resolveAvatarUrl } from '@/utils/presetAvatars'
import type { BitableField } from '@/types/bitable'

/**
 * 人员字段（fieldType='user'）的可选项 = 系统活跃用户列表。
 *
 * 此前 user 字段在表单/详情里被当成文本输入或落入只读区，现在统一走：
 * getFilterUsers()（GET /v1/users/active：id/username/realName/avatar/orgId）+
 * 按字段属性 userScope 过滤。
 *
 * 缓存共享：组件树里任意入口首次拉取后，后续直接复用，避免弹框反复发请求。
 */
// 共享用户缓存：模块级单例 ref，bitable 组件直接导入使用（.value 读取）
export const allUsers = ref<UserOption[]>([])
let loadingPromise: Promise<void> | null = null

export interface UserOption {
  id: number
  username: string
  realName: string
  /** 已解析为可直接绑定 :src 的 URL（preset: 前缀转 data/静态资源 URL） */
  avatar?: string | null
  /** 所属组织（org 树节点，即部门）ID，用于 userScope='dept' 过滤 */
  orgId?: number | null
}

/** 拉取系统用户列表（并发去重；失败静默为空列表，不阻塞表单渲染） */
export async function ensureUsersLoaded(): Promise<void> {
  if (allUsers.value.length) return
  if (loadingPromise) return loadingPromise
  loadingPromise = (async () => {
    try {
      const res = await getFilterUsers()
      const list = Array.isArray(res) ? res : (res as any)?.data || []
      allUsers.value = list.map((u: any) => ({
        id: Number(u.id),
        username: String(u.username ?? ''),
        realName: String(u.realName || u.username || `用户${u.id}`),
        // 后端存的是 preset:xxx 原始串，直接绑 :src 会 ERR_UNKNOWN_URL_SCHEME
        avatar: resolveAvatarUrl(u.avatar ?? null),
        orgId: u.orgId != null ? Number(u.orgId) : null,
      }))
    } catch {
      allUsers.value = []
    } finally {
      loadingPromise = null
    }
  })()
  return loadingPromise
}

/**
 * 按字段属性 userScope 过滤可选项：
 * - all（默认）：全组织
 * - dept：指定部门（userDeptIds 命中用户 orgId，含其在 org 树上的后代节点不在
 *   精简接口里，因此只做直接命中；后续如需「含子部门」需在下发 org 树后展开）
 * - self：仅当前登录用户
 */
export function filterUsersByScope(
  users: UserOption[],
  field: BitableField,
  currentUserId?: number | null,
): UserOption[] {
  const config = field.config || {}
  if (config.userScope === 'self') {
    if (currentUserId == null) return []
    return users.filter((u) => u.id === currentUserId)
  }
  if (config.userScope === 'dept') {
    const deptIds = (config.userDeptIds || []).map(Number)
    if (!deptIds.length) return []
    return users.filter((u) => u.orgId != null && deptIds.includes(u.orgId))
  }
  return users
}
