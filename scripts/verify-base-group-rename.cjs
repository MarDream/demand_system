// 验证「多维表格」列表页左侧分组树的「重命名」能力
// 覆盖：多维表格（Base）重命名、分组重命名、分组新建/删除收尾
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-base-group-rename.cjs
const path = require('node:path');
const fs = require('node:fs');

const BASE = 'http://127.0.0.1:5170';
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

  // 树快照
  const snap = () =>
    page.locator('.bgtree-node').evaluateAll((els) =>
      els.map((el) => ({
        key: el.dataset.key,
        kind: ([...el.classList].find((c) => c.startsWith('bgtree-node--')) || '').replace('bgtree-node--', ''),
        indent: parseInt(el.style.paddingLeft) || 0,
        name: el.querySelector('.bgtree-node__name')?.textContent?.trim() ?? '',
        count: el.querySelector('.bgtree-node__count')?.textContent?.trim() || '',
      })),
    );
  const show = (list) => list.map((n) => `${n.kind}:${n.name}${n.count ? `(${n.count})` : ''}@${n.indent}`).join(' / ');

  // .el-message 会堆叠，断言前先等它消失，再取最后一条
  const waitNoMessage = async () => {
    await page
      .waitForFunction(() => !document.querySelector('.el-message'), { timeout: 8000 })
      .catch(() => {});
  };
  const lastMessage = async () => {
    await page.waitForSelector('.el-message', { timeout: 8000 }).catch(() => {});
    return page
      .locator('.el-message')
      .last()
      .innerText()
      .catch(() => '');
  };

  const nodeByKey = (key) => page.locator(`.bgtree-node[data-key="${key}"]`);
  const menuItem = (text) => page.locator('.el-dropdown-menu__item:visible').filter({ hasText: text }).first();

  /** 打开某个树节点的 ··· 菜单并点击指定项 */
  const openNodeMenu = async (key, itemText) => {
    const node = nodeByKey(key);
    await node.hover();
    await node.locator('.bgtree-node__tool').last().click();
    await menuItem(itemText).click();
  };

  /** 填写 ElMessageBox.prompt 并确认 */
  const fillPrompt = async (value) => {
    const input = page.locator('.el-message-box__input input').first();
    await input.waitFor({ timeout: 8000 });
    await input.fill(value);
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
  };

  const GROUP_ORIG = '__RENAME_ORIG';
  const GROUP_NEW = '__RENAME_NEW';
  let baseKey = null;
  let baseOrigName = null;

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- 打开多维表格列表页 ----------
    await page.goto(BASE + '/bitable', { waitUntil: 'domcontentloaded' });
    await page.locator('.bgtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(800);

    const init = await snap();
    log('[2] 初始树:', show(init));
    check('左侧分组树已渲染', init.length > 0);
    check('存在「全部」虚拟节点', init.some((n) => n.name === '全部'));

    const baseNodes = init.filter((n) => n.kind === 'base');
    check('树中存在多维表格节点', baseNodes.length > 0, 'bases=' + baseNodes.length);
    if (!baseNodes.length) throw new Error('树里没有多维表格节点，无法继续');

    baseKey = baseNodes[0].key;
    baseOrigName = baseNodes[0].name;
    log('    目标多维表格:', baseKey, baseOrigName);

    // ---------- [3] 多维表格重命名 ----------
    await waitNoMessage();
    await openNodeMenu(baseKey, '重命名');

    const promptInput = page.locator('.el-message-box__input input').first();
    await promptInput.waitFor({ timeout: 8000 });
    check('弹出重命名输入框', true);
    check(
      '输入框预填当前名称',
      (await promptInput.inputValue()) === baseOrigName,
      'got=' + (await promptInput.inputValue()),
    );

    const baseNewName = baseOrigName + '_RN';
    await fillPrompt(baseNewName);

    const msg1 = await lastMessage();
    check('提示重命名成功', msg1.includes('重命名'), 'msg=' + msg1);
    await waitNoMessage();
    await page.waitForTimeout(600);

    let after = await snap();
    log('[3] Base 重命名后:', show(after));
    check(
      '树中该多维表格名称已更新',
      after.some((n) => n.key === baseKey && n.name === baseNewName),
      show(after.filter((n) => n.key === baseKey)),
    );
    check(
      '未误改其它节点',
      after.filter((n) => n.kind === 'base').length === baseNodes.length,
    );

    // 改回原名，避免污染数据
    await openNodeMenu(baseKey, '重命名');
    await fillPrompt(baseOrigName);
    await waitNoMessage();
    await page.waitForTimeout(600);
    const restored = await snap();
    check(
      '可改回原名称（幂等收尾）',
      restored.some((n) => n.key === baseKey && n.name === baseOrigName),
    );

    // ---------- [4] 新建分组 ----------
    await page.locator('.bgtree-header__btn[title="新建分组"]').first().click();
    const editorInput = page.locator('.bgtree-editor input').first();
    await editorInput.waitFor({ timeout: 8000 });
    await editorInput.fill(GROUP_ORIG);
    await editorInput.press('Enter');
    await waitNoMessage();
    await page.waitForTimeout(800);

    let withGroup = await snap();
    log('[4] 新建分组后:', show(withGroup));
    const created = withGroup.find((n) => n.kind === 'group' && n.name === GROUP_ORIG);
    check('分组已创建', !!created, show(withGroup));

    // ---------- [5] 分组重命名 ----------
    await openNodeMenu(created.key, '重命名');
    const groupPrompt = page.locator('.el-message-box__input input').first();
    await groupPrompt.waitFor({ timeout: 8000 });
    check(
      '分组重命名输入框预填当前名称',
      (await groupPrompt.inputValue()) === GROUP_ORIG,
      'got=' + (await groupPrompt.inputValue()),
    );
    await fillPrompt(GROUP_NEW);

    const msg2 = await lastMessage();
    check('提示分组已重命名', msg2.includes('重命名'), 'msg=' + msg2);
    await waitNoMessage();
    await page.waitForTimeout(800);

    const renamed = await snap();
    log('[5] 分组重命名后:', show(renamed));
    check(
      '分组名称已更新为新名',
      renamed.some((n) => n.kind === 'group' && n.name === GROUP_NEW),
      show(renamed),
    );
    check(
      '旧分组名已不存在',
      !renamed.some((n) => n.kind === 'group' && n.name === GROUP_ORIG),
    );

    // ---------- [6] 删除测试分组收尾 ----------
    const target = renamed.find((n) => n.kind === 'group' && n.name === GROUP_NEW);
    await openNodeMenu(target.key, '删除分组');
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await waitNoMessage();
    await page.waitForTimeout(800);

    const cleaned = await snap();
    log('[6] 清理后:', show(cleaned));
    check('测试分组已删除', !cleaned.some((n) => n.kind === 'group' && n.name === GROUP_NEW));
    check('多维表格数量未变', cleaned.filter((n) => n.kind === 'base').length === baseNodes.length);

    await page.screenshot({ path: path.join(OUT, 'bgtree-rename.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'bgtree-rename-error.png') }).catch(() => {});
  } finally {
    await browser.close();
  }

  log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
  process.exit(fail ? 1 : 0);
}

main();
