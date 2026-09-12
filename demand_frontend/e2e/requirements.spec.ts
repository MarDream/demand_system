import { test, expect } from '@playwright/test'

test.describe('需求管理', () => {
  test('需求列表加载并展示数据', async ({ page }) => {
    await page.goto('/requirements')
    await expect(page).toHaveURL(/requirements/)
    // 列表区域出现（表格或卡片），且请求完成
    const listReady = page.getByText('共').or(page.locator('.el-table__row').first())
    await expect(listReady.first()).toBeVisible({ timeout: 20_000 })
  })

  test('需求列表按关键字搜索', async ({ page }) => {
    await page.goto('/requirements')
    const listReady = page.getByText('共').or(page.locator('.el-table__row').first())
    await expect(listReady.first()).toBeVisible({ timeout: 20_000 })

    const searchInput = page.getByPlaceholder(/搜索|请输入/).first()
    await searchInput.fill('工建系统故障')
    await searchInput.press('Enter')
    // 等待新查询完成
    await page.waitForTimeout(1500)
    await expect(page.locator('body')).toContainText('工建系统故障')
  })
})
