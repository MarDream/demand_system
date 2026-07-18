import { ref } from 'vue'
import { listBaseMembers } from '@/api/modules/bitable'
import type { MemberRole } from '@/types/bitable'

/**
 * 多维表格权限 composable
 * 用于在前端判断当前用户在指定 Base 中的角色和权限
 */
export function useBitablePermission() {
  const currentRole = ref<MemberRole | null>(null)
  const loading = ref(false)

  /**
   * 加载当前用户在指定 Base 中的角色
   * @param baseId 多维表格容器ID
   * @param userId 当前用户ID
   */
  async function loadRole(baseId: number, userId: number) {
    loading.value = true
    try {
      const members = await listBaseMembers(baseId)
      const member = members.find((m: any) => m.userId === userId)
      currentRole.value = member?.role ?? null
    } catch {
      currentRole.value = null
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置角色状态
   */
  function resetRole() {
    currentRole.value = null
  }

  /**
   * 是否有写入权限（EDITOR 及以上）
   */
  function canWrite(): boolean {
    if (currentRole.value === null) return false
    return ['owner', 'admin', 'editor'].includes(currentRole.value)
  }

  /**
   * 是否有管理权限（ADMIN 及以上）
   */
  function canManage(): boolean {
    if (currentRole.value === null) return false
    return ['owner', 'admin'].includes(currentRole.value)
  }

  /**
   * 是否为所有者
   */
  function isOwner(): boolean {
    return currentRole.value === 'owner'
  }

  /**
   * 是否有读取权限（VIEWER 及以上，即任何成员）
   */
  function canRead(): boolean {
    return currentRole.value !== null
  }

  /**
   * 是否有评论权限（COMMENTER 及以上）
   */
  function canComment(): boolean {
    if (currentRole.value === null) return false
    return ['owner', 'admin', 'editor', 'commenter'].includes(currentRole.value)
  }

  /**
   * 检查是否达到指定角色等级
   */
  function isAtLeast(role: MemberRole): boolean {
    if (currentRole.value === null) return false
    const levels: Record<MemberRole, number> = {
      owner: 5,
      admin: 4,
      editor: 3,
      commenter: 2,
      viewer: 1,
    }
    return (levels[currentRole.value] ?? 0) >= (levels[role] ?? 0)
  }

  return {
    currentRole,
    loading,
    loadRole,
    resetRole,
    canRead,
    canWrite,
    canManage,
    canComment,
    isOwner,
    isAtLeast,
  }
}
