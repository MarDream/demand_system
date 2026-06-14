import { expect, type Page } from '@playwright/test'

export async function selectElOption(page: Page, fieldLabel: string, optionText?: string) {
  const labelPattern = new RegExp(`^\\*?${escapeRegExp(fieldLabel)}$`)
  const field = page.locator('.el-form-item')
    .filter({ has: page.locator('.el-form-item__label').filter({ hasText: labelPattern }) })
    .first()
  await field.locator('.el-select').click({ force: true })
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await dropdown.waitFor({ state: 'visible', timeout: 5_000 })
  const option = optionText
    ? dropdown.locator('.el-select-dropdown__item:not(.is-disabled)').filter({ hasText: optionText }).first()
    : dropdown.locator('.el-select-dropdown__item:not(.is-disabled)').first()
  await expect(option).toBeVisible({ timeout: 5_000 })
  await option.click({ force: true })
  await expect(dropdown).toBeHidden({ timeout: 5_000 })
}

export async function waitForElMessage(page: Page, text: string | RegExp) {
  await expect(page.locator('.el-message').filter({ hasText: text })).toBeVisible({ timeout: 30_000 })
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
