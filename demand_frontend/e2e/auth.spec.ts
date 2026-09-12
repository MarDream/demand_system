import { test, expect } from '@playwright/test'

// 该文件专门测试未登录态，清空项目级 storageState
test.use({ storageState: { cookies: [], origins: [] } })

test.describe('登录页', () => {
  test('未登录访问受保护页面应跳转登录页', async ({ page }) => {
    // 不带 storageState 的新上下文
    await page.goto('/requirements')
    await expect(page).toHaveURL(/login/)
  })

  test('错误密码应提示登录失败', async ({ page }) => {
    await page.goto('/login')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('wrong-password')
    await page.getByRole('button', { name: '登 录' }).click()
    await expect(page).toHaveURL(/login/)
    // 页面仍停留在登录表单
    await expect(page.getByPlaceholder('请输入密码')).toBeVisible()
  })
})
