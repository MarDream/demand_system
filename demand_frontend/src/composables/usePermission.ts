import { useUserStore } from '@/stores/modules/user'

export function usePermission() {
  const userStore = useUserStore()

  function hasRole(role: string): boolean {
    return userStore.isSuperAdmin || userStore.roles.includes(role) || userStore.roles.includes('admin')
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.some(role => hasRole(role))
  }

  function hasPermission(permission: string): boolean {
    return userStore.isSuperAdmin || userStore.permissions.includes(permission) || userStore.roles.includes('admin')
  }

  function hasAnyPermission(permissions: string[]): boolean {
    return permissions.some(permission => hasPermission(permission))
  }

  return { hasRole, hasAnyRole, hasPermission, hasAnyPermission }
}
