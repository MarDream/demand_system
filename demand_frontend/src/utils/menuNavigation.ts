import type { RouteLocationNormalizedLoaded } from 'vue-router'

type MenuAwareRoute = Pick<RouteLocationNormalizedLoaded, 'path' | 'name' | 'meta' | 'query'>

export function readRouteQueryString(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    const firstValue = value.find((item) => typeof item === 'string' && item.trim())
    return typeof firstValue === 'string' ? firstValue : undefined
  }

  return typeof value === 'string' && value.trim() ? value : undefined
}

export function resolveActiveMenuPath(route: MenuAwareRoute): string {
  const sourceMenu = readRouteQueryString(route.query?.sourceMenu)
  if (sourceMenu) {
    return sourceMenu
  }

  const activeMenu = route.meta?.activeMenu
  if (typeof activeMenu === 'string' && activeMenu.trim()) {
    return activeMenu
  }

  return route.path
}

export function resolveRouteBreadcrumbTitle(route: MenuAwareRoute): string {
  if (route.name === 'RequirementCreate') {
    return readRouteQueryString(route.query?.id) ? '编辑需求' : '新建需求'
  }

  if (route.name === 'WorkflowConfigEditor') {
    const mode = readRouteQueryString(route.query?.mode)
    if (mode === 'view') return '查看工作流'
    if (mode === 'edit') return '编辑工作流'
    return '新建工作流'
  }

  return typeof route.meta?.title === 'string' ? route.meta.title : ''
}
