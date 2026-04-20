import { useUserStore } from '@/stores/modules/user'

export function usePermission() {
  const userStore = useUserStore()

  function hasRole(role: string): boolean {
    return userStore.roles.includes(role) || userStore.roles.includes('admin')
  }

  function hasAnyRole(roles: string[]): boolean {
    return roles.some(r => hasRole(r))
  }

  return { hasRole, hasAnyRole }
}
