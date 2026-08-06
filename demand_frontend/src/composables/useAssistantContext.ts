import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { resolveActiveMenuPath, resolveRouteBreadcrumbTitle } from '@/utils/menuNavigation'
import type { AssistantPageContext } from '@/types/assistant'

function resolveEntityType(routePath: string, routeName?: string) {
  switch (routeName) {
    case 'RequirementCreate':
    case 'RequirementDetail':
    case 'Requirements':
      return 'requirement'
    case 'RequirementConfig':
      return 'requirement-config'
    case 'RequirementTemplates':
      return 'requirement-template'
    case 'WorkflowConfig':
    case 'WorkflowConfigEditor':
    case 'WorkflowMigration':
      return 'workflow'
    case 'KnowledgeBases':
    case 'KnowledgeDetail':
      return 'knowledge'
    case 'KnowledgeSearch':
      return 'knowledge-search'
    case 'BitableList':
    case 'BitableEditor':
      return 'bitable'
    case 'UserManage':
      return 'user'
    case 'RoleManage':
      return 'role'
    case 'MenuManagement':
      return 'menu'
    case 'ProjectSettings':
      return 'project'
    case 'LlmConfig':
      return 'llm'
    default:
      break
  }

  if (routePath.startsWith('/settings/requirement-templates')) return 'requirement-template'
  if (routePath.startsWith('/settings/requirements')) return 'requirement-config'
  if (routePath.startsWith('/requirements')) return 'requirement'
  if (routePath.startsWith('/iterations')) return 'iteration'
  if (routePath.startsWith('/settings/knowledge/search')) return 'knowledge-search'
  if (routePath.startsWith('/settings/knowledge')) return 'knowledge'
  if (routePath.startsWith('/system/workflow-config')) return 'workflow'
  if (routePath.startsWith('/system/workflow-migration')) return 'workflow'
  if (routePath.startsWith('/settings/llm')) return 'llm'
  if (routePath.startsWith('/settings/projects')) return 'project'
  if (routePath.startsWith('/settings/users')) return 'user'
  if (routePath.startsWith('/settings/roles')) return 'role'
  if (routePath.startsWith('/settings/menus')) return 'menu'
  if (routePath.startsWith('/bitable')) return 'bitable'
  return undefined
}

function resolveEntityId(params: Record<string, unknown>) {
  const key = ['id', 'baseId', 'projectId', 'requirementId'].find((paramKey) => params?.[paramKey] != null)
  if (!key) return undefined
  const raw = params[key]
  if (Array.isArray(raw)) return raw[0] ? String(raw[0]) : undefined
  return raw != null ? String(raw) : undefined
}

export function useAssistantContext() {
  const route = useRoute()
  const appStore = useAppStore()

  const currentPageContext = computed<AssistantPageContext>(() => {
    const activeMenu = resolveActiveMenuPath(route)
    const pageTitle = resolveRouteBreadcrumbTitle(route)
      || (typeof route.meta?.title === 'string' ? route.meta.title : '')
      || appStore.getMenuNameByPath(activeMenu)
      || String(route.name || '')

    return {
      route: route.path,
      routeName: route.name ? String(route.name) : undefined,
      pageTitle: pageTitle || undefined,
      activeMenu,
      entityType: resolveEntityType(route.path, route.name ? String(route.name) : undefined),
      entityId: resolveEntityId(route.params as Record<string, unknown>),
    }
  })

  return {
    currentPageContext,
  }
}
