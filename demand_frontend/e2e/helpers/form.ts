import { expect, type Page } from '@playwright/test'

export async function selectElOption(page: Page, fieldLabel: string, optionText?: string) {
  const field = page.locator('.el-form-item').filter({ hasText: fieldLabel }).first()
  await field.locator('.el-select').click({ force: true })
  const dropdown = page.locator('.el-select-dropdown:visible')
  await dropdown.waitFor({ state: 'visible', timeout: 5_000 })
  const option = optionText
    ? dropdown.locator('.el-select-dropdown__item').filter({ hasText: optionText })
    : dropdown.locator('.el-select-dropdown__item').first()
  await option.click({ force: true })
}

export async function waitForElMessage(page: Page, text: string | RegExp) {
  await expect(page.locator('.el-message').filter({ hasText: text })).toBeVisible({ timeout: 30_000 })
}
