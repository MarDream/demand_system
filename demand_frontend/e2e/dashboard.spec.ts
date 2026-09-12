import { test, expect } from '@playwright/test'

test.describe('工作台', () => {
  test('登录后进入工作台并渲染欢迎区', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page.locator('.dashboard-container')).toBeVisible()
    await expect(page.locator('.dashboard-header')).toBeVisible()
  })
})
