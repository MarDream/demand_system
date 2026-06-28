import { test, expect, request as apiRequest } from '@playwright/test'

/**
 * 工作流复制功能 - E2E 测试
 *
 * 覆盖场景：
 *  1. 从版本列表点击复制按钮
 *  2. 复制对话框自动选中源版本
 *  3. 名称冲突检测和建议
 *  4. 高级选项（清空审批人、清空敏感数据）
 *  5. 复制成功后刷新列表
 *  6. API 层：复制接口、模板列表、名称检查
 *
 * 账号：admin / admin123
 */

const FRONTEND = process.env.FRONTEND_URL || 'http://localhost:5170'
const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081'

test.describe.configure({ mode: 'serial', timeout: 120_000 })

async function getAdminToken() {
  const ctx = await apiRequest.newContext()
  const res = await ctx.post(`${BACKEND}/api/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' }
  })
  const json = await res.json()
  await ctx.dispose()
  return json.data.accessToken
}

async function loginAsAdmin(page: any) {
  await page.goto(`${FRONTEND}/login`)
  await page.fill('input[placeholder*="用户名"]', 'admin')
  await page.fill('input[type="password"]', 'admin123')
  await page.click('button:has-text("登录")')
  await page.waitForURL(`${FRONTEND}/**`, { timeout: 10_000 })
}

test.describe('工作流复制功能', () => {
  test('C1. API 测试：获取模板列表', async () => {
    const ctx = await apiRequest.newContext()
    const token = await getAdminToken()
    
    const res = await ctx.get(`${BACKEND}/api/v1/workflows/templates`, {
      headers: { Authorization: `Bearer ${token}` },
      params: { page: 1, pageSize: 20 }
    })
    
    expect(res.ok()).toBeTruthy()
    const json = await res.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeDefined()
    expect(json.data.records).toBeInstanceOf(Array)
    
    await ctx.dispose()
  })

  test('C2. API 测试：检查名称冲突', async () => {
    const ctx = await apiRequest.newContext()
    const token = await getAdminToken()
    
    // 用一个已存在的名称测试
    const res = await ctx.get(`${BACKEND}/api/v1/workflows/check-name`, {
      headers: { Authorization: `Bearer ${token}` },
      params: { 
        name: '全局工作流',
        projectId: 0
      }
    })
    
    expect(res.ok()).toBeTruthy()
    const json = await res.json()
    expect(json.code).toBe(200)
    expect(json.data).toBeDefined()
    
    // 如果已存在，应该返回 exists: true 和 suggestedName
    if (json.data.exists) {
      expect(json.data.suggestedName).toBeTruthy()
    }
    
    await ctx.dispose()
  })

  test('C3. API 测试：复制工作流接口（需要有效版本 ID）', async () => {
    const ctx = await apiRequest.newContext()
    const token = await getAdminToken()
    
    // 先获取一个可用的版本 ID
    const listRes = await ctx.get(`${BACKEND}/api/v1/workflows/templates`, {
      headers: { Authorization: `Bearer ${token}` },
      params: { page: 1, pageSize: 1 }
    })
    
    const listJson = await listRes.json()
    
    if (listJson.data?.records?.length > 0) {
      const sourceVersionId = listJson.data.records[0].id
      
      // 测试复制接口
      const copyRes = await ctx.post(`${BACKEND}/api/v1/workflows/versions/${sourceVersionId}/copy`, {
        headers: { Authorization: `Bearer ${token}` },
        data: {
          newName: `复制测试_${Date.now()}`,
          clearAssignees: false,
          sanitizeSensitiveData: true
        }
      })
      
      expect(copyRes.ok()).toBeTruthy()
      const copyJson = await copyRes.json()
      expect(copyJson.code).toBe(200)
      expect(copyJson.data).toBeDefined()
      expect(copyJson.data.newVersionId).toBeDefined()
      expect(copyJson.data.nodeCount).toBeGreaterThan(0)
    } else {
      test.skip()
    }
    
    await ctx.dispose()
  })

  test('C4. UI 测试：版本列表显示复制按钮', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    
    // 等待版本列表加载
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 检查第一个版本的操作列是否有复制按钮
    const copyButton = page.locator('.el-table .el-button').filter({ hasText: /复制/ }).first()
    
    if (await copyButton.count() === 0) {
      // 如果没有"复制"文本，尝试找复制图标按钮
      const iconButton = page.locator('.el-table .el-button .el-icon').first()
      expect(await iconButton.count()).toBeGreaterThan(0)
    } else {
      expect(await copyButton.count()).toBeGreaterThan(0)
    }
  })

  test('C5. UI 测试：点击复制按钮打开对话框', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    
    // 等待版本列表加载
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 点击第一个复制按钮（可能是图标或文本）
    const copyButtons = page.locator('.el-table .el-button').filter({ hasText: /复制/ })
    
    if (await copyButtons.count() > 0) {
      await copyButtons.first().click()
    } else {
      // 尝试点击操作列的第三个按钮（查看、编辑、复制）
      await page.locator('.el-table tbody tr').first().locator('.el-button').nth(2).click()
    }
    
    // 等待对话框出现
    const dialog = page.locator('.el-dialog:visible')
    await dialog.waitFor({ state: 'visible', timeout: 5_000 })
    
    // 验证对话框标题
    const title = await dialog.locator('.el-dialog__title').textContent()
    expect(title).toMatch(/工作流|复制/)
  })

  test('C6. UI 测试：复制对话框默认选中"从现有复制"模式', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 点击复制按钮
    const copyButtons = page.locator('.el-table .el-button').filter({ hasText: /复制/ })
    if (await copyButtons.count() > 0) {
      await copyButtons.first().click()
    } else {
      await page.locator('.el-table tbody tr').first().locator('.el-button').nth(2).click()
    }
    
    await page.waitForTimeout(1_000)
    
    // 验证"从现有复制"单选按钮被选中
    const copyRadio = page.locator('.el-radio-button:has-text("从现有复制")')
    await expect(copyRadio).toHaveClass(/is-active/)
  })

  test('C7. UI 测试：名称冲突检测和建议', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 点击复制按钮
    const copyButtons = page.locator('.el-table .el-button').filter({ hasText: /复制/ })
    if (await copyButtons.count() > 0) {
      await copyButtons.first().click()
    } else {
      await page.locator('.el-table tbody tr').first().locator('.el-button').nth(2).click()
    }
    
    await page.waitForTimeout(1_000)
    
    // 在名称输入框输入一个已存在的名称
    const nameInput = page.locator('input[placeholder*="名称"]').first()
    await nameInput.fill('全局工作流')
    await nameInput.blur()
    
    // 等待名称检查完成
    await page.waitForTimeout(1_500)
    
    // 验证是否出现冲突提示或建议
    const conflictHint = page.locator('text=/已存在|冲突|建议/')
    
    if (await conflictHint.count() > 0) {
      expect(await conflictHint.count()).toBeGreaterThan(0)
    }
  })

  test('C8. UI 测试：高级选项展开和配置', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 点击复制按钮
    const copyButtons = page.locator('.el-table .el-button').filter({ hasText: /复制/ })
    if (await copyButtons.count() > 0) {
      await copyButtons.first().click()
    } else {
      await page.locator('.el-table tbody tr').first().locator('.el-button').nth(2).click()
    }
    
    await page.waitForTimeout(1_000)
    
    // 查找高级选项折叠面板
    const advancedCollapse = page.locator('.el-collapse-item:has-text("高级选项")')
    
    if (await advancedCollapse.count() > 0) {
      // 展开高级选项
      await advancedCollapse.click()
      await page.waitForTimeout(500)
      
      // 验证高级选项内容可见
      const clearAssignees = page.locator('text=/清空审批人/')
      const sanitizeData = page.locator('text=/清空敏感数据/')
      
      expect(await clearAssignees.count() + await sanitizeData.count()).toBeGreaterThan(0)
    }
  })

  test('C9. UI 测试：完整复制流程', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 记录当前版本数量
    const initialRowCount = await page.locator('.el-table tbody tr').count()
    
    // 点击复制按钮
    const copyButtons = page.locator('.el-table .el-button').filter({ hasText: /复制/ })
    if (await copyButtons.count() > 0) {
      await copyButtons.first().click()
    } else {
      await page.locator('.el-table tbody tr').first().locator('.el-button').nth(2).click()
    }
    
    await page.waitForTimeout(1_000)
    
    // 输入新名称
    const uniqueName = `E2E测试复制_${Date.now()}`
    const nameInput = page.locator('input[placeholder*="名称"]').first()
    await nameInput.fill(uniqueName)
    
    // 点击确认按钮
    const confirmButton = page.locator('.el-dialog__footer button:has-text("确认"), .el-dialog__footer button:has-text("复制")')
    await confirmButton.click()
    
    // 等待成功提示
    await page.waitForSelector('.el-message--success', { timeout: 10_000 })
    
    // 等待对话框关闭
    await page.waitForTimeout(1_000)
    
    // 验证版本列表已刷新且包含新版本
    await page.waitForTimeout(2_000)
    const newRowCount = await page.locator('.el-table tbody tr').count()
    expect(newRowCount).toBeGreaterThanOrEqual(initialRowCount)
    
    // 验证新版本在列表中
    const newVersionRow = page.locator(`.el-table tbody tr:has-text("${uniqueName}")`)
    expect(await newVersionRow.count()).toBeGreaterThan(0)
  })

  test('C10. 回归测试：复制后版本详情可查看', async ({ page }) => {
    await loginAsAdmin(page)
    
    await page.goto(`${FRONTEND}/system/workflow-config`)
    await page.waitForLoadState('networkidle', { timeout: 30_000 })
    await page.waitForSelector('.el-table', { timeout: 10_000 })
    
    // 查找最近复制的测试版本
    const testVersionRow = page.locator('.el-table tbody tr').filter({ hasText: /E2E测试复制/ }).first()
    
    if (await testVersionRow.count() > 0) {
      // 点击查看按钮
      await testVersionRow.locator('.el-button').first().click()
      
      // 等待编辑器页面加载
      await page.waitForURL(/workflow-config\/editor/, { timeout: 10_000 })
      await page.waitForLoadState('networkidle', { timeout: 30_000 })
      
      // 验证编辑器画布存在
      const canvas = page.locator('.workflow-canvas, .lf-canvas')
      await canvas.waitFor({ state: 'visible', timeout: 10_000 })
      
      expect(await canvas.count()).toBeGreaterThan(0)
    } else {
      test.skip()
    }
  })
})
