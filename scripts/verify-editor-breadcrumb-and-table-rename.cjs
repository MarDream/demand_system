// 验证多维表格编辑器的两处增强：
//   1) 顶部路径导航（面包屑）支持点击回退跳转
//   2) 左侧「数据表」树中支持 分组重命名 + 数据表重命名
// 用法：NODE_PATH=<demand_frontend/node_modules> node scripts/verify-editor-breadcrumb-and-table-rename.cjs
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

  /** 左侧数据表树快照 */
  const treeSnap = () =>
    page.locator('.tgt-node').evaluateAll((els) =>
      els.map((el) => ({
        key: el.dataset.key,
        kind: ([...el.classList].find((c) => c.startsWith('tgt-node--')) || '').replace('tgt-node--', ''),
        active: el.classList.contains('is-active'),
        name: el.querySelector('.tgt-node__name')?.textContent?.trim() ?? '',
      })),
    );
  const show = (list) => list.map((n) => `${n.kind}:${n.name}${n.active ? '*' : ''}`).join(' / ');

  /** 面包屑快照（tag 用于确认是否可点击） */
  const crumbSnap = () =>
    page.locator('.editor-header__breadcrumbs .editor-header__crumb').evaluateAll((els) =>
      els.map((el) => ({ tag: el.tagName.toLowerCase(), text: el.textContent.trim() })),
    );

  const activeTableName = async () => {
    const n = page.locator('.tgt-node--table.is-active .tgt-node__name').first();
    return (await n.count()) ? (await n.innerText()).trim() : '';
  };

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

  const nodeByKey = (key) => page.locator(`.tgt-node[data-key="${key}"]`);
  const menuItem = (text) =>
    page.locator('.el-dropdown-menu__item:visible').filter({ hasText: text }).first();

  /** 打开某个树节点的 ··· 菜单并点击指定项 */
  const openNodeMenu = async (key, itemText) => {
    const node = nodeByKey(key);
    await node.hover();
    await node.locator('.tgt-node__tool').last().click();
    await menuItem(itemText).click();
  };

  /** 填写 ElMessageBox.prompt 并确认 */
  const fillPrompt = async (value) => {
    const input = page.locator('.el-message-box__input input').first();
    await input.waitFor({ timeout: 8000 });
    await input.fill(value);
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
  };

  const GROUP_A = '__ED_RN_A';
  const GROUP_B = '__ED_RN_B';
  const TEMP_TABLE = '__ED_TMP_TBL';
  let baseId = null;
  let baseName = '';
  let editorUrl = '';

  try {
    // ---------- 登录 ----------
    await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' });
    await page.locator('input[placeholder="请输入用户名"]').fill('admin');
    await page.locator('input[placeholder="请输入密码"]').fill('admin123');
    await page.locator('.login-btn').first().click();
    await page.waitForURL((u) => !u.pathname.includes('/login'), { timeout: 30000 });
    log('[1] 登录成功');

    // ---------- 定位一个多维表格 ----------
    await page.goto(BASE + '/bitable', { waitUntil: 'domcontentloaded' });
    await page.locator('.bgtree-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(800);

    const baseNode = page.locator('.bgtree-node--base').first();
    check('列表页存在多维表格节点', (await baseNode.count()) > 0);
    const rawKey = await baseNode.getAttribute('data-key');
    baseId = Number(String(rawKey).replace(/^b:/, ''));
    baseName = (await baseNode.locator('.bgtree-node__name').innerText()).trim();
    check('解析出多维表格 ID', Number.isFinite(baseId) && baseId > 0, 'key=' + rawKey);
    log('    目标多维表格:', baseId, baseName);

    // ---------- [2] 进入编辑器，检查面包屑 ----------
    editorUrl = `${BASE}/bitable/${baseId}`;
    await page.goto(editorUrl, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1200);

    const crumbs = await crumbSnap();
    log('[2] 面包屑:', JSON.stringify(crumbs));
    check('面包屑至少两级', crumbs.length >= 2, JSON.stringify(crumbs));
    check('第一级为「多维表格」且是按钮（可点击）', crumbs[0]?.text === '多维表格' && crumbs[0]?.tag === 'button', JSON.stringify(crumbs[0]));
    check('第二级为多维表格名称且是按钮（可点击）', crumbs[1]?.text === baseName && crumbs[1]?.tag === 'button', JSON.stringify(crumbs[1]));

    const tree = await treeSnap();
    log('[3] 数据表树:', show(tree));
    const tableNodes = tree.filter((n) => n.kind === 'table');
    check('树中存在数据表节点', tableNodes.length > 0, 'tables=' + tableNodes.length);
    if (!tableNodes.length) throw new Error('树里没有数据表，无法继续');

    check('面包屑第三级为当前数据表名', crumbs[2]?.text === (await activeTableName()), JSON.stringify(crumbs[2]));

    // 视觉取证：悬停面包屑的可点击反馈
    await page.locator('.editor-header__breadcrumbs .editor-header__crumb').nth(1).hover();
    await page.waitForTimeout(300);
    await page
      .locator('.editor-header')
      .screenshot({ path: path.join(OUT, 'editor-breadcrumb-hover.png') })
      .catch(() => {});

    // ---------- [4] 切到第二张表，再用面包屑回退 ----------
    let tempTable = null;
    let secondTable = tableNodes.length >= 2 ? tableNodes[1] : null;

    if (!secondTable) {
      // 当前多维表格只有一张数据表，临时新建一张来覆盖「切表后回退」用例
      await page.locator('.tgt-header__btn[title="新建数据表"]').first().click();
      await fillPrompt(TEMP_TABLE);
      await waitNoMessage();
      await page.waitForTimeout(1500);
      const snapAfterCreate = await treeSnap();
      const created = snapAfterCreate.find((n) => n.kind === 'table' && n.name === TEMP_TABLE);
      check('临时数据表已创建（用于覆盖切表回退）', !!created, show(snapAfterCreate));
      if (created) {
        secondTable = created;
        tempTable = created;
      }
    }

    if (secondTable) {
      await nodeByKey(secondTable.key).locator('.tgt-node__name').click();
      await page.waitForFunction(
        (name) =>
          (document.querySelector('.tgt-node--table.is-active .tgt-node__name')?.textContent || '').trim() === name,
        secondTable.name,
        { timeout: 15000 },
      ).catch(() => {});
      check('可切换到第二张数据表', (await activeTableName()) === secondTable.name, 'now=' + (await activeTableName()));

      const before = await crumbSnap();
      check('面包屑第三级跟随切换', before[2]?.text === secondTable.name, JSON.stringify(before[2]));

      // 点击「{多维表格名称}」→ 回退到默认数据表
      await page.locator('.editor-header__breadcrumbs .editor-header__crumb').nth(1).click();
      await page.waitForFunction(
        (name) =>
          (document.querySelector('.tgt-node--table.is-active .tgt-node__name')?.textContent || '').trim() === name,
        tableNodes[0].name,
        { timeout: 15000 },
      ).catch(() => {});
      check('点击多维表格名称可回退到默认数据表', (await activeTableName()) === tableNodes[0].name, 'now=' + (await activeTableName()));
      check('回退后仍停留在编辑器内', new URL(page.url()).pathname === `/bitable/${baseId}`, page.url());
      await waitNoMessage();
    }

    // 清理临时数据表
    if (tempTable) {
      await openNodeMenu(tempTable.key, '删除数据表');
      await page.locator('.el-message-box__btns .el-button--primary').first().click();
      await waitNoMessage();
      await page.waitForTimeout(1200);
      const afterDel = await treeSnap();
      check('临时数据表已清理', !afterDel.some((n) => n.kind === 'table' && n.name === TEMP_TABLE), show(afterDel));
    }

    // ---------- [5] 点击「多维表格」→ 回退到列表页 ----------
    await page.locator('.editor-header__breadcrumbs .editor-header__crumb').first().click();
    await page.waitForURL((u) => u.pathname === '/bitable', { timeout: 15000 }).catch(() => {});
    check('点击「多维表格」可回退到列表页', new URL(page.url()).pathname === '/bitable', page.url());

    // ---------- [6] 数据表重命名 ----------
    await page.goto(editorUrl, { waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);

    const beforeRename = await treeSnap();
    const targetTable = beforeRename.find((n) => n.kind === 'table');
    const tableOrigName = targetTable.name;
    const tableNewName = tableOrigName + '_RN';
    log('[6] 目标数据表:', targetTable.key, tableOrigName);

    await waitNoMessage();
    const tableNode = nodeByKey(targetTable.key);
    await tableNode.hover();
    await tableNode.locator('.tgt-node__tool').last().click();
    await page.waitForTimeout(400);
    // 视觉取证：数据表节点的 ··· 菜单包含「重命名」
    await page.screenshot({ path: path.join(OUT, 'editor-table-menu.png') }).catch(() => {});
    await menuItem('重命名').click();
    const tableInput = page.locator('.el-message-box__input input').first();
    await tableInput.waitFor({ timeout: 8000 });
    check('数据表菜单可弹出重命名输入框', true);
    check(
      '数据表重命名输入框预填当前名称',
      (await tableInput.inputValue()) === tableOrigName,
      'got=' + (await tableInput.inputValue()),
    );
    await fillPrompt(tableNewName);

    const msgTable = await lastMessage();
    check('提示表名已保存', msgTable.includes('表名'), 'msg=' + msgTable);
    await waitNoMessage();
    await page.waitForTimeout(800);

    const afterRename = await treeSnap();
    log('[7] 数据表重命名后:', show(afterRename));
    check(
      '树中数据表名称已更新',
      afterRename.some((n) => n.key === targetTable.key && n.name === tableNewName),
      show(afterRename),
    );
    check('数据表数量未变', afterRename.filter((n) => n.kind === 'table').length === beforeRename.filter((n) => n.kind === 'table').length);

    // 刷新后仍为新名（确认落库）
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.locator('.tgt-node').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1200);
    const afterReload = await treeSnap();
    check(
      '刷新后名称仍为新名（已落库）',
      afterReload.some((n) => n.key === targetTable.key && n.name === tableNewName),
      show(afterReload),
    );

    // 改回原名，避免污染数据
    await waitNoMessage();
    await openNodeMenu(targetTable.key, '重命名');
    await fillPrompt(tableOrigName);
    await waitNoMessage();
    await page.waitForTimeout(800);
    const restored = await treeSnap();
    check('可改回原名称（幂等收尾）', restored.some((n) => n.key === targetTable.key && n.name === tableOrigName));

    // ---------- [8] 分组重命名（编辑器内） ----------
    await page.locator('.tgt-header__btn[title="新建分组"]').first().click();
    const groupInput = page.locator('.tgt-editor input').first();
    await groupInput.waitFor({ timeout: 8000 });
    await groupInput.fill(GROUP_A);
    await groupInput.press('Enter');
    await waitNoMessage();
    await page.waitForTimeout(1000);

    let withGroup = await treeSnap();
    const createdGroup = withGroup.find((n) => n.kind === 'group' && n.name === GROUP_A);
    check('分组已创建', !!createdGroup, show(withGroup));
    if (!createdGroup) throw new Error('分组创建失败，无法继续');

    await openNodeMenu(createdGroup.key, '重命名');
    const groupPrompt = page.locator('.el-message-box__input input').first();
    await groupPrompt.waitFor({ timeout: 8000 });
    check(
      '分组重命名输入框预填当前名称',
      (await groupPrompt.inputValue()) === GROUP_A,
      'got=' + (await groupPrompt.inputValue()),
    );
    await fillPrompt(GROUP_B);
    const msgGroup = await lastMessage();
    check('提示分组已重命名', msgGroup.includes('重命名'), 'msg=' + msgGroup);
    await waitNoMessage();
    await page.waitForTimeout(1000);

    const groupRenamed = await treeSnap();
    log('[8] 分组重命名后:', show(groupRenamed));
    check('分组名称已更新为新名', groupRenamed.some((n) => n.kind === 'group' && n.name === GROUP_B), show(groupRenamed));
    check('旧分组名已不存在', !groupRenamed.some((n) => n.kind === 'group' && n.name === GROUP_A));

    // ---------- [9] 删除测试分组收尾 ----------
    const delTarget = groupRenamed.find((n) => n.kind === 'group' && n.name === GROUP_B);
    await openNodeMenu(delTarget.key, '删除分组');
    await page.locator('.el-message-box__btns .el-button--primary').first().click();
    await waitNoMessage();
    await page.waitForTimeout(1000);

    const cleaned = await treeSnap();
    log('[9] 清理后:', show(cleaned));
    check('测试分组已删除', !cleaned.some((n) => n.kind === 'group' && n.name === GROUP_B));
    check('数据表数量未变', cleaned.filter((n) => n.kind === 'table').length === beforeRename.filter((n) => n.kind === 'table').length);

    await page.screenshot({ path: path.join(OUT, 'editor-breadcrumb-table-rename.png') });
    check('页面无 JS 报错', errs.length === 0, errs.join(' | '));
  } catch (e) {
    fail++;
    log('!! 执行异常: ' + e.message + '\n' + e.stack);
    await page.screenshot({ path: path.join(OUT, 'editor-breadcrumb-table-rename-error.png') }).catch(() => {});
  } finally {
    await browser.close();
  }

  log(`\n===== 结果：PASS ${pass} / FAIL ${fail} =====`);
  process.exit(fail ? 1 : 0);
}

main();
