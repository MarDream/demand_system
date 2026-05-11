<template>
  <div class="settings-container">
    <div class="settings-header">
      <h2>系统配置</h2>
      <p class="settings-desc">管理系统项目、用户、组织、需求配置、工作流与知识库能力</p>
    </div>
    <el-row :gutter="20" class="settings-cards">
      <el-col v-for="item in visibleCards" :key="item.path" :span="8">
        <el-card shadow="hover" class="settings-card" @click="$router.push(item.path)">
          <div class="card-content">
            <el-icon :size="48" :color="item.color"><component :is="item.icon" /></el-icon>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
            <el-button :type="item.buttonType" text>进入管理 &rarr;</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'
import { usePermission } from '@/composables/usePermission'
import { getCurrentMenus, type MenuItem } from '@/api/modules/menu'

const { hasPermission, hasAnyRole, hasAnyPermission } = usePermission()

// path -> 卡片额外配置（描述、颜色等不从菜单获取的部分）
const cardMeta: Record<string, { description: string; color: string; buttonType: string }> = {
  '/settings/projects': { description: '创建、编辑和管理项目，配置项目成员', color: '#409EFF', buttonType: 'primary' },
  '/settings/users': { description: '管理成员账号、部门组织、角色权限和账号状态', color: '#67C23A', buttonType: 'success' },
  '/settings/roles': { description: '维护团队角色、授权范围和高风险操作权限', color: '#3B82F6', buttonType: 'primary' },
  '/settings/requirements': { description: '管理系统需求类型和优先级配置', color: '#909399', buttonType: '' },
  '/system/workflow-config': { description: '在系统设置中维护工作流与审批配置', color: '#8E44AD', buttonType: 'primary' },
  '/settings/menus': { description: '维护菜单、按钮以及角色授权能力', color: '#F56C6C', buttonType: 'danger' },
  '/settings/rag': { description: '上传文档并进行智能检索与问答', color: '#16A085', buttonType: 'success' },
  '/settings/knowledge': { description: '创建和管理知识库，配置文档索引', color: '#2C3E50', buttonType: '' },
  '/settings/llm': { description: '配置文档知识库可用的大模型参数和密钥', color: '#9B59B6', buttonType: 'primary' },
}

// 权限校验映射：path -> 权限判断函数
const pathPermissions: Record<string, () => boolean> = {
  '/settings/projects': () => hasPermission('menu:settings:project') || hasPermission('menu:system-config'),
  '/settings/users': () => hasPermission('menu:settings:user') || hasPermission('menu:system-config'),
  '/settings/roles': () => hasPermission('menu:settings:role') || hasPermission('menu:system-config'),
  '/settings/requirements': () => hasPermission('menu:settings:requirement') || hasPermission('menu:system-config'),
  '/system/workflow-config': () => hasAnyRole(['admin', 'workflow:config']) || hasPermission('menu:settings:workflow'),
  '/settings/menus': () => hasPermission('menu:menu-management') || hasAnyPermission(['button:menu:create', 'button:menu:update', 'button:menu:delete']),
  '/settings/rag': () => hasPermission('menu:rag'),
  '/settings/knowledge': () => hasPermission('menu:rag'),
  '/settings/llm': () => hasPermission('menu:settings:llm') || hasPermission('menu:system-config'),
}

const iconMap: Record<string, Component> = {}
for (const [name, comp] of Object.entries(ElementPlusIcons)) {
  iconMap[name] = comp as Component
}

const menuItems = ref<MenuItem[]>([])

onMounted(async () => {
  try {
    const res = await getCurrentMenus() as any
    const data = res?.data ?? res
    const list: MenuItem[] = Array.isArray(data) ? data : []
    // 找到系统配置的子菜单
    const systemConfig = list.find((m: MenuItem) => m.permissionCode === 'menu:system-config' || m.name === '系统配置')
    menuItems.value = systemConfig?.children ?? []
  } catch {
    menuItems.value = []
  }
})

interface CardItem {
  path: string
  title: string
  description: string
  icon: Component
  color: string
  buttonType: string
  visible: () => boolean
}

const visibleCards = computed<CardItem[]>(() => {
  return menuItems.value
    .filter(m => m.menuType === 'MENU' && m.enabled === 1 && m.visible === 1)
    .map(m => {
      const path = m.path || ''
      const meta = cardMeta[path] || { description: m.name, color: '#909399', buttonType: '' }
      return {
        path,
        title: m.name,
        description: meta.description,
        icon: iconMap[m.icon || 'Setting'] || iconMap['Setting'],
        color: meta.color,
        buttonType: meta.buttonType,
        visible: pathPermissions[path] || (() => true),
      }
    })
    .filter(item => item.visible())
})
</script>

<style lang="scss" scoped>
.settings-container {
  padding: 20px;
}

.settings-header {
  margin-bottom: 24px;

  h2 {
    margin: 0 0 8px;
    font-size: 22px;
    color: #303133;
  }

  .settings-desc {
    margin: 0;
    color: #909399;
    font-size: 14px;
  }
}

.settings-cards {
  .settings-card {
    cursor: pointer;
    transition: transform 0.2s;

    &:hover {
      transform: translateY(-4px);
    }

    :deep(.el-card__body) {
      padding: 24px;
    }
  }

  .card-content {
    text-align: center;

    h3 {
      margin: 16px 0 8px;
      font-size: 18px;
      color: #303133;
    }

    p {
      margin: 0 0 16px;
      color: #909399;
      font-size: 13px;
    }
  }
}
</style>
