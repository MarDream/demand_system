// 断言：右上角操作按钮组只剩「最小化 / 全屏 / 关闭」，不再有重复的「新会话 +」
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto('http://127.0.0.1:5170/login', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle', { timeout: 15000 });
    await page.waitForTimeout(500);
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('button:has-text("登录"), button:has-text("登 录"), button[type="submit"]').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 });
    await page.waitForTimeout(1000);

    await page.locator('.assistant-fab').click();
    await page.waitForTimeout(600);

    const labels = await page.$$eval('.assistant-panel__action-group button', (els) =>
      els.map((e) => e.getAttribute('aria-label')),
    );
    console.log('右上角操作组 aria-label:', JSON.stringify(labels));

    const plusCount = await page
      .locator('.assistant-panel__action-group button[aria-label="新会话"]')
      .count();
    console.log('右上角 + (新会话) 数量:', plusCount, plusCount === 0 ? '=> 已移除 OK' : '=> 仍存在 FAIL');

    const sidebarPlus = await page.locator('.assistant-session-list__new-btn').count();
    console.log('会话列表标题栏 + 数量:', sidebarPlus, sidebarPlus === 1 ? '=> 保留 OK' : '=> 异常');

    const toggleCount = await page.locator('.assistant-panel__sidebar-toggle').count();
    console.log('header 左侧侧栏折叠按钮数量:', toggleCount);

    await page.screenshot({ path: require('node:path').join(__dirname, 'out', '10-header-no-plus.png') });
  } catch (err) {
    console.error('[FAIL]', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();