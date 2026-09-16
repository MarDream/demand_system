// 边界验证：防环、三层嵌套、跨 Base、鉴权
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-table-group-edge.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const OUT = path.join(__dirname, 'out');
const log = (...a) => console.log(...a);

let pass = 0, fail = 0;
const check = (label, ok, extra) => {
  if (ok) { pass++; log('  PASS  ' + label); }
  else { fail++; log('  FAIL  ' + label + (extra ? '  ' + extra : '')); }
};

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error') errs.push('console: ' + m.text()); });

  const snap = () => page.locator('.tgt-node').evaluateAll((els) => els.map((el) => ({
    key: el.dataset.key,
    indent: parseInt(el.style.paddingLeft) || 0,
    name: el.querySelector('.tgt-node__name')?.textContent?.trim(),
    count: el.querySelector('.tgt-node__count')?.textContent?.trim() || '',
  })));
  const names = (s) => s.map((n) => `${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`);
  const groupRow = (name) => page.locator('.tgt-node--group').filter({ hasText: name }).first();
  const menuItem = (t) => page.locator('.el-dropdown-menu__item:visible').filter({ hasText: t }).first();

  const createdGroups = [];   // 测试期间新建的分组 id，最后清理

  // el-message 会堆叠，取最后一条前先等上一条消失，避免读到上一次的残留提示
  const waitNoMessage = async () => {
    await page.waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 }).catch(() => {});
  };
  const lastMessage = async () => {
    await page.waitForSelector('.el-message', { timeout: 8000 }).catch(() => {});
    return page.locator('.el-message').last().innerText().catch(() => '');
  };

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });

    const cookies = await ctx.cookies();
    const tokenCookie = cookies.find((c) => /access[_-]?token/i.test(c.name));
    log('[1] 登录成功，cookie:', cookies.map((c) => c.name).join(', '));
    check('拿到 access_token cookie', !!tokenCookie);

    const token = tokenCookie ? tokenCookie.value : '';
    const api = async (method, url, body) => {
      const res = await ctx.request.fetch(BASE + url, {
        method,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        data: body === undefined ? undefined : JSON.stringify(body),
      });
      let json = null;
      try { json = await res.json(); } catch { /* 非 JSON */ }
      return { status: res.status(), body: json };
    };

    // ---------- 打开编辑器 ----------
    await page.goto(`${BASE}/bitable/1`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);

    // ---------- 三层嵌套 ----------
    log('\n[2] 三层嵌套');
    await page.locator('.tgt-header__btn[title="新建分组"]').click();
    await page.locator('.tgt-editor input').waitFor({ timeout: 5000 });
    await page.locator('.tgt-editor input').fill('__E_L1');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(1200);

    for (const [parent, child] of [['__E_L1', '__E_L2'], ['__E_L2', '__E_L3']]) {
      await groupRow(parent).hover();
      await groupRow(parent).locator('.tgt-node__tool[title="新建子分组"]').click();
      await page.locator('.tgt-editor input').waitFor({ timeout: 5000 });
      await page.locator('.tgt-editor input').fill(child);
      await page.keyboard.press('Enter');
      await page.waitForTimeout(1200);
    }
    const nested = await snap();
    log('    ', names(nested));
    const i1 = nested.find((n) => n.name === '__E_L1')?.indent;
    const i2 = nested.find((n) => n.name === '__E_L2')?.indent;
    const i3 = nested.find((n) => n.name === '__E_L3')?.indent;
    check('三层嵌套缩进递增', i1 < i2 && i2 < i3, `${i1} < ${i2} < ${i3}`);

    // ---------- UI 防环：把 L1 拖到它的子孙 L3 ----------
    log('\n[3] UI 防环');
    await waitNoMessage();
    await groupRow('__E_L1').dragTo(groupRow('__E_L3'));
    await page.waitForTimeout(1200);
    const warnText = await lastMessage();
    log('    提示文案:', JSON.stringify(warnText));
    const afterCycle = await snap();
    check('拖入自身子孙被拒绝（有警告提示）', /子分组/.test(warnText), warnText);
    check('L1 仍在根层级（缩进未变）', afterCycle.find((n) => n.name === '__E_L1')?.indent === i1);

    // ---------- 「全部实体」不可拖入 ----------
    log('\n[4] 「全部实体」只读');
    await waitNoMessage();
    const tableRow = page.locator('.tgt-node--table').first();
    const tableName = (await tableRow.locator('.tgt-node__name').innerText()).trim();
    await tableRow.dragTo(page.locator('.tgt-node[data-key="all"]'));
    await page.waitForTimeout(1000);
    const warn2 = await lastMessage();
    log('    提示文案:', JSON.stringify(warn2), '| 拖动的表:', tableName);
    check('拖到「全部实体」被拒绝', /全部实体/.test(warn2), warn2);

    // ---------- API 层：防环 / 跨 Base / 鉴权 ----------
    log('\n[5] API 层校验');
    const tree = await api('GET', '/api/v1/bitable/bases/1/table-groups');
    const flat = [];
    const walk = (l) => (l || []).forEach((g) => { flat.push(g); walk(g.children); });
    walk(tree.body?.data);
    const gL1 = flat.find((g) => g.name === '__E_L1');
    const gL3 = flat.find((g) => g.name === '__E_L3');
    check('树接口返回 L1/L3', !!gL1 && !!gL3);
    check('L1 totalTableCount 已汇总', gL1 && typeof gL1.totalTableCount === 'number');

    const cycle = await api('PUT', `/api/v1/bitable/table-groups/${gL1.id}/move`, { parentId: gL3.id });
    log('    L1->L3 move:', cycle.status, JSON.stringify(cycle.body?.message));
    check('后端拒绝把分组移到自己子孙下', cycle.status !== 200, `status=${cycle.status}`);

    const self = await api('PUT', `/api/v1/bitable/table-groups/${gL1.id}/move`, { parentId: gL1.id });
    log('    L1->L1 move:', self.status, JSON.stringify(self.body?.message));
    check('后端拒绝把分组移到自己下面', self.status !== 200, `status=${self.status}`);

    // 找一个别的 base
    const bases = await api('GET', '/api/v1/bitable/bases');
    const list = Array.isArray(bases.body?.data) ? bases.body.data : (bases.body?.data?.list || []);
    const otherBase = list.find((b) => b.id !== 1);
    log('    可用 base:', list.map((b) => b.id).join(', '), '| 选用:', otherBase?.id);
    if (otherBase) {
      const foreign = await api('POST', `/api/v1/bitable/bases/${otherBase.id}/table-groups`, { name: '__E_FOREIGN' });
      const foreignId = foreign.body?.data;
      log('    外部 base 分组:', foreign.status, foreignId);
      if (foreignId) {
        createdGroups.push({ id: foreignId, baseId: otherBase.id });
        const cross = await api('PUT', `/api/v1/bitable/table-groups/${gL1.id}/move`, { parentId: foreignId });
        log('    跨 base move:', cross.status, JSON.stringify(cross.body?.message));
        check('后端拒绝跨 Base 移动分组', cross.status !== 200, `status=${cross.status}`);

        const crossCreate = await api('POST', '/api/v1/bitable/bases/1/table-groups', { name: '__E_X', parentId: foreignId });
        log('    跨 base 建子分组:', crossCreate.status, JSON.stringify(crossCreate.body?.message));
        check('后端拒绝把分组建到别的 Base 下', crossCreate.status !== 200, `status=${crossCreate.status}`);
      }
    }

    // 未带 token
    const anon = await ctx.request.fetch(BASE + '/api/v1/bitable/bases/1/table-groups', { method: 'GET' });
    log('    未带 token:', anon.status());
    check('未鉴权访问被拒绝', anon.status() === 401 || anon.status() === 403, `status=${anon.status()}`);

    // ---------- 清理：删掉 L1（L2/L3 应上移） ----------
    log('\n[6] 清理测试分组');
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(800);
    await groupRow('__E_L1').hover();
    await groupRow('__E_L1').locator('.tgt-node__tool[title="更多操作"]').click();
    await menuItem('删除分组').click();
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await page.waitForTimeout(1500);
    const afterDel = await snap();
    log('    ', names(afterDel));
    check('L1 已删除', !afterDel.some((n) => n.name === '__E_L1'));
    check('L2 上移到根层级（缩进回到 6）', afterDel.find((n) => n.name === '__E_L2')?.indent === 6);

    // 逐个删掉剩余测试分组
    for (const name of ['__E_L2', '__E_L3']) {
      const row = groupRow(name);
      if (await row.count()) {
        await row.hover();
        await row.locator('.tgt-node__tool[title="更多操作"]').click();
        await menuItem('删除分组').click();
        await page.locator('.el-message-box__btns .el-button--primary').first().click();
        await page.waitForTimeout(1200);
      }
    }
    const finalSnap = await snap();
    log('    最终:', names(finalSnap));
    check('测试分组已清空', !finalSnap.some((n) => n.name.startsWith('__E_')));

    for (const g of createdGroups) {
      await api('DELETE', `/api/v1/bitable/table-groups/${g.id}`);
    }

    await page.screenshot({ path: path.join(OUT, 'edge-final.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'edge-error.png') }).catch(() => {});
  } finally {
    log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
    await browser.close();
    if (fail > 0) process.exitCode = 1;
  }
}

main();
