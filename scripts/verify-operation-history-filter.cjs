// 验证「多维表格 → 操作记录」面板的三项能力
//   1) 按时间范围查询（含「今天 / 最近 7 天 / 最近 30 天」快捷项）
//   2) 按操作用户查询（下拉数据源 = 当前多维表格成员）
//   3) 消息折叠与展开（单条展开/收起 + 合并连续重复操作 + 全部展开/收起）
// 另附：detail 里单元格值的可读化（不再直接显示 {"valueDate":"..."} 这种原始 JSON）
//
// 用法：NODE_PATH=E:/Project/Vue_demo/demand_system/demand_frontend/node_modules \
//       node scripts/verify-operation-history-filter.cjs
// 前置：后端 8081 + 前端 5170 均已启动；admin/admin123 可登录。
// 自清理：为验证「筛选到无操作的用户 → 暂无操作记录」，脚本会临时把测试用户
//        （user 40，测试_commenter）加为 base 15 的 viewer，结束时在 finally 里移除；
//        若该成员本来就存在则不动它。
const path = require('node:path');
const fs = require('node:fs');

const WEB = 'http://127.0.0.1:5170';
const API = 'http://127.0.0.1:8081';
/** 用截图里那个 base（季KPI）+ 其唯一数据表，保证有 update_cell / update_field 记录 */
const BASE_ID = 15;
const TABLE_ID = 20;
const TMP_USER_ID = 40;
const OUT = path.join(__dirname, 'out');

const log = (...a) => console.log(...a);

let pass = 0;
let fail = 0;
function check(label, ok, extra) {
  if (ok) {
    pass++;
    log('  PASS  ' + label);
  } else {
    fail++;
    log('  FAIL  ' + label + (extra ? '  ' + extra : ''));
  }
}

const pad2 = (n) => String(n).padStart(2, '0');
const ymd = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;

async function main() {
  const { chromium } = require('playwright');
  fs.mkdirSync(OUT, { recursive: true });

  // ---------- 直接打接口用的 token（node fetch 不走环境代理） ----------
  const loginRes = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const token = (await loginRes.json()).data.accessToken;
  const apiGet = async (p) => (await fetch(`${API}${p}`, { headers: { Authorization: `Bearer ${token}` } })).json();
  const apiSend = async (method, p, body) =>
    (
      await fetch(`${API}${p}`, {
        method,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: body ? JSON.stringify(body) : undefined,
      })
    ).json();

  // 记录临时加的成员，收尾时移除
  let addedMember = false;
  const memberListBefore = await apiGet(`/api/v1/bitable/bases/${BASE_ID}/members`);
  const hadTmpMember = (memberListBefore.data || []).some((m) => m.userId === TMP_USER_ID);

  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 950 } });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', (e) => errs.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errs.push('console: ' + m.text());
  });

  // 抓操作记录接口的请求 URL，用于断言筛选参数真的发出去了
  const opReqs = [];
  page.on('request', (r) => {
    if (r.url().includes('/operations')) opReqs.push(r.url());
  });
  const lastOpReq = () => opReqs[opReqs.length - 1] || '';

  const drawer = () => page.locator('.el-drawer:visible');
  const items = () => page.locator('.el-drawer:visible .operation-item');
  const expandableLines = () => page.locator('.el-drawer:visible .operation-item__line.is-clickable');
  const badges = () => page.locator('.el-drawer:visible .operation-item__badge');
  const openChanges = () => page.locator('.el-drawer:visible .operation-item__changes');
  const childRows = () => page.locator('.el-drawer:visible .operation-item__child');

  const waitNoMessage = async () => {
    await page.waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 }).catch(() => {});
  };
  /** 「共 N 条」里的 N */
  const totalText = async () => (await drawer().locator('.operation-history__count').innerText()).trim();
  const totalNum = async () => {
    const m = (await totalText()).match(/共\s*(\d+)\s*条/);
    return m ? Number(m[1]) : -1;
  };
  const waitLoaded = async () => {
    await page.waitForTimeout(1200);
    await page.waitForFunction(() => !document.querySelector('.el-loading-mask'), { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(300);
  };
  const openDrawer = async () => {
    await page.locator('.el-dropdown').filter({ hasText: '更多' }).first().locator('button').first().click();
    await page.waitForTimeout(400);
    await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '操作记录' }).first().click();
    await drawer().waitFor({ timeout: 10000 });
    await waitLoaded();
  };
  const closeDrawer = async () => {
    await drawer().locator('.el-drawer__close-btn').first().click().catch(() => {});
    await page.waitForTimeout(700);
  };

  try {
    // ---------- [1] 登录并进入编辑器 ----------
    await page.goto(WEB + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    await page.goto(`${WEB}/bitable/${BASE_ID}`, { waitUntil: 'domcontentloaded' });
    await page.locator('.el-dropdown').filter({ hasText: '更多' }).first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1200);
    log('[2] 已进入多维表格编辑器 baseId=' + BASE_ID);

    // ---------- [2] 打开操作记录 ----------
    await openDrawer();
    check('操作记录抽屉已打开', await drawer().isVisible());
    check('抽屉标题为「操作记录」', (await drawer().locator('.el-drawer__title').innerText()).trim() === '操作记录');

    // ---------- [3] 筛选控件齐全 ----------
    log('\n[3] 筛选控件');
    const d = drawer();
    check('存在范围下拉（当前数据表/整个多维表格）', (await d.locator('.operation-history__filter-row').first().locator('.el-select').count()) >= 1);
    check('存在时间范围选择器', (await d.locator('.el-date-editor').count()) === 1);
    const row2 = d.locator('.operation-history__filter-row').nth(1);
    check('时间范围是 daterange 且带两个输入框', (await row2.locator('.el-range-input').count()) === 2);
    check('存在操作人下拉', (await row2.locator('.el-select').count()) === 1);
    check('存在「合并重复」开关', (await d.locator('.operation-history__toggle .el-switch').count()) === 1);
    check('存在「全部展开/收起」按钮', (await d.getByText(/全部(展开|收起)/).count()) >= 1);

    // ---------- [4] 操作人下拉数据源 = Base 成员 ----------
    log('\n[4] 操作人下拉数据源');
    const membersApi = await apiGet(`/api/v1/bitable/bases/${BASE_ID}/members`);
    const memberNames = (membersApi.data || []).map((m) => m.userName).filter(Boolean).sort();
    log('    Base 成员:', JSON.stringify(memberNames));

    await row2.locator('.el-select').click();
    await page.waitForTimeout(700);
    const userOptions = (await page.locator('.el-select-dropdown__item:visible').allInnerTexts())
      .map((s) => s.trim())
      .filter((s) => !/^全部操作(类型|人)$/.test(s));
    log('    操作人下拉选项:', JSON.stringify(userOptions));
    check('操作人下拉选项与成员接口一致', JSON.stringify([...userOptions].sort()) === JSON.stringify(memberNames), `ui=${JSON.stringify(userOptions)} api=${JSON.stringify(memberNames)}`);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);

    const baseline = await totalNum();
    log('    未筛选总数 =', baseline);
    check('未筛选时能读到总数', baseline >= 0, await totalText());

    // ---------- [5] 按时间范围查询 ----------
    log('\n[5] 时间范围筛选');
    await d.locator('.el-date-editor').click();
    await page.waitForTimeout(700);
    const shortcuts = (await page.locator('.el-picker-panel__shortcut:visible').allInnerTexts()).map((s) => s.trim());
    log('    快捷项:', JSON.stringify(shortcuts));
    check('存在「今天」快捷项', shortcuts.includes('今天'), JSON.stringify(shortcuts));
    check('存在「最近 7 天」快捷项', shortcuts.includes('最近 7 天'), JSON.stringify(shortcuts));

    await page.locator('.el-picker-panel__shortcut:visible').filter({ hasText: '今天' }).first().click();
    await page.waitForTimeout(400);
    await waitLoaded();

    const today = ymd(new Date());
    const reqToday = decodeURIComponent(lastOpReq());
    log('    请求:', reqToday.replace(API, ''));
    check('请求带 startTime=今天 00:00:00', reqToday.includes(`startTime=${today}T00:00:00`), reqToday);
    check('请求带 endTime=今天 23:59:59', reqToday.includes(`endTime=${today}T23:59:59`), reqToday);

    // 探针必须与 UI 同一范围（抽屉默认「当前数据表」），否则总数天然对不上
    const apiToday = await apiGet(`/api/v1/bitable/tables/${TABLE_ID}/operations?pageNum=1&pageSize=1&startTime=${today}T00:00:00&endTime=${today}T23:59:59`);
    check('UI 总数与接口同范围总数一致', (await totalNum()) === apiToday.data.total, `ui=${await totalNum()} api=${apiToday.data.total}`);
    check('「今天」范围能筛出记录', apiToday.data.total > 0, 'total=' + apiToday.data.total);

    // 换一个明确为空的区间（2020 年），验证时间范围真的起过滤作用
    const empty = await apiGet(`/api/v1/bitable/tables/${TABLE_ID}/operations?pageNum=1&pageSize=1&startTime=2020-01-01T00:00:00&endTime=2020-01-31T23:59:59`);
    check('接口对 2020 年区间返回 0 条（时间范围生效）', empty.data.total === 0, 'total=' + empty.data.total);

    // 重置
    await d.getByText('重置').first().click();
    await waitLoaded();
    check('「重置」后总数回到基线', (await totalNum()) === baseline, `now=${await totalNum()} baseline=${baseline}`);
    check('「重置」后时间范围已清空', (await d.locator('.el-range-input').first().inputValue()) === '');

    // ---------- [6] 按操作用户查询 ----------
    log('\n[6] 操作用户筛选');
    // 临时加一个「没有操作记录」的成员，才能验证筛选确实会排除
    if (!hadTmpMember) {
      const r = await apiSend('POST', `/api/v1/bitable/bases/${BASE_ID}/members`, { userId: TMP_USER_ID, role: 'viewer' });
      addedMember = r.code === 200;
      log('    临时加成员 userId=' + TMP_USER_ID + ' →', addedMember ? 'ok' : JSON.stringify(r));
    } else {
      log('    成员 userId=' + TMP_USER_ID + ' 已存在，跳过新增');
    }

    // 重开抽屉让成员列表重新拉取
    await closeDrawer();
    await openDrawer();

    await drawer().locator('.operation-history__filter-row').nth(1).locator('.el-select').click();
    await page.waitForTimeout(700);
    const userOptions2 = (await page.locator('.el-select-dropdown__item:visible').allInnerTexts()).map((s) => s.trim());
    log('    加入临时成员后的下拉:', JSON.stringify(userOptions2));
    const tmpLabel = userOptions2.find((o) => o.includes('测试_commenter'));
    check('下拉包含临时成员「测试_commenter」', !!tmpLabel, JSON.stringify(userOptions2));

    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '测试_commenter' }).first().click();
    await waitLoaded();
    const reqUser = decodeURIComponent(lastOpReq());
    log('    请求:', reqUser.replace(API, ''));
    check('请求带 userId=' + TMP_USER_ID, reqUser.includes(`userId=${TMP_USER_ID}`), reqUser);
    check('筛到无操作的用户时总数 = 0', (await totalNum()) === 0, await totalText());
    check('列表显示「暂无操作记录」', (await drawer().locator('.el-empty').count()) === 1);

    // 换成有操作的用户
    await drawer().locator('.operation-history__filter-row').nth(1).locator('.el-select').click();
    await page.waitForTimeout(700);
    await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '系统管理员' }).first().click();
    await waitLoaded();
    const reqAdmin = decodeURIComponent(lastOpReq());
    check('请求带 userId=1', reqAdmin.includes('userId=1'), reqAdmin);
    check('筛选后总数回到基线', (await totalNum()) === baseline, `now=${await totalNum()} baseline=${baseline}`);

    const names = (await drawer().locator('.operation-item__user').allInnerTexts()).map((s) => s.trim());
    check('筛选后列表里的操作人全部是被选中的用户', names.length > 0 && names.every((n) => n === '系统管理员'), JSON.stringify(names.slice(0, 6)));

    await d.getByText('重置').first().click();
    await waitLoaded();

    // ---------- [7] 折叠与展开 ----------
    log('\n[7] 折叠与展开');
    const mergeSwitch = drawer().locator('.operation-history__toggle .el-switch');
    check('「合并重复」默认开启', (await mergeSwitch.getAttribute('class') || '').includes('is-checked'));

    const mergedItemCount = await items().count();
    const mergedBadges = await badges().count();
    log(`    合并开启：条目 ${mergedItemCount}，×N 徽标 ${mergedBadges}`);
    check('合并开启时出现 ×N 徽标', mergedBadges > 0, 'badges=' + mergedBadges);
    const badgeTexts = await badges().allInnerTexts();
    check('徽标形如 ×N', badgeTexts.every((t) => /^×\d+$/.test(t.trim())), JSON.stringify(badgeTexts));
    check('「共 N 条」里提示了合并组数', /已合并\s*\d+\s*组/.test(await totalText()), await totalText());

    // 默认收起：不应有展开区
    check('默认收起（无展开区）', (await openChanges().count()) === 0);
    check('可展开条目带展开箭头', (await expandableLines().count()) > 0);

    // 单条展开/收起
    const firstExpandable = expandableLines().first();
    const beforeOpen = await openChanges().count();
    await firstExpandable.click();
    await page.waitForTimeout(500);
    check('点击条目后出现展开区', (await openChanges().count()) === beforeOpen + 1);
    await firstExpandable.click();
    await page.waitForTimeout(500);
    check('再次点击后收起', (await openChanges().count()) === beforeOpen);

    // 合并组展开：子条目数应等于 ×N 里的 N
    const badge = badges().first();
    const badgeNum = Number((await badge.innerText()).replace('×', '').trim());
    await badge.locator('xpath=ancestor::div[contains(@class,"operation-item__line")]').first().click();
    await page.waitForTimeout(600);
    const childCount = await childRows().count();
    log(`    合并组 ×${badgeNum} 展开后子条目 = ${childCount}`);
    check('合并组展开后子条目数等于 ×N', childCount === badgeNum, `children=${childCount} badge=${badgeNum}`);
    check('子条目带各自的时间', (await drawer().locator('.operation-item__child-time').count()) === badgeNum);

    // 全部展开 / 全部收起
    await drawer().getByText('全部展开').first().click();
    await page.waitForTimeout(700);
    const expandedAll = await openChanges().count();
    check('「全部展开」后所有可展开条目都展开', expandedAll === (await expandableLines().count()), `open=${expandedAll} expandable=${await expandableLines().count()}`);
    check('「全部展开」后按钮变为「全部收起」', (await drawer().getByText('全部收起').count()) === 1);

    await drawer().getByText('全部收起').first().click();
    await page.waitForTimeout(700);
    check('「全部收起」后无展开区', (await openChanges().count()) === 0);

    // 关闭合并重复
    await mergeSwitch.click();
    await waitLoaded();
    const unmergedItemCount = await items().count();
    log(`    合并关闭：条目 ${unmergedItemCount}，×N 徽标 ${await badges().count()}`);
    check('关闭合并后 ×N 徽标消失', (await badges().count()) === 0);
    check('关闭合并后条目数变多', unmergedItemCount > mergedItemCount, `merged=${mergedItemCount} unmerged=${unmergedItemCount}`);
    check('关闭合并后总数仍为基线', (await totalNum()) === baseline);

    // 打开回来
    await mergeSwitch.click();
    await waitLoaded();
    check('重新开启合并后条目数复原', (await items().count()) === mergedItemCount);

    // ---------- [8] 单元格值可读化 ----------
    log('\n[8] detail 值格式化');
    await drawer().getByText('全部展开').first().click();
    await page.waitForTimeout(700);
    const drawerText = await drawer().innerText();
    check('不再出现原始 JSON 键名 valueDate/valueText/valueNumber', !/value(Date|Text|Number|Boolean|Select|User|Json)/.test(drawerText));
    check('不再出现 {"value 形式的裸 JSON', !drawerText.includes('{"value'));
    check('日期值被渲染成可读日期', /\d{4}-\d{2}-\d{2}/.test(drawerText), drawerText.slice(0, 200).replace(/\n/g, ' | '));
    await page.screenshot({ path: path.join(OUT, 'operation-history-expanded.png') });

    await drawer().getByText('全部收起').first().click();
    await page.waitForTimeout(600);
    await page.screenshot({ path: path.join(OUT, 'operation-history-collapsed.png') });

    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'operation-history-error.png') }).catch(() => {});
  } finally {
    // 清掉临时成员
    if (addedMember) {
      const r = await apiSend('DELETE', `/api/v1/bitable/bases/${BASE_ID}/members/${TMP_USER_ID}`);
      log(`\n[清理] 移除临时成员 userId=${TMP_USER_ID} →`, r.code === 200 ? 'ok' : JSON.stringify(r));
    }
    await browser.close();
  }

  log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
  process.exit(fail ? 1 : 0);
}

main();
