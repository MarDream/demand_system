import { test, expect } from '@playwright/test'

test('管理员登录并进入仪表盘', async ({ page }) => {
  await page.goto('/login')

  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: '登 录' }).click()

  await expect(page).toHaveURL(/\/dashboard/)
  await expect(page.getByText('仪表盘')).toBeVisible()
})

