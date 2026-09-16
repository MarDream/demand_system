// 清理测试数据：还原 base 1 的数据表分组状态
const path = require('node:path');
const BASE = 'http://127.0.0.1:5170';
const log = (...a) => console.log(...a);

async function main() {
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();

  const snap = () => page.locator('.tgt-node').evaluateAll((els) => els.map((el) => ({
    indent: parseInt(el.style.paddingLeft) || 0,
    name: el.querySelector('.tgt-node__name')?.textContent?.trim(),
    count: el.querySelector('.tgt-node__count')?.textContent?.trim() || '',
  })));
  const show = (s) => s.map((n) => `${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`);
  const groupRow = (name) => page.locator('.tgt-node--group').filter({ hasText: name }).first();
  const tableRow = (name) => page.locator('.tgt-node--table').filter({ hasText: name }).first();
  const menuItem = (t) => page.locator('.el-dropdown-menu__item:visible').filter({ hasText: t }).first();
  const waitNoMessage = () => page.waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 }).catch(() => {});

  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
  await page.locator('input[placeholder="请输入用户名"]').fill('admin');
  await page.locator('input[placeholder="请输入密码"]').fill('admin123');
  await page.locator('.login-btn').first().click();
  await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

  await page.goto(`${BASE}/bitable/1`, { waitUntil: 'domcontentloaded' });
  await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
  await page.waitForTimeout(1000);
  log('清理前:', show(await snap()));

  // 1) 把「项目管理」拖回根层级「未分组」
  const proj = tableRow('项目管理');
  if (await proj.count()) {
    await waitNoMessage();
    await proj.dragTo(page.locator('.tgt-node--ungrouped').first());
    await page.waitForTimeout(1500);
    log('拖回根层级后:', show(await snap()));
  }

  // 2) 删掉测试分组（可通过命令行参数覆盖）
  const groupNames = process.argv.slice(2).length
    ? process.argv.slice(2)
    : ['__P_MOVE', '__E_L1', '__E_L2', '__E_L3', '采购管理', '销售', '进销存系统（JXC）'];
  for (const name of groupNames) {
    const row = groupRow(name);
    if (!(await row.count())) continue;
    await waitNoMessage();
    await row.hover();
    await row.locator('.tgt-node__tool[title="更多操作"]').click();
    await menuItem('删除分组').click();
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await page.waitForTimeout(1500);
    log(`已删除「${name}」`);
  }

  log('清理后:', show(await snap()));
  await browser.close();
}

main();
