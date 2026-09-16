// 验证「字段属性」在非网格视图里同样生效 —— 此前看板/画廊/日历/甘特各写了一套取值逻辑，
// 导致同一份数据在不同视图显示不一致（最典型：进度 85 在网格显示 85%、在看板显示 8500%）。
//
// 本脚本自建一套 fixture（Base + 表 + 字段 + 1 条记录 + 3 个视图），逐项取证：
//   [A] 画廊视图按字段属性展示（进度百分比 / 数字精度千分位前后缀 / 日期格式）
//   [B] 看板视图同样按属性展示（进度 / 数字 / 日期 / 评分图标与上限）
//   [C] 日历视图能用「非文本字段」兜底出标题
//   [D] 记录编辑弹框支持多选字段，且只读区不再把结构化值 JSON 串化
//
// 用法（NODE_PATH 必须是 Windows 路径）：
//   NODE_PATH=E:/Project/Vue_demo/demand_system/demand_frontend/node_modules \
//     node scripts/verify-bitable-field-attr-cross-view.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const OUT = path.join(__dirname, 'out');
const BASE_NAME = '__FA_XVIEW';

// 用「今天」构造日期，日历视图默认停在当月，否则记录会落在别的月份里看不见
const _now = new Date();
const _pad = (n) => String(n).padStart(2, '0');
const DATE_ISO = `${_now.getFullYear()}-${_pad(_now.getMonth() + 1)}-${_pad(_now.getDate())}`;
const DATE_DISPLAY = `${_now.getFullYear()}/${_pad(_now.getMonth() + 1)}/${_pad(_now.getDate())}`;

let pass = 0;
let fail = 0;
function check(label, ok, extra) {
  if (ok) {
    pass++;
    console.log('  PASS  ' + label);
  } else {
    fail++;
    console.log('  FAIL  ' + label + (extra ? '  ' + extra : ''));
  }
}

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  let token = '';
  const api = (p, init) =>
    page.evaluate(
      async ({ p, init, token }) => {
        const opts = Object.assign({ method: 'GET' }, init || {});
        opts.credentials = 'include';
        opts.headers = Object.assign(
          { 'Content-Type': 'application/json' },
          token ? { Authorization: 'Bearer ' + token } : {},
          opts.headers || {},
        );
        const r = await fetch('/api' + p, opts);
        let body = null;
        try {
          body = await r.json();
        } catch {
          /* 非 JSON */
        }
        return { status: r.status, body };
      },
      { p, init, token },
    );
  const dataOf = (resp) => (resp && resp.body ? resp.body.data : null);
  // Base/表/字段/记录/视图 的创建接口返回的是裸 ID（前端 TS 类型写成了对象，
  // editor.vue 里也是 `typeof x === 'object' ? x.id : Number(x)` 兜的），这里同样兼容两种形态。
  const asId = (v) => (v && typeof v === 'object' ? v.id ?? null : v == null ? null : Number(v));

  let baseId = null;

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    token = (await ctx.cookies(BASE)).find((c) => c.name === 'access_token')?.value || '';
    check('登录成功并拿到 token', !!token);
    console.log('[1] 登录成功');

    // ---------- 建 fixture ----------
    // 保证可重复执行：先清掉上次异常中断留下的同名 Base
    const existing = dataOf(await api('/v1/bitable/bases')) || [];
    for (const b of existing.filter((x) => x.name === BASE_NAME)) {
      await api(`/v1/bitable/bases/${b.id}`, { method: 'DELETE' });
      console.log('    清理上次残留 Base id=' + b.id);
    }

    baseId = asId(
      dataOf(await api('/v1/bitable/bases', { method: 'POST', body: JSON.stringify({ name: BASE_NAME }) })),
    );
    check('创建测试 Base', !!baseId, 'baseId=' + baseId);
    if (!baseId) throw new Error('无法创建 Base，终止');

    const tableId = asId(
      dataOf(
        await api(`/v1/bitable/bases/${baseId}/tables`, {
          method: 'POST',
          body: JSON.stringify({ name: '__XVIEW_T' }),
        }),
      ),
    );
    check('创建测试数据表', !!tableId, 'tableId=' + tableId);
    if (!tableId) throw new Error('无法创建数据表，终止');

    // 字段顺序很关键：画廊只展示前 3 个非附件字段，看板卡片展示前 4 个非分组字段
    const fieldDefs = [
      { name: '进度', fieldType: 'progress', config: { precision: 0 } },
      { name: '金额', fieldType: 'number', config: { precision: 2, thousandSeparator: true, prefix: '¥' } },
      { name: '日期', fieldType: 'date', config: { dateFormat: 'YYYY/MM/DD', withTime: false } },
      { name: '评分', fieldType: 'rating', config: { maxRating: 10, ratingIcon: 'heart' } },
      {
        name: '等级',
        fieldType: 'single_select',
        config: {
          options: [
            { label: '高', color: 'red' },
            { label: '低', color: 'green' },
          ],
        },
      },
      {
        name: '标签',
        fieldType: 'multi_select',
        config: {
          options: [
            { label: '甲', color: 'blue' },
            { label: '乙', color: 'purple' },
          ],
        },
      },
      { name: '标题', fieldType: 'text', config: {} },
      // 追加在末尾，避免影响「画廊取前 3 个 / 看板取前 4 个」的展示顺序假设
      { name: '日期范围', fieldType: 'date_range', config: {} },
      // 第二个日期字段：日历/甘特的字段选择默认落到第一个日期字段（日期），
      // 只有切到「日期2」才是一次真实变更，才能验证「选择被落库」
      { name: '日期2', fieldType: 'date', config: { dateFormat: 'YYYY/MM/DD', withTime: false } },
    ];

    const fieldIds = {};
    for (const def of fieldDefs) {
      fieldIds[def.name] = asId(
        dataOf(
          await api(`/v1/bitable/tables/${tableId}/fields`, {
            method: 'POST',
            body: JSON.stringify({ name: def.name, fieldType: def.fieldType, config: def.config }),
          }),
        ),
      );
    }
    const missing = fieldDefs.filter((d) => !fieldIds[d.name]).map((d) => d.name);
    check('创建全部字段', missing.length === 0, '缺失=' + missing.join(','));

    // 记录：进度 85（不做 100 倍缩放 → 应显示 85%）、金额 1234.5、日期 2026-03-05、评分 7
    const recId = asId(
      dataOf(
        await api(`/v1/bitable/tables/${tableId}/records`, {
          method: 'POST',
          body: JSON.stringify({
            cells: {
              [fieldIds['进度']]: { valueNumber: 85 },
              [fieldIds['金额']]: { valueNumber: 1234.5 },
              [fieldIds['日期']]: { valueDate: DATE_ISO },
              [fieldIds['评分']]: { valueNumber: 7 },
              [fieldIds['等级']]: { valueText: '高' },
              [fieldIds['标签']]: { valueJson: ['甲', '乙'] },
              [fieldIds['标题']]: { valueText: '属性生效验证' },
              [fieldIds['日期范围']]: { valueJson: { start: DATE_ISO, end: DATE_ISO } },
              [fieldIds['日期2']]: { valueDate: DATE_ISO },
            },
          }),
        }),
      ),
    );
    check('创建测试记录', !!recId, 'recordId=' + recId);

    // ---------- 建视图 ----------
    const viewIds = {};
    for (const vt of ['gallery', 'kanban', 'calendar', 'gantt']) {
      viewIds[vt] = asId(
        dataOf(
          await api(`/v1/bitable/tables/${tableId}/views`, {
            method: 'POST',
            body: JSON.stringify({ name: '__XVIEW_' + vt, viewType: vt, config: {} }),
          }),
        ),
      );
    }
    check('创建 画廊/看板/日历/甘特 四个视图', Object.values(viewIds).every(Boolean), JSON.stringify(viewIds));

    const gotoView = async (vt) => {
      await page.goto(`${BASE}/bitable/${baseId}?viewId=${viewIds[vt]}`, { waitUntil: 'domcontentloaded' });
      await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
      await page.waitForTimeout(2200);
    };

    // ================= [A] 画廊视图 =================
    console.log('\n[A] 画廊视图按字段属性展示');
    await gotoView('gallery');
    const galleryText = await page.locator('.gallery-view').innerText().catch(() => '');
    console.log('    画廊文本: ' + JSON.stringify(galleryText.replace(/\s+/g, ' ').slice(0, 200)));
    check('画廊渲染出卡片', (await page.locator('.gallery-card').count()) > 0);
    check('进度按属性显示 85%（未被 ×100）', galleryText.includes('85%') && !galleryText.includes('8500'), galleryText.slice(0, 120));
    check('数字按属性显示 ¥1,234.50（前缀 + 千分位 + 2 位小数）', galleryText.includes('¥1,234.50'), galleryText.slice(0, 120));
    check('日期按属性显示 ' + DATE_DISPLAY + '（未出现 ISO 串）', galleryText.includes(DATE_DISPLAY) && !galleryText.includes(DATE_ISO), galleryText.slice(0, 120));
    check('画廊标题取自文本字段', galleryText.includes('属性生效验证'), galleryText.slice(0, 120));
    await page.screenshot({ path: path.join(OUT, 'xview-gallery.png') }).catch(() => {});

    // ================= [B] 看板视图 =================
    console.log('\n[B] 看板视图按字段属性展示');
    await gotoView('kanban');
    const kanbanText = await page.locator('.kanban-view').innerText().catch(() => '');
    console.log('    看板文本: ' + JSON.stringify(kanbanText.replace(/\s+/g, ' ').slice(0, 220)));
    check('看板渲染出卡片', (await page.locator('.kanban-card').count()) > 0);
    check('看板进度显示 85%（修复了此前的 8500%）', kanbanText.includes('85%') && !kanbanText.includes('8500'), kanbanText.slice(0, 140));
    check('看板数字显示 ¥1,234.50', kanbanText.includes('¥1,234.50'), kanbanText.slice(0, 140));
    check('看板日期显示 ' + DATE_DISPLAY, kanbanText.includes(DATE_DISPLAY), kanbanText.slice(0, 140));
    check('看板评分按 maxRating/icon 显示 7 个爱心', kanbanText.includes('♥♥♥♥♥♥♥'), kanbanText.slice(0, 160));
    await page.screenshot({ path: path.join(OUT, 'xview-kanban.png') }).catch(() => {});

    // ================= [C] 日历视图 =================
    console.log('\n[C] 日历视图');
    const patches = [];
    page.on('request', (r) => {
      if (r.method() === 'PATCH' && r.url().includes('/bitable/views/')) {
        patches.push(r.url().replace(BASE, '') + ' ' + String(r.postData()).slice(0, 220));
      }
    });
    page.on('response', async (r) => {
      if (r.request().method() === 'PATCH' && r.url().includes('/bitable/views/')) {
        patches.push('  <- ' + r.status() + ' ' + String(await r.text().catch(() => '')).slice(0, 200));
      }
    });
    await gotoView('calendar');
    check('日历视图正常渲染', (await page.locator('.calendar-view').count()) > 0);
    // 日历的日期字段默认落到第一个日期字段，这里切到「日期2」制造一次真实变更
    await page.locator('.calendar-view .el-select').first().click();
    await page.waitForTimeout(400);
    await page
      .locator('.el-select-dropdown__item:visible')
      .filter({ hasText: /^\s*日期2\s*$/ })
      .first()
      .click();
    await page.waitForTimeout(1200);
    const calText = await page.locator('.calendar-view').innerText().catch(() => '');
    check('选择日期字段后日历展示记录', calText.includes('属性生效验证'), calText.replace(/\s+/g, ' ').slice(0, 160));
    await page.screenshot({ path: path.join(OUT, 'xview-calendar.png') }).catch(() => {});

    // 字段选择必须落库到 view.config，否则切走再切回来就白选了
    const viewsAfterCal = dataOf(await api(`/v1/bitable/tables/${tableId}/views`)) || [];
    const calView = viewsAfterCal.find((v) => Number(v.id) === viewIds.calendar);
    check(
      '日历日期字段已落库到 view.config.calendar.startFieldId',
      Number(calView?.config?.calendar?.startFieldId) === fieldIds['日期2'],
      'patches=' + JSON.stringify(patches) + ' config=' + JSON.stringify(calView?.config),
    );
    await gotoView('calendar');
    const calReloadText = await page.locator('.calendar-view').innerText().catch(() => '');
    check(
      '重新进入日历视图，日期字段自动回填并展示记录',
      calReloadText.includes('属性生效验证'),
      calReloadText.replace(/\s+/g, ' ').slice(0, 160),
    );

    // ================= [E] 甘特视图 =================
    console.log('\n[E] 甘特视图');
    await gotoView('gantt');
    check('甘特视图正常渲染', (await page.locator('.gantt-view').count()) > 0);
    const ganttSelects = page.locator('.gantt-view .el-select');
    // 起始字段：默认已落到「日期」，切到「日期2」制造真实变更
    await ganttSelects.first().click();
    await page.waitForTimeout(400);
    await page
      .locator('.el-select-dropdown__item:visible')
      .filter({ hasText: /^\s*日期2\s*$/ })
      .first()
      .click();
    await page.waitForTimeout(900);
    // 范围字段：默认未选，选「日期范围」
    await ganttSelects.nth(1).click();
    await page.waitForTimeout(400);
    await page
      .locator('.el-select-dropdown__item:visible')
      .filter({ hasText: /^\s*日期范围\s*$/ })
      .first()
      .click();
    await page.waitForTimeout(1400);

    const viewsAfterGantt = dataOf(await api(`/v1/bitable/tables/${tableId}/views`)) || [];
    const ganttView = viewsAfterGantt.find((v) => Number(v.id) === viewIds.gantt);
    check(
      '甘特起始字段已落库到 view.config.gantt.startFieldId',
      Number(ganttView?.config?.gantt?.startFieldId) === fieldIds['日期2'],
      'patches=' + JSON.stringify(patches) + ' config=' + JSON.stringify(ganttView?.config),
    );
    check(
      '甘特范围字段已落库到 view.config.gantt.endFieldId',
      Number(ganttView?.config?.gantt?.endFieldId) === fieldIds['日期范围'],
      'patches=' + JSON.stringify(patches) + ' config=' + JSON.stringify(ganttView?.config),
    );
    await gotoView('gantt');
    const ganttReloadText = await page.locator('.gantt-view').innerText().catch(() => '');
    check(
      '重新进入甘特视图，两个字段自动回填并展示记录',
      ganttReloadText.includes('属性生效验证'),
      ganttReloadText.replace(/\s+/g, ' ').slice(0, 160),
    );
    await page.screenshot({ path: path.join(OUT, 'xview-gantt.png') }).catch(() => {});

    // ================= [D] 记录编辑弹框的多选字段 =================
    console.log('\n[D] 记录编辑弹框');
    await gotoView('kanban');
    await page.locator('.kanban-card__actions .el-button').first().click();
    const dlg = page.locator('.el-dialog').filter({ hasText: '编辑记录' }).first();
    await dlg.waitFor({ timeout: 15000 });
    await page.waitForTimeout(800);

    const labels = (await dlg.locator('.el-form-item__label').allTextContents()).map((s) => s.trim());
    check('弹框把「标签」（多选）列为可编辑字段', labels.includes('标签'), JSON.stringify(labels));
    check('弹框把「等级」（单选）列为可编辑字段', labels.includes('等级'));

    const multiItem = dlg
      .locator('.el-form-item')
      .filter({ has: page.locator('.el-form-item__label', { hasText: /^\s*标签\s*$/ }) })
      .first();
    const multiSelectCount = await multiItem.locator('.el-select').count();
    check('多选字段渲染为下拉选择器', multiSelectCount > 0);
    const tagText = await multiItem.innerText().catch(() => '');
    // 卡片用了 collapse-tags：选中 2 项时渲染成「甲 + 1」
    check(
      '多选字段回填了已选值（甲 + 1，即甲乙都被选中）',
      /甲\s*\+\s*1/.test(tagText) || (tagText.includes('甲') && tagText.includes('乙')),
      tagText.replace(/\s+/g, ' '),
    );

    // 只读区不应再出现裸 JSON
    const readonlyText = await dlg.locator('.record-edit-dialog__readonly').innerText().catch(() => '');
    check('只读区未把结构化值 JSON 串化', !readonlyText.includes('{') && !readonlyText.includes('['), readonlyText.replace(/\s+/g, ' ').slice(0, 140));
    await page.screenshot({ path: path.join(OUT, 'xview-record-dialog.png') }).catch(() => {});

    // ================= [F] 网格视图：结构化值不能串化成 [object Object] =================
    console.log('\n[F] 网格视图结构化值展示');
    await page.goto(`${BASE}/bitable/${baseId}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(2400);
    const gridText = (await page.locator('.vxe-table--body').first().innerText().catch(() => '')).replace(/\s+/g, ' ');
    console.log('    网格文本: ' + JSON.stringify(gridText.slice(0, 220)));
    check('网格未把结构化值串化成 [object Object]', !gridText.includes('[object Object]'), gridText.slice(0, 220));
    // 日期范围列没有 dateFormat 配置，直接展示 ISO 日期；日期列配置了 YYYY/MM/DD 所以不会产出 ISO 串
    check('日期范围列按 start ~ end 口径展示', gridText.includes(DATE_ISO), gridText.slice(0, 220));
    await page.screenshot({ path: path.join(OUT, 'xview-grid.png') }).catch(() => {});

    check('全程无前端运行时报错', errs.length === 0, errs.slice(0, 3).join(' | '));
  } finally {
    try {
      if (baseId) {
        const del = await api(`/v1/bitable/bases/${baseId}`, { method: 'DELETE' });
        console.log('  ----  清理 Base ' + baseId + ': status=' + del.status);
      }
    } catch (e) {
      console.log('  ----  清理失败: ' + e.message);
    }
    await browser.close();
  }

  console.log('\n' + '='.repeat(72));
  console.log('结果：PASS ' + pass + ' / FAIL ' + fail);
  console.log('='.repeat(72));
  if (fail > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error('脚本异常：', e);
  process.exitCode = 1;
});
