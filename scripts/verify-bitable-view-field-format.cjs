// 验证「字段属性在网格之外的视图里也生效」—— 本次完善的核心是把取值口径收敛到
// `formatCellDisplay()`，此前 看板/画廊/日历/甘特 各写一套，导致同一条数据在不同视图显示不一致。
//
// 覆盖：
//   [A] 纯函数口径：动态 import 应用正在使用的那个模块，逐类型断言格式化结果
//       （含历史 bug：进度 85 在看板被渲染成 8500%）
//   [B] 网格视图：列按属性格式化
//   [C] 看板视图：卡片按属性格式化，且不再出现 ×100 的进度
//
// 用法（NODE_PATH 必须是 Windows 路径）：
//   NODE_PATH=E:/Project/Vue_demo/demand_system/demand_frontend/node_modules \
//     node scripts/verify-bitable-view-field-format.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
const OUT = path.join(__dirname, 'out');

const SUFFIX = String(Date.now() % 100000000).padStart(8, '0');

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
  const ctx = await browser.newContext({ viewport: { width: 1600, height: 900 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  let token = '';
  let baseId = null;

  // ---------- 工具 ----------
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

  const waitNoToast = () =>
    page
      .waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 })
      .catch(() => {});

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    token = (await ctx.cookies(BASE)).find((c) => c.name === 'access_token')?.value || '';
    console.log('[1] 登录成功');

    // ================= [A] 纯函数口径 =================
    // 直接 import dev server 正在提供的那个模块，断言的就是应用实际跑的代码
    console.log('\n[A] formatCellDisplay 取值口径');
    const A = await page.evaluate(async () => {
      const mod = await import('/src/utils/bitableFieldConfig.ts');
      const f = (fieldType, config) => ({ id: 1, name: 'f', fieldType, config: config || {} });
      const c = (o) => Object.assign({ fieldId: 1 }, o);
      const fmt = (fieldType, config, cell) => mod.formatCellDisplay(f(fieldType, config), c(cell));
      return {
        progress85: fmt('progress', {}, { valueNumber: 85 }),
        progressDecimal: fmt('progress', { precision: 1 }, { valueNumber: 33.3 }),
        numberPrefix: fmt('number', { precision: 2, thousandSeparator: true, prefix: '¥' }, { valueNumber: 1234.5 }),
        numberPercent: fmt('number', { numberFormat: 'percent' }, { valueNumber: 85 }),
        currencyUsd: fmt(
          'currency',
          { currency: 'USD', currencySymbolPosition: 'prefix', precision: 2, thousandSeparator: true },
          { valueNumber: 1234.5 },
        ),
        currencySuffix: fmt('currency', { currency: 'CNY', currencySymbolPosition: 'suffix', precision: 0 }, { valueNumber: 99 }),
        dateNoTime: fmt('date', { dateFormat: 'YYYY/MM/DD', withTime: false }, { valueDate: '2026-09-16 00:00:00' }),
        dateWithTime: fmt(
          'date',
          { dateFormat: 'YYYY-MM-DD', withTime: true, timeFormat: '24h' },
          { valueDate: '2026-09-16 14:35:00' },
        ),
        ratingHeart3: fmt('rating', { maxRating: 3, ratingIcon: 'heart' }, { valueNumber: 3 }),
        ratingNumber: fmt('rating', { maxRating: 5, ratingIcon: 'number' }, { valueNumber: 3 }),
        ratingHalf: fmt('rating', { maxRating: 5, ratingIcon: 'star', allowHalf: true }, { valueNumber: 2.5 }),
        checkboxOn: fmt('checkbox', {}, { valueText: 'true' }),
        checkboxOff: fmt('checkbox', {}, { valueText: 'false' }),
        phoneMasked: fmt('phone', { masked: true }, { valueText: '13812345678' }),
        phonePlain: fmt('phone', { masked: false }, { valueText: '13812345678' }),
        locationName: fmt('location', {}, { valueJson: { name: '深圳湾', address: '南山区' } }),
        locationAddress: fmt('location', { locationDisplayMode: 'address' }, { valueJson: { name: '深圳湾', address: '南山区' } }),
        locationLatLng: fmt('location', { locationDisplayMode: 'latlng' }, { valueJson: { lat: 22.5, lng: 114 } }),
        locationString: fmt('location', {}, { valueJson: '深圳市南山区科技园' }),
        multiSelect: fmt('multi_select', {}, { valueJson: ['A', 'B'] }),
        attachment: fmt('attachment', {}, { valueJson: [{ name: 'a.pdf' }, { name: 'b.png' }] }),
        // 旧看板视图的 ×100 约定，必须不复存在
        progressLegacyScaling: fmt('progress', {}, { valueNumber: 85 }) === '8500%',
      };
    });

    check('进度 85 → "85%"（不做 ×100 缩放）', A.progress85 === '85%', JSON.stringify(A.progress85));
    check('旧 ×100 行为已不存在', A.progressLegacyScaling === false);
    check('进度 33.3 + precision1 → "33.3%"', A.progressDecimal === '33.3%', JSON.stringify(A.progressDecimal));
    check('数字 前缀+千分位+2位 → "¥1,234.50"', A.numberPrefix === '¥1,234.50', JSON.stringify(A.numberPrefix));
    check('数字 百分比格式 → "85%"', A.numberPercent === '85%', JSON.stringify(A.numberPercent));
    check('货币 USD 前缀 → "$1,234.50"', A.currencyUsd === '$1,234.50', JSON.stringify(A.currencyUsd));
    check('货币 符号后置 → "99¥"', A.currencySuffix === '99¥', JSON.stringify(A.currencySuffix));
    check('日期 自定义格式 → "2026/09/16"', A.dateNoTime === '2026/09/16', JSON.stringify(A.dateNoTime));
    check('日期 含时间 → "2026-09-16 14:35"', A.dateWithTime === '2026-09-16 14:35', JSON.stringify(A.dateWithTime));
    check('评分 maxRating=3 爱心 → "♥♥♥"', A.ratingHeart3 === '♥♥♥', JSON.stringify(A.ratingHeart3));
    check('评分 数字样式 → "3 / 5"', A.ratingNumber === '3 / 5', JSON.stringify(A.ratingNumber));
    check('评分 允许半星 → "★★½"', A.ratingHalf === '★★½', JSON.stringify(A.ratingHalf));
    check('复选框 真 → "✓"', A.checkboxOn === '✓', JSON.stringify(A.checkboxOn));
    check('复选框 假 → "✗"', A.checkboxOff === '✗', JSON.stringify(A.checkboxOff));
    check('电话 脱敏 → "138****5678"', A.phoneMasked === '138****5678', JSON.stringify(A.phoneMasked));
    check('电话 不脱敏 → 原值', A.phonePlain === '13812345678', JSON.stringify(A.phonePlain));
    check('位置 默认显示地名', A.locationName === '深圳湾', JSON.stringify(A.locationName));
    check('位置 显示详细地址', A.locationAddress === '南山区', JSON.stringify(A.locationAddress));
    check('位置 显示经纬度 → "22.5,114"', A.locationLatLng === '22.5,114', JSON.stringify(A.locationLatLng));
    check('位置 纯字符串值 → 原样', A.locationString === '深圳市南山区科技园', JSON.stringify(A.locationString));
    check('多选 → "A, B"', A.multiSelect === 'A, B', JSON.stringify(A.multiSelect));
    check('附件 → "a.pdf, b.png"', A.attachment === 'a.pdf, b.png', JSON.stringify(A.attachment));

    // ================= 造数据 =================
    console.log('\n[2] 准备测试数据');
    const baseResp = await api('/v1/bitable/bases', {
      method: 'POST',
      body: JSON.stringify({ name: `__VFMT_${SUFFIX}` }),
    });
    baseId = dataOf(baseResp);
    check('创建测试 Base', baseResp.status === 200 && !!baseId, JSON.stringify(baseResp.body));

    const tableId = dataOf(
      await api(`/v1/bitable/bases/${baseId}/tables`, {
        method: 'POST',
        body: JSON.stringify({ name: '格式表' }),
      }),
    );
    check('创建测试数据表', !!tableId);

    const mkField = async (name, fieldType, config) => {
      const r = await api(`/v1/bitable/tables/${tableId}/fields`, {
        method: 'POST',
        body: JSON.stringify({ name, fieldType, ...(config ? { config } : {}) }),
      });
      return dataOf(r);
    };

    // 创建顺序决定卡片上展示的字段（看板取前 4 个），把要断言的排前面
    const fTitle = await mkField('标题', 'text');
    const fGroup = await mkField('分组', 'single_select', {
      options: [
        { label: '进行中', color: 'blue' },
        { label: '已完成', color: 'green' },
      ],
    });
    const fProgress = await mkField('进度', 'progress');
    const fAmount = await mkField('金额', 'number', { precision: 2, thousandSeparator: true, prefix: '¥' });
    const fDate = await mkField('日期', 'date', { dateFormat: 'YYYY/MM/DD', withTime: false });
    const fRating = await mkField('评分', 'rating', { maxRating: 3, ratingIcon: 'heart' });
    const fPhone = await mkField('电话', 'phone', { masked: true });
    const fLocation = await mkField('位置', 'location', { locationDisplayMode: 'name' });

    check(
      '8 个字段创建成功',
      [fTitle, fGroup, fProgress, fAmount, fDate, fRating, fPhone, fLocation].every(Boolean),
      JSON.stringify({ fTitle, fGroup, fProgress, fAmount, fDate, fRating, fPhone, fLocation }),
    );

    const recResp = await api(`/v1/bitable/tables/${tableId}/records`, {
      method: 'POST',
      body: JSON.stringify({
        cells: {
          [String(fTitle)]: { valueText: `Alpha${SUFFIX}` },
          [String(fGroup)]: { valueText: '进行中' },
          [String(fProgress)]: { valueNumber: 85 },
          [String(fAmount)]: { valueNumber: 1234.5 },
          [String(fDate)]: { valueDate: '2026-09-16 00:00:00' },
          [String(fRating)]: { valueNumber: 3 },
          [String(fPhone)]: { valueText: '13812345678' },
          [String(fLocation)]: { valueJson: { name: '深圳湾', address: '广东省深圳市南山区' } },
        },
      }),
    });
    check('创建测试记录', recResp.status === 200 && !!dataOf(recResp), JSON.stringify(recResp.body));
    const recId = dataOf(recResp);

    // ================= [B] 网格视图 =================
    console.log('\n[B] 网格视图');
    await page.goto(`${BASE}/bitable/${baseId}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(2000);

    const gridText = await page
      .locator('.vxe-table--body')
      .first()
      .innerText()
      .catch(() => '');
    check('网格：进度列显示 85%', gridText.includes('85%'), gridText.replace(/\s+/g, ' ').slice(0, 200));
    check('网格：金额列显示 ¥1,234.50', gridText.includes('¥1,234.50'));
    check('网格：日期列显示 2026/09/16', gridText.includes('2026/09/16'));
    check('网格：电话列脱敏', gridText.includes('138****5678'));
    check('网格：位置列显示地名而非 [object Object]', gridText.includes('深圳湾') && !gridText.includes('[object Object]'));
    // innerText 会在行内元素之间插空白（flex + gap），比较前先压掉空白
    const compact = gridText.replace(/\s+/g, '');
    check('网格：评分列画出生效图标（3 颗爱心）', compact.includes('♥♥♥'), compact.slice(0, 160));
    await page.screenshot({ path: path.join(OUT, 'vfmt-grid.png') }).catch(() => {});

    // ================= [D] 内联编辑不能因为改渲染器名而失效 =================
    // GridView 把 editRender.name 从 VxeXxx 改成 BitableXxx，靠 renderTableEdit 委托给原生编辑器。
    // 这里必须验证「点进去还能编辑」，否则就是把展示修好了、编辑修坏了。
    console.log('\n[D] 内联编辑仍然可用');
    // Playwright 的 fill() 对「受控 + 带格式化」的编辑器不可靠：它直接改 DOM value，
    // vxe 的格式化逻辑会把旧值拼回来（实测数字列落库 1234.5777 而不是 777.77）。
    // 这里统一走真实键盘路径：三击全选 → 再 Ctrl+A 兜底 → 删除 → 逐字输入。
    async function typeIntoEditor(input, value) {
      await input.click({ clickCount: 3 });
      await page.keyboard.press('Control+A');
      await page.keyboard.press('Backspace');
      await page.keyboard.type(String(value), { delay: 30 });
    }

    const headerTexts = await page.locator('.vxe-header--column').allInnerTexts();
    const colIndex = (name) => headerTexts.findIndex((t) => t.includes(name));
    const firstRowCells = page.locator('.vxe-body--row').first().locator('.vxe-body--column');

    const amountIdx = colIndex('金额');
    check('能定位到「金额」列', amountIdx >= 0, JSON.stringify(headerTexts));
    const amountCell = firstRowCells.nth(amountIdx);
    await amountCell.click();
    await page.waitForTimeout(600);
    const amountInput = amountCell.locator('input').first();
    check('数字单元格点击后挂载原生编辑器', (await amountInput.count()) > 0);
    await typeIntoEditor(amountInput, '777.77');
    await amountInput.press('Enter');
    await page.waitForTimeout(1800);
    await waitNoToast();

    const recAfter = dataOf(await api(`/v1/bitable/records/${recId}`)) || {};
    const savedAmount = (recAfter.cells || {})[String(fAmount)]?.valueNumber;
    check('数字编辑已落库 777.77', Number(savedAmount) === 777.77, JSON.stringify(savedAmount));
    const gridAfterEdit = await page.locator('.vxe-table--body').first().innerText().catch(() => '');
    check('编辑后仍按属性展示 ¥777.77', gridAfterEdit.includes('¥777.77'), gridAfterEdit.replace(/\s+/g, ' ').slice(0, 160));

    const titleIdx = colIndex('标题');
    const titleCell = firstRowCells.nth(titleIdx);
    await titleCell.click();
    await page.waitForTimeout(600);
    const titleInput = titleCell.locator('input').first();
    check('文本单元格点击后挂载原生编辑器', (await titleInput.count()) > 0);
    await typeIntoEditor(titleInput, 'Beta');
    await titleInput.press('Enter');
    await page.waitForTimeout(1800);
    await waitNoToast();
    const recAfterText = dataOf(await api(`/v1/bitable/records/${recId}`)) || {};
    const savedTitle = (recAfterText.cells || {})[String(fTitle)]?.valueText;
    check('文本编辑已落库 Beta', savedTitle === 'Beta', JSON.stringify(savedTitle));

    // ================= [C] 看板视图 =================
    console.log('\n[C] 看板视图');
    await page.getByRole('button', { name: /新建视图/ }).first().click();
    await page.waitForTimeout(400);
    await page
      .locator('.el-dropdown-menu__item:visible')
      .filter({ hasText: '看板视图' })
      .first()
      .click();
    await page.waitForTimeout(2500);
    await waitNoToast();
    await page.waitForTimeout(1500);

    const cardCount = await page.locator('.kanban-card').count();
    check('看板出现卡片', cardCount > 0, 'cards=' + cardCount);

    const cardText = await page
      .locator('.kanban-card')
      .first()
      .innerText()
      .catch(() => '');
    const flat = cardText.replace(/\s+/g, ' ');
    check('看板卡片：进度显示 85%（不是 8500%）', cardText.includes('85%') && !cardText.includes('8500%'), flat);
    // 金额/标题在 [D] 里被就地改成 777.77 / Beta：这里顺带验证「网格改完，看板取到新值且仍按属性格式化」
    check('看板卡片：金额显示 ¥777.77', cardText.includes('¥777.77'), flat);
    check('看板卡片：日期显示 2026/09/16', cardText.includes('2026/09/16'), flat);
    check('看板卡片：标题取文本字段值 Beta', cardText.includes('Beta'), flat);
    await page.screenshot({ path: path.join(OUT, 'vfmt-kanban.png') }).catch(() => {});

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
