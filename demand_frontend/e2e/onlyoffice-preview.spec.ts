import { test, expect, type Page } from '@playwright/test'

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill('admin')
  await page.getByPlaceholder('请输入密码').fill('admin123')
  await page.getByRole('button', { name: '登 录' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
}

async function openKnowledgePreview(page: Page, rowText: string | RegExp) {
  await page.goto('/settings/knowledge/1')
  await expect(page.locator('.el-table')).toBeVisible()

  const row = page.locator('.el-table__row').filter({ hasText: rowText }).first()
  await expect(row).toBeVisible()
  const editorConfigResponse = page.waitForResponse(resp =>
    resp.url().includes('/api/v1/onlyoffice/editor-config') && resp.request().method() === 'POST'
  )
  const statusResponse = page.waitForResponse(resp => resp.url().includes('/api/v1/onlyoffice/status'))
  const apiJsResponse = page.waitForResponse(resp => resp.url().includes('/web-apps/apps/api/documents/api.js'))

  await row.locator('.file-name-link').click()

  const dialog = page.locator('.preview-container').first()
  await expect(dialog).toBeVisible()

  const [editorConfig, status, apiJs] = await Promise.all([editorConfigResponse, statusResponse, apiJsResponse])
  expect(editorConfig.ok()).toBeTruthy()
  expect(status.ok()).toBeTruthy()
  expect(apiJs.ok()).toBeTruthy()

  const previewFrame = dialog.locator('iframe').first()
  const editorFrame = dialog.frameLocator('iframe').first()
  const unsupported = page.locator('.preview-unsupported')

  try {
    await expect(previewFrame).toBeVisible({ timeout: 20_000 })
    await expect(editorFrame.getByRole('button', { name: 'Download file' })).toBeVisible({ timeout: 20_000 })
    await expect(editorFrame.getByText(/Page 1 of \d+/)).toBeVisible({ timeout: 20_000 })
  } catch (error) {
    const unsupportedText = (await unsupported.count()) > 0 ? await unsupported.textContent() : ''
    const dialogText = await dialog.textContent()
    throw new Error(`OnlyOffice preview did not become ready for row=${rowText}. unsupported=${unsupportedText} dialog=${dialogText}`)
  }
}

test('知识库中的 DOCX 文档可以进入预览态', async ({ page }) => {
  await login(page)
  await openKnowledgePreview(page, /test1\.docx/)
})

test('知识库中的 DOC 文档可以进入预览态', async ({ page }) => {
  await login(page)
  await openKnowledgePreview(page, /\.doc(?!x)/)
})
