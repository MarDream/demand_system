import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './helpers/auth'
import { selectElOption, waitForElMessage } from './helpers/form'

/**
 * 前置条件：本地后端 8081 已启动，且全局/项目 1 已启用可视化工作流。
 */
test.describe.configure({ timeout: 90_000 })

test.describe('需求草稿与实例流转', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('创建页：填写草稿并提交流转到详情', async ({ page }) => {
    const title = `E2E-草稿-${Date.now()}`

    await page.goto('/requirements/create')
    await expect(page.getByRole('button', { name: '提交流转' })).toBeVisible()

    await page.getByPlaceholder('请输入需求标题').fill(title)
    await selectElOption(page, '优先级')
    await selectElOption(page, '所属项目', '演示项目')

    const submitResponse = page.waitForResponse(
      (res) => res.url().includes('/requirements/') && res.url().includes('/submit') && res.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '提交流转' }).click()
    const response = await submitResponse
    expect(response.ok()).toBeTruthy()

    await expect(page).toHaveURL(/\/requirements\/\d+/, { timeout: 30_000 })
    await expect(page.getByRole('button', { name: '执行流转' })).toBeVisible({ timeout: 15_000 })
  })

  test('详情页：展示工作流操作区', async ({ page }) => {
    await page.goto('/requirements/create')
    const title = `E2E-详情-${Date.now()}`
    await page.getByPlaceholder('请输入需求标题').fill(title)
    await selectElOption(page, '优先级')
    await selectElOption(page, '所属项目', '演示项目')

    await page.getByRole('button', { name: '提交流转' }).click()
    await waitForElMessage(page, /提交流转成功|草稿已保存/)

    await expect(page).toHaveURL(/\/requirements\/\d+/, { timeout: 30_000 })
    await expect(page.getByRole('button', { name: '执行流转' })).toBeVisible({ timeout: 15_000 })
  })
})
