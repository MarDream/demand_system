// 断言：助手界面不再有重复入口
// 1) 停止生成：全屏内只有 composer 一处（HUD 里的已移除）
// 2) 快捷提问：空状态下不重复渲染同一批问题（中央一次，composer 0 次）
// 3) 迷你条还原：只有「热区」+「显式还原按钮」两个入口，没有双击/头像独立点击
const path = require('node:path');
const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const outDir = path.join(__dirname, 'out');

  const fail = [];
  const ok = (cond, msg) => {
    console.log((cond ? '  OK   ' : '  FAIL ') + msg);
    if (!cond) fail.push(msg);
  };

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
    await page.waitForTimeout(700);

    console.log('\n--- 1. 快捷提问不重复（空状态）---');
    const emptyChips = await page.locator('.assistant-empty__quick-asks .assistant-chip').count();
    const composerChips = await page.locator('.assistant-composer__quick-asks .assistant-chip').count();
    console.log(`  空状态中央 chips: ${emptyChips}, composer chips: ${composerChips}`);
    ok(composerChips === 0, '空状态下 composer 不再重复渲染快捷提问');
    ok(emptyChips > 0, '空状态中央仍展示推荐问题');
    await page.screenshot({ path: path.join(outDir, '20-no-dup-empty.png') });

    console.log('\n--- 2. 侧栏折叠只有一个显式入口 ---');
    const sidebarToggle = await page.locator('.assistant-panel__sidebar-toggle').count();
    const listFoldBtn = await page.locator('.assistant-session-list__fold-btn').count();
    console.log(`  header 折叠按钮: ${sidebarToggle}, 会话列表内折叠按钮: ${listFoldBtn}`);
    ok(sidebarToggle === 1, 'header 左侧折叠按钮存在（唯一显式开关）');
    ok(listFoldBtn === 0, '会话列表标题栏不再有第二个折叠按钮');

    console.log('\n--- 3. header 操作组只有窗口控制 ---');
    const labels = await page.$$eval('.assistant-panel__action-group button', (els) =>
      els.map((e) => e.getAttribute('aria-label')),
    );
    console.log('  操作组:', JSON.stringify(labels));
    ok(JSON.stringify(labels) === JSON.stringify(['最小化', '全屏', '关闭']), '操作组 = 最小化/全屏/关闭');

    console.log('\n--- 4. 迷你条还原入口收敛为 2 个 ---');
    await page.locator('.assistant-window button[aria-label="最小化"]').first().click();
    await page.waitForTimeout(600);
    const hitCount = await page.locator('.assistant-mini-bar__hit').count();
    const restoreBtn = await page.locator('.assistant-mini-bar button[aria-label="还原"]').count();
    const avatarBtn = await page.locator('.assistant-mini-bar__avatar').count();
    console.log(`  热区: ${hitCount}, 还原按钮: ${restoreBtn}, 头像元素: ${avatarBtn}`);
    ok(hitCount === 1, '还原热区 = 1（头像+标题+副标题合并）');
    ok(restoreBtn === 1, '显式还原按钮 = 1');
    // 头像现在是 span（不可点），不是 button
    const avatarTag = await page.$eval('.assistant-mini-bar__avatar', (e) => e.tagName.toLowerCase());
    console.log(`  头像元素标签: ${avatarTag}`);
    ok(avatarTag !== 'button', '头像不再是独立可点按钮');
    await page.screenshot({ path: path.join(outDir, '21-mini-single-restore.png') });

    // 点热区能还原
    await page.locator('.assistant-mini-bar__hit').click();
    await page.waitForTimeout(600);
    const backToNormal = await page.locator('.assistant-panel__header').count();
    ok(backToNormal === 1, '点击热区可还原为普通窗口');

    console.log('\n--- 5. 停止生成唯一入口（源码级已在 HUD 移除，此处确认 composer 存在）---');
    const sendOrStop = await page.locator('.assistant-composer button:has-text("发送"), .assistant-composer button:has-text("停止")').count();
    ok(sendOrStop === 1, 'composer 发送/停止互斥按钮 = 1');

    console.log(fail.length === 0 ? '\n[OK] 去重断言全部通过' : `\n[FAIL] ${fail.length} 项未通过`);
    if (fail.length) process.exitCode = 1;
  } catch (err) {
    console.error('[ERROR]', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();