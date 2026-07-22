import { chromium } from 'playwright'

const BASE = 'http://127.0.0.1:5171'
const API = BASE + '/api/v1'
let TOKEN = ''
const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext()
const page = await ctx.newPage()
const allMsgs = []
page.on('response', async (r) => { if (r.url().includes('/auth/login')) { try { const j = await r.json(); TOKEN = j?.data?.accessToken || TOKEN } catch {} } })
page.on('console', (m) => allMsgs.push(`[${m.type()}] ${m.text()}`))
page.on('pageerror', (e) => allMsgs.push(`[PAGEERROR] ${e.message}`))

async function api(method, path, body) {
  const res = await ctx.request[method.toLowerCase()](API + path, { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${TOKEN}` }, data: body })
  return res.json()
}

await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
await page.getByPlaceholder('请输入用户名').fill('admin')
await page.getByPlaceholder('请输入密码').fill('admin123')
await page.getByRole('button', { name: '登 录' }).click()
await page.waitForURL(/\/dashboard/, { timeout: 15000 })

const base = await api('POST', '/bitable/bases', { name: 'DBG_BASE', description: 'dbg', icon: 'Grid', coverColor: '#1890ff' })
const baseId = base.data
const table = await api('POST', `/bitable/bases/${baseId}/tables`, { name: 'DBG_TABLE', description: 'dbg' })
const tableId = table.data
const fText = (await api('POST', `/bitable/tables/${tableId}/fields`, { name: '名称', fieldType: 'text', required: 1 })).data
await api('POST', `/bitable/tables/${tableId}/views`, { name: '默认视图', viewType: 'grid' })
await api('POST', `/bitable/tables/${tableId}/records`, { cells: { [fText]: { valueText: '任务A' } } })

await page.goto(BASE + `/bitable/${baseId}`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.vxe-grid', { timeout: 15000 })
await page.waitForTimeout(800)

console.log('=== BEFORE CLICK ===')
console.log('body rows:', await page.locator('.vxe-body--row').count())
console.log('.vxe-cell--edit count:', await page.locator('.vxe-cell--edit').count())

// click first data column header text
const headers = page.locator('.vxe-header--column .vxe-cell')
const nh = await headers.count()
let colIdx = -1
for (let i = 0; i < nh; i++) { if ((await headers.nth(i).innerText()).includes('名称')) { colIdx = i; break } }
console.log('名称 column index:', colIdx, 'total headers:', nh)

const row = page.locator('.vxe-body--row').first()
const cell = row.locator('.vxe-body--column').nth(colIdx)
console.log('cell classes before:', await cell.getAttribute('class'))
await cell.click()
await page.waitForTimeout(800)
console.log('=== AFTER SINGLE CLICK ===')
console.log('cell classes after:', await cell.getAttribute('class'))
console.log('.vxe-cell--edit count:', await page.locator('.vxe-cell--edit').count())
console.log('any editor (vxe-input/number-input/date-picker/select/rate) in grid:', await page.locator('.vxe-cell--edit .vxe-input, .vxe-cell--edit .vxe-number-input, .vxe-cell--edit .vxe-date-picker, .vxe-cell--edit .vxe-select, .vxe-cell--edit .vxe-rate').count())
console.log('clicked td outerHTML (first 1500):', (await cell.innerHTML()).slice(0, 1500))

// try double click
await cell.dblclick()
await page.waitForTimeout(800)
console.log('=== AFTER DOUBLE CLICK ===')
console.log('cell classes after dblclick:', await cell.getAttribute('class'))
console.log('.vxe-cell--edit count:', await page.locator('.vxe-cell--edit').count())
console.log('clicked td outerHTML (first 1500):', (await cell.innerHTML()).slice(0, 1500))
console.log('editor input value:', await page.locator('.vxe-cell--edit input').first().inputValue().catch(() => 'NONE'))

// dump a snippet of the grid component props via DOM (edit-config presence is not in DOM; check vxe internal)
console.log('=== CONSOLE MESSAGES (last 40) ===')
console.log(allMsgs.slice(-40).join('\n'))

await browser.close()
