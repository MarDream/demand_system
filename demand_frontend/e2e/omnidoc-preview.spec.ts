import { test, expect, type APIRequestContext, type Page } from '@playwright/test'

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
    resp.url().includes('/api/v1/omnidoc/editor-config') && resp.request().method() === 'POST'
  )
  const statusResponse = page.waitForResponse(resp => resp.url().includes('/api/v1/omnidoc/status'))
  const apiJsResponse = page.waitForResponse(resp => resp.url().includes('/web-apps/apps/api/documents/api.js'))

  await row.locator('.file-name-link').click()

  const dialog = page.locator('.preview-container').first()
  await expect(dialog).toBeVisible()

  const [editorConfig, status, apiJs] = await Promise.all([editorConfigResponse, statusResponse, apiJsResponse])
  expect(editorConfig.ok()).toBeTruthy()
  expect(status.ok()).toBeTruthy()
  expect(apiJs.ok()).toBeTruthy()
  const editorConfigBody = await editorConfig.json()
  expect(editorConfigBody?.data?.editorConfig?.lang).toBe('zh-CN')

  const previewFrame = dialog.locator('iframe').first()
  const editorFrame = dialog.frameLocator('iframe').first()
  const unsupported = page.locator('.preview-unsupported')

  try {
    await expect(previewFrame).toBeVisible({ timeout: 20_000 })
    await expect(editorFrame.getByRole('button', { name: '下载文件' })).toBeVisible({ timeout: 20_000 })
    await expect(editorFrame.getByText(/第\s*1\s*页共\s*\d+\s*页/)).toBeVisible({ timeout: 20_000 })
  } catch (error) {
    const unsupportedText = (await unsupported.count()) > 0 ? await unsupported.textContent() : ''
    const dialogText = await dialog.textContent()
    throw new Error(`OmniDoc preview did not become ready for row=${rowText}. unsupported=${unsupportedText} dialog=${dialogText}`)
  }
}

async function loginByApi(request: APIRequestContext) {
  const response = await request.post('/api/v1/auth/login', {
    data: {
      username: 'admin',
      password: 'admin123',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  return body.data.accessToken as string
}

async function createPublicShareLink(request: APIRequestContext, accessToken: string, documentId: number) {
  const response = await request.post(`/api/v1/knowledge/bases/1/documents/${documentId}/share?expireHours=24&requireLogin=false&oneTimeAccess=false`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Origin: 'http://localhost:5173',
      Referer: 'http://localhost:5173/settings/knowledge/1',
    },
  })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  return body.data as string
}

test('知识库中的 DOCX 文档可以进入预览态', async ({ page }) => {
  await login(page)
  await openKnowledgePreview(page, /test1\.docx/)
})

test('知识库中的 DOC 文档可以进入预览态', async ({ page }) => {
  await login(page)
  await openKnowledgePreview(page, /\.doc(?!x)/)
})

test('知识库中的 PDF 文档可以进入预览态', async ({ page }) => {
  await login(page)
  await openKnowledgePreview(page, /test2\.pdf/)
})

test('公开分享链接会落到前端分享页并可匿名预览', async ({ page, request }) => {
  const accessToken = await loginByApi(request)
  const shareLink = await createPublicShareLink(request, accessToken, 8)

  expect(shareLink).toContain('http://localhost:5173/public/share/')

  const contextResponse = page.waitForResponse(resp =>
    /\/api\/v1\/public\/knowledge\/shares\/.+\/context/.test(resp.url())
  )

  await page.goto(shareLink)

  await expect(page).toHaveURL(/\/public\/share\//)
  await expect(page.locator('.share-header__title')).toContainText('.doc')

  const context = await contextResponse
  expect(context.ok()).toBeTruthy()

  const previewFrame = page.locator('#public-omnidoc-editor-host iframe').first()
  await expect(previewFrame).toBeVisible({ timeout: 20_000 })
})
