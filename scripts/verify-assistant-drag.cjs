// 验证弹框拖拽：normal 态拖 header、minimized 态拖迷你条；全屏态不拖
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

  // 从 a 点拖到 b 点
  async function dragBy(from, to, steps = 12) {
    await page.mouse.move(from.x, from.y);
    await page.mouse.down();
    for (let i = 1; i <= steps; i++) {
      await page.mouse.move(
        from.x + ((to.x - from.x) * i) / steps,
        from.y + ((to.y - from.y) * i) / steps,
      );
    }
    await page.mouse.up();
    await page.waitForTimeout(300);
  }

  const boxOf = async (sel) => page.locator(sel).first().boundingBox();

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

    console.log('\n--- 1. normal 态：拖 header 移动窗口 ---');
    let box = await boxOf('.assistant-window');
    const before = { x: box.x, y: box.y };
    // header 左侧空白处（避开按钮和标题）
    const headerBox = await boxOf('.assistant-panel__header');
    const grabAt = { x: headerBox.x + 60, y: headerBox.y + headerBox.height / 2 };
    await dragBy(grabAt, { x: grabAt.x - 300, y: grabAt.y - 150 });
    box = await boxOf('.assistant-window');
    console.log(`  拖动前 (${Math.round(before.x)}, ${Math.round(before.y)}) -> 拖动后 (${Math.round(box.x)}, ${Math.round(box.y)})`);
    ok(Math.abs(box.x - before.x) > 200, 'normal 态窗口水平移动生效');
    ok(Math.abs(box.y - before.y) > 100, 'normal 态窗口垂直移动生效');
    await page.screenshot({ path: path.join(outDir, '30-drag-normal.png') });

    console.log('\n--- 2. 拖 header 上的按钮不应移动窗口 ---');
    const boxBeforeBtn = await boxOf('.assistant-window');
    const miniBtn = await boxOf('.assistant-window button[aria-label="最小化"]');
    await page.mouse.move(miniBtn.x + miniBtn.width / 2, miniBtn.y + miniBtn.height / 2);
    await page.mouse.down();
    await page.mouse.move(miniBtn.x + 100, miniBtn.y + 80);
    await page.mouse.up();
    await page.waitForTimeout(300);
    const boxAfterBtn = await boxOf('.assistant-window');
    const mode = await page.locator('.assistant-mini-bar').count();
    console.log(`  按钮上拖动后位移: dx=${Math.round(boxAfterBtn.x - boxBeforeBtn.x)} dy=${Math.round(boxAfterBtn.y - boxBeforeBtn.y)}, 迷你条数量=${mode}`);
    // 在最小化按钮上按下并移动，不应产生拖拽位移（松手可能触发最小化，属于预期）
    ok(mode === 1 || Math.abs(boxAfterBtn.x - boxBeforeBtn.x) < 5, '在按钮上拖拽不会带动窗口');

    // 确保处于 minimized 态
    if (mode === 0) {
      await page.locator('.assistant-window button[aria-label="最小化"]').first().click();
      await page.waitForTimeout(600);
    }

    console.log('\n--- 3. minimized 态：拖迷你条移动 ---');
    let mBox = await boxOf('.assistant-window');
    const mBefore = { x: mBox.x, y: mBox.y };
    // 抓热区（头像+标题区），避开右侧按钮
    const hitBox = await boxOf('.assistant-mini-bar__hit');
    // 抓取点相对窗口左上角的偏移，据此把窗口精确拖到视口内的目标点（避免撞边界被 clamp）
    const offX = hitBox.x + 60 - mBefore.x;
    const offY = hitBox.y + hitBox.height / 2 - mBefore.y;
    const mGrab = { x: mBefore.x + offX, y: mBefore.y + offY };
    const TARGET = { x: 420, y: 520 };
    await dragBy(mGrab, { x: TARGET.x + offX, y: TARGET.y + offY }, 15);
    mBox = await boxOf('.assistant-window');
    console.log(`  迷你条 (${Math.round(mBefore.x)}, ${Math.round(mBefore.y)}) -> (${Math.round(mBox.x)}, ${Math.round(mBox.y)})，目标 (${TARGET.x}, ${TARGET.y})`);
    ok(Math.abs(mBox.x - TARGET.x) < 8, 'minimized 态迷你条水平移动到目标位');
    ok(Math.abs(mBox.y - TARGET.y) < 8, 'minimized 态迷你条垂直移动到目标位');
    ok(Math.abs(mBox.x - mBefore.x) > 50 || Math.abs(mBox.y - mBefore.y) > 50, '迷你条位置确实发生了变化');
    // 拖完不应误触发还原
    const stillMini = await page.locator('.assistant-mini-bar').count();
    ok(stillMini === 1, '拖动结束后没有误触发还原');
    await page.screenshot({ path: path.join(outDir, '31-drag-mini.png') });

    console.log('\n--- 4. 点击（不拖动）仍能还原 ---');
    await page.locator('.assistant-mini-bar__hit').click();
    await page.waitForTimeout(600);
    const back = await page.locator('.assistant-panel__header').count();
    ok(back === 1, '纯点击热区可正常还原为普通窗口');

    console.log('\n--- 5. 边界约束：拖到左上角不越界 ---');
    const h2 = await boxOf('.assistant-panel__header');
    await dragBy({ x: h2.x + 60, y: h2.y + h2.height / 2 }, { x: -200, y: -200 }, 15);
    const clamped = await boxOf('.assistant-window');
    console.log(`  越界拖拽后位置 (${Math.round(clamped.x)}, ${Math.round(clamped.y)})`);
    ok(clamped.x >= -1 && clamped.y >= -1, '窗口不会被拖出视口左上角');

    console.log(fail.length === 0 ? '\n[OK] 拖拽验证全部通过' : `\n[FAIL] ${fail.length} 项未通过`);
    if (fail.length) process.exitCode = 1;
  } catch (err) {
    console.error('[ERROR]', err.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();