import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './helpers/auth'
import { selectElOption, waitForElMessage } from './helpers/form'

/**
 * 前置条件：本地后端已启动，且至少一个可见项目已启用可视化工作流。
 */
test.describe.configure({ timeout: 90_000 })

test.describe('需求草稿与实例流转', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page)
  })

  test('创建页：填写草稿并提交流转到详情', async ({ page }) => {
    const title = `E2E-草稿-${Date.now()}`

    await page.goto('/requirements/create')
    await expect(submitAuditButton(page)).toBeVisible()

    await page.getByPlaceholder('请输入需求标题').fill(title)
    await selectElOption(page, '优先级')
    await selectElOption(page, '所属项目')

    const submitResponse = page.waitForResponse(
      (res) => res.url().includes('/requirements/') && res.url().includes('/submit') && res.request().method() === 'POST',
    )
    await submitAuditButton(page).click()
    await confirmTargetNodeIfNeeded(page)
    const response = await submitResponse
    expect(response.ok()).toBeTruthy()

    await expect(page).toHaveURL(/\/requirements\/\d+/, { timeout: 30_000 })
    await expect(page.getByText('审批功能').first()).toBeVisible({ timeout: 15_000 })
  })

  test('详情页：展示工作流操作区', async ({ page }) => {
    await page.goto('/requirements/create')
    const title = `E2E-详情-${Date.now()}`
    await page.getByPlaceholder('请输入需求标题').fill(title)
    await selectElOption(page, '优先级')
    await selectElOption(page, '所属项目')

    await submitAuditButton(page).click()
    await confirmTargetNodeIfNeeded(page)
    await waitForElMessage(page, /提交审核成功|草稿已保存/)

    await expect(page).toHaveURL(/\/requirements\/\d+/, { timeout: 30_000 })
    await expect(page.getByText('审批功能').first()).toBeVisible({ timeout: 15_000 })
  })
})

async function confirmTargetNodeIfNeeded(page: import('@playwright/test').Page) {
  const confirmButton = page.getByRole('button', { name: '确认提交' })
  if (await confirmButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await confirmButton.click()
  }
}

function submitAuditButton(page: import('@playwright/test').Page) {
  return page.locator('button:has-text("提交审核")').last()
}
