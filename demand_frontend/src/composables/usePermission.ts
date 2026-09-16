import { useUserStore } from '@/stores/modules/user'

export function usePermission() {
  const userStore = useUserStore()

  // 角色切换激活态下严格按服务端过滤后的角色/权限判断，
  // 不再走 admin/SUPER_ADMIN 直通，否则切换即失效
  function hasRole(role: string): boolean {
    if (userStore.activeRole) {
      return userStore.isSuperAdmin || userStore.roles.includes(role)
    }
    return userStore.isSuperAdmin || userStore.roles.includes(role) || userStore.roles.includes('admin')
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.some(role => hasRole(role))
  }

  function hasPermission(permission: string): boolean {
    if (userStore.activeRole) {
      return userStore.isSuperAdmin || userStore.permissions.includes(permission)
    }
    return userStore.isSuperAdmin || userStore.permissions.includes(permission) || userStore.roles.includes('admin')
  }

  function hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(permission => hasPermission(permission))
  }

  return { hasRole, hasAnyRole, hasPermission, hasAnyPermission }
}
