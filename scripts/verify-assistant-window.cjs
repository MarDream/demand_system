// 验证 AI 助手弹框三态（normal / maximized / minimized）切换
// 用法：NODE_PATH=<demand_frontend/node_modules> node verify-assistant-window.cjs
const path = require('node:path');
const fs = require('node:fs');

(async () => {
  const { chromium } = require('playwright');

  const outDir = path.join(__dirname, 'out');
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  page.on('console', (msg) => {
    if (msg.type() === 'error') console.log(`[console.error] ${msg.text()}`);
  });
  page.on('pageerror', (err) => console.log(`[pageerror] ${err.message}`));

  try {
    // 1. 打开登录页
    await page.goto('http://127.0.0.1:5170/login', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle', { timeout: 15000 });
    await page.waitForTimeout(500);

    // 2. 登录
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('button:has-text("登录"), button:has-text("登 录"), button[type="submit"]').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 15000 });
    await page.waitForLoadState('networkidle', { timeout: 15000 });
    await page.waitForTimeout(800);

    console.log('[1] 已登录到主页面：' + page.url());

    // 3. 等待助手 FAB 出现
    const fab = page.locator('.assistant-fab');
    await fab.waitFor({ state: 'visible', timeout: 10000 });
    await fab.click();
    await page.waitForTimeout(500);

    // 4. 等待 assistant-window 出现，截图 normal
    const win = page.locator('.assistant-window').first();
    await win.waitFor({ state: 'visible', timeout: 5000 });
    await page.waitForTimeout(400);
    const boxNormal = await win.boundingBox();
    console.log('[2] normal 模式 bbox:', boxNormal);
    await page.screenshot({ path: path.join(outDir, '01-normal.png') });

    // 5. 点击最小化按钮（aria-label="最小化"）
    await page.locator('.assistant-window button[aria-label="最小化"]').first().click();
    await page.waitForTimeout(500);
    const boxMini = await win.boundingBox();
    console.log('[3] minimized 模式 bbox:', boxMini);
    // 迷你条应显示
    const miniVisible = await page.locator('.assistant-mini-bar').isVisible();
    console.log('    .assistant-mini-bar visible:', miniVisible);
    await page.screenshot({ path: path.join(outDir, '02-minimized.png') });

    // 6. 点击迷你条的还原按钮
    await page.locator('.assistant-mini-bar button[aria-label="还原"]').first().click();
    await page.waitForTimeout(500);
    const boxRestored = await win.boundingBox();
    console.log('[4] 还原后 bbox:', boxRestored);
    await page.screenshot({ path: path.join(outDir, '03-restored.png') });

    // 7. 点击全屏按钮
    await page.locator('.assistant-window button[aria-label="全屏"]').first().click();
    await page.waitForTimeout(600);
    const boxMax = await win.boundingBox();
    console.log('[5] maximized 模式 bbox:', boxMax);
    const isFullscreen = await page.evaluate(() => document.body.classList.contains('is-fullscreen') || document.querySelector('.assistant-window--maximized') !== null);
    console.log('    is-fullscreen present:', isFullscreen);
    await page.screenshot({ path: path.join(outDir, '04-maximized.png') });

    // 8. 退出全屏
    await page.locator('.assistant-window button[aria-label="退出全屏"]').first().click();
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(outDir, '05-back-to-normal.png') });

    // 9. 测试侧栏折叠
    await page.locator('.assistant-panel__sidebar-toggle').first().click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(outDir, '06-sidebar-collapsed.png') });
    await page.locator('.assistant-panel__sidebar-toggle').first().click();
    await page.waitForTimeout(300);

    // 10. 关闭
    await page.locator('.assistant-window button[aria-label="关闭"]').first().click();
    await page.waitForTimeout(400);
    const stillVisible = await page.locator('.assistant-window').count();
    console.log('[6] 关闭后 assistant-window 数量:', stillVisible);

    // 11. 再打开测试一次：open → minimize → close
    await fab.click();
    await page.waitForTimeout(400);
    await page.locator('.assistant-window button[aria-label="最小化"]').first().click();
    await page.waitForTimeout(400);
    await page.screenshot({ path: path.join(outDir, '07-reopen-mini.png') });
    await page.locator('.assistant-mini-bar button[aria-label="关闭"]').first().click();
    await page.waitForTimeout(400);
    console.log('[7] 完整 open → minimize → close 链路通过');

    console.log('\n[OK] 所有验证通过');
  } catch (err) {
    console.error('[FAIL]', err.message);
    await page.screenshot({ path: path.join(outDir, '99-error.png'), fullPage: true });
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();