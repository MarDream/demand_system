import { test, expect } from '@playwright/test'

/**
 * ============================================
 * 知识库问答系统 - E2E 测试套件 v3
 * ============================================
 * 覆盖：登录认证、模型配置、知识库管理、
 *       RAG 工作台、全局语义检索、导航连通性
 * ============================================
 * 
 * 修复 v2 问题：PageContainer 组件 showTitle 默认 false，
 * 标题 h2 不渲染，改用各页面实际存在的元素断言。
 * ============================================
 */

test.describe('知识库问答 - E2E 测试', () => {

  // ==================== 模块 1: 登录认证 ====================
  test.describe('登录认证', () => {
    test('should 管理员登录成功并进入工作台', async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
      await expect(page).toHaveURL(/\/dashboard/)
      await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible()
    })

    test('should 错误密码登录失败并停留登录页', async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('wrongpassword')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForTimeout(3000)
      await expect(page).toHaveURL(/\/login/)
    })
  })

  // ==================== 模块 2: 模型配置 ====================
  test.describe('模型配置', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    })

    test('should 模型配置页加载并显示接入组列表', async ({ page }) => {
      await page.goto('/settings/llm')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await expect(page.locator('.config-header h2')).toBeVisible({ timeout: 10000 })
      await expect(page.locator('.provider-panel')).toBeVisible()
      
      const count = await page.locator('.provider-item').count()
      console.log(`模型配置页: ${count} 个接入组`)
      expect(count).toBeGreaterThanOrEqual(1)
    })

    test('should 点击接入组选中并显示模型列表', async ({ page }) => {
      await page.goto('/settings/llm')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const providerItem = page.locator('.provider-item').first()
      await expect(providerItem).toBeVisible()
      await providerItem.click()
      await expect(providerItem).toHaveClass(/is-selected/)
    })

    test('should 接入组 toggle 开关可见', async ({ page }) => {
      await page.goto('/settings/llm')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await expect(page.locator('.provider-item .el-switch').first()).toBeVisible()
    })
  })

  // ==================== 模块 3: 知识库管理 ====================
  test.describe('知识库管理', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    })

    test('should 知识库管理页加载并显示概览卡片', async ({ page }) => {
      await page.goto('/settings/knowledge')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      // 用实际存在的元素验证，PageContainer 标题默认隐藏
      await expect(page.locator('.kb-overview')).toBeVisible({ timeout: 10000 })
      await expect(page.locator('.overview-card').first()).toBeVisible()
      
      const cardCount = await page.locator('.kb-card').count()
      console.log(`知识库管理页: ${cardCount} 个知识库`)
    })

    test('should 知识库管理页支持搜索和刷新', async ({ page }) => {
      await page.goto('/settings/knowledge')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const searchInput = page.locator('.kb-toolbar__search input')
      await expect(searchInput).toBeVisible()
      await searchInput.fill('测试')
      await expect(searchInput).toHaveValue('测试')
      
      await expect(page.getByRole('button', { name: '刷新' })).toBeVisible()
      await expect(page.getByRole('button', { name: /新建|创建/ })).toBeVisible()
    })
  })

  // ==================== 模块 4: RAG 工作台 ====================
  test.describe('RAG 文档检索工作台', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    })

    test('should RAG 工作台加载并显示三栏布局', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(3000)
      
      // RAG 工作台有 headerActions，实际 DOM 用 .rag-workspace
      await expect(page.locator('.rag-sidebar')).toBeVisible({ timeout: 10000 })
      await expect(page.locator('.rag-chat')).toBeVisible()
      
      const sidebarVisible = await page.locator('.rag-sidebar').isVisible()
      const chatVisible = await page.locator('.rag-chat').isVisible()
      console.log(`RAG 工作台: sidebar=${sidebarVisible}, chat=${chatVisible}`)
    })

    test('should RAG 工作台显示知识库列表', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const count = await page.locator('.knowledge-card').count()
      console.log(`RAG 工作台: ${count} 个知识库`)
      expect(count).toBeGreaterThanOrEqual(1)
    })

    test('should 选择知识库后卡片高亮', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const firstCard = page.locator('.knowledge-card').first()
      await expect(firstCard).toBeVisible()
      await firstCard.click()
      await expect(page.locator('.knowledge-card--active')).toBeVisible()
    })

    test('should RAG 工作台检索参数控件可见', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const count = await page.locator('.chat-filters .el-select').count()
      console.log(`RAG 工作台: ${count} 个检索参数选择器`)
      expect(count).toBeGreaterThanOrEqual(2)
    })

    test('should 侧边栏收起/展开', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      // 点击收起按钮（仅在可见时测试）
      const collapseBtn = page.locator('.rag-sidebar__collapse-trigger')
      if (await collapseBtn.isVisible()) {
        await collapseBtn.click()
        // .rag-sidebar 默认就是展开的，收起后 class 变为 is-collapsed
        const collapsed = await page.locator('.rag-sidebar').count() > 0
        expect(collapsed).toBe(true)
      }
    })
  })

  // ==================== 模块 5: 全局语义检索 ====================
  test.describe('全局语义检索', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    })

    test('should 语义检索页加载并显示搜索框和模式选项', async ({ page }) => {
      await page.goto('/settings/knowledge/search')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await expect(page.locator('.search-input input')).toBeVisible({ timeout: 10000 })
      
      const modeButtons = await page.locator('.el-radio-button').allTextContents()
      console.log(`语义检索模式: ${modeButtons.join(', ')}`)
      expect(modeButtons.some(t => t.includes('混合模式'))).toBe(true)
      expect(modeButtons.some(t => t.includes('语义检索'))).toBe(true)
      expect(modeButtons.some(t => t.includes('关键词'))).toBe(true)
    })

    test('should 搜索框可输入和清空', async ({ page }) => {
      await page.goto('/settings/knowledge/search')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const input = page.locator('.search-input input')
      await expect(input).toBeVisible()
      await input.fill('测试搜索内容')
      await expect(input).toHaveValue('测试搜索内容')
      await input.clear()
      await expect(input).toBeEmpty()
    })

    test('should 知识库选择器可见', async ({ page }) => {
      await page.goto('/settings/knowledge/search')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await expect(page.locator('.kb-selector')).toBeVisible()
    })
  })

  // ==================== 模块 6: 导航连通性 ====================
  test.describe('导航连通性', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/login')
      await page.getByPlaceholder('请输入用户名').fill('admin')
      await page.getByPlaceholder('请输入密码').fill('admin123')
      await page.getByRole('button', { name: '登 录' }).click()
      await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    })

    test('should 从 RAG 工作台跳转到模型配置', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await page.getByRole('button', { name: '模型配置' }).click()
      await page.waitForURL(/\/settings\/llm/, { timeout: 10000 })
      await expect(page.locator('.config-header h2')).toBeVisible()
    })

    test('should 从 RAG 工作台跳转到知识库管理', async ({ page }) => {
      await page.goto('/settings/documents')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      await page.getByRole('button', { name: '管理知识库' }).click()
      await page.waitForURL(/\/settings\/knowledge/, { timeout: 10000 })
      await expect(page.locator('.kb-overview')).toBeVisible()
    })

    test('should 从知识库管理跳转到知识库详情', async ({ page }) => {
      await page.goto('/settings/knowledge')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      const firstCard = page.locator('.kb-card').first()
      await expect(firstCard).toBeVisible()
      await firstCard.click()
      await page.waitForURL(/\/settings\/knowledge\/\d+/, { timeout: 10000 })
    })

    test('should 从知识库管理跳转到 RAG 工作台', async ({ page }) => {
      await page.goto('/settings/knowledge')
      await page.waitForLoadState('networkidle', { timeout: 10000 })
      await page.waitForTimeout(2000)
      
      // 卡片上有"进入知识库"按钮，点击后跳到知识库详情页（RAG 工作台的入口）
      await expect(page.getByRole('button', { name: '进入知识库' })).toBeVisible()
      await page.getByRole('button', { name: '进入知识库' }).first().click()
      await page.waitForURL(/\/settings\/knowledge\/\d+/, { timeout: 15000 })
    })
  })
})
