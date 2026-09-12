import { test as setup, expect } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const authFile = path.join(here, '.auth', 'user.json')

setup('authenticate as admin', async ({ page }) => {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: '登 录' }).click()

  // 登录成功后跳转到工作台
  await expect(page).toHaveURL(/dashboard/, { timeout: 15_000 })
  await expect(page.locator('.dashboard-container')).toBeVisible()

  fs.mkdirSync(path.dirname(authFile), { recursive: true })
  await page.context().storageState({ path: authFile })
})
